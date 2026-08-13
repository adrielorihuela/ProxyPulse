package dev.adriel.proxypulse.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.proxy.protocol.ProtocolUtils;
import com.velocitypowered.proxy.protocol.StateRegistry;
import com.velocitypowered.proxy.protocol.packet.BossBarPacket;
import com.velocitypowered.proxy.protocol.packet.HeaderAndFooterPacket;
import com.velocitypowered.proxy.protocol.packet.chat.ComponentHolder;
import com.velocitypowered.proxy.protocol.packet.chat.SystemChatPacket;
import dev.adriel.proxypulse.ProxyPulse;
import dev.adriel.proxypulse.animation.AnimationRegistry;
import dev.adriel.proxypulse.config.PulseConfig;
import dev.adriel.proxypulse.placeholder.PlaceholderEngine;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

/**
 * Drives the rewriting path with the packets a backend would really send.
 *
 * <p>This is as close to the wire as the plugin can be tested without a Minecraft client: the
 * buffers are built exactly as the proxy would encode them, and the results are read back through
 * the proxy's own packet classes.
 */
class PlayerSessionTest {

  private static final ProtocolVersion VERSION = ProtocolVersion.MINECRAFT_1_21_5;

  private PlayerSession session;

  @BeforeEach
  void setUp() throws Exception {
    // A single frame, so that what the rewriting produces does not depend on when the test runs.
    // Frames advancing with the clock is AnimationTest's job to check.
    final AnimationRegistry animations = new AnimationRegistry();
    animations.load(YamlConfigurationLoader.builder().buildAndLoadString("""
        logo:
          change-interval: 100
          texts:
          - "FRAME-ONE"
        """));

    final PlaceholderEngine engine = new PlaceholderEngine(animations);
    engine.register("player", (argument, context) -> "Steve");

    final PulseConfig config = new PulseConfig(500L, Map.of(),
        new PulseConfig.GlobalTablist(false, Set.of(), true, "%player%", List.of(), List.of(),
            List.of()),
        false);

    session = new PlayerSession(new TestPlugin(engine, animations, config), stubPlayer(), null);
  }

  private static int clientboundId(final Object packet) {
    return StateRegistry.PLAY
        .getProtocolRegistry(ProtocolUtils.Direction.CLIENTBOUND, VERSION)
        .getPacketId((com.velocitypowered.proxy.protocol.MinecraftPacket) packet);
  }

  private static ByteBuf headerFooterBuffer(final String header, final String footer) {
    final ByteBuf buffer = Unpooled.buffer();
    ProtocolUtils.writeVarInt(buffer, clientboundId(HeaderAndFooterPacket.reset(VERSION)));
    new ComponentHolder(VERSION, Component.text(header)).write(buffer);
    new ComponentHolder(VERSION, Component.text(footer)).write(buffer);
    return buffer;
  }

  private static String plain(final ComponentHolder holder) {
    return PlainTextComponentSerializer.plainText().serialize(holder.getComponent());
  }

  /**
   * A player that answers only what the rewriting path asks of it.
   *
   * <p>A dynamic proxy is used rather than a mocking library because the Player interface drags in
   * most of Adventure behind it, and stubbing three methods by hand is clearer than fighting a
   * framework over the other eighty.
   */
  private static Player stubPlayer() {
    final UUID uuid = UUID.randomUUID();
    return (Player) java.lang.reflect.Proxy.newProxyInstance(
        PlayerSessionTest.class.getClassLoader(),
        new Class<?>[] {Player.class},
        (proxy, method, arguments) -> switch (method.getName()) {
          case "getProtocolVersion" -> VERSION;
          case "getUsername" -> "Steve";
          case "getUniqueId" -> uuid;
          case "getCurrentServer" -> java.util.Optional.empty();
          case "getPing" -> 0L;
          case "toString" -> "Steve";
          case "hashCode" -> uuid.hashCode();
          case "equals" -> proxy == arguments[0];
          default -> null;
        });
  }

  /** The plugin, reduced to the three things a session actually reads from it. */
  private static final class TestPlugin extends ProxyPulse {

    private final PlaceholderEngine engine;
    private final AnimationRegistry animations;
    private final PulseConfig config;

    private TestPlugin(final PlaceholderEngine engine, final AnimationRegistry animations,
        final PulseConfig config) {
      super(null, null, java.nio.file.Path.of("."));
      this.engine = engine;
      this.animations = animations;
      this.config = config;
    }

    @Override
    public PlaceholderEngine placeholders() {
      return engine;
    }

    @Override
    public AnimationRegistry animations() {
      return animations;
    }

    @Override
    public PulseConfig config() {
      return config;
    }
  }

  @Test
  @DisplayName("a header carrying an animation comes out as the current frame")
  void headerIsRewritten() {
    final Object result = session.rewrite(headerFooterBuffer("%animation:logo%", "static"));

    final HeaderAndFooterPacket packet = assertInstanceOf(HeaderAndFooterPacket.class, result);
    assertEquals("FRAME-ONE", plain(packet.getHeader()));
    assertEquals("static", plain(packet.getFooter()));
    assertEquals(1, session.trackedElements());
  }

  @Test
  @DisplayName("a header with no placeholder is passed through as the very same buffer")
  void plainHeaderIsUntouched() {
    final ByteBuf buffer = headerFooterBuffer("Welcome", "Bye");
    final Object result = session.rewrite(buffer);

    assertSame(buffer, result);
    assertEquals(0, session.trackedElements(), "plain text must never be tracked");
  }

  @Test
  @DisplayName("a static header replacing an animated one stops the animation")
  void staticHeaderStopsTracking() {
    session.rewrite(headerFooterBuffer("%animation:logo%", ""));
    assertEquals(1, session.trackedElements());

    session.rewrite(headerFooterBuffer("Welcome", ""));
    assertEquals(0, session.trackedElements());
  }

  @Test
  @DisplayName("a boss bar name is rewritten in place and starts being tracked")
  void bossBarIsRewritten() {
    final BossBarPacket packet = new BossBarPacket();
    packet.setUuid(UUID.randomUUID());
    packet.setAction(BossBarPacket.ADD);
    packet.setName(new ComponentHolder(VERSION, Component.text("Hi %player% %animation:logo%")));

    final Object result = session.rewrite(packet);

    assertSame(packet, result);
    assertEquals("Hi Steve FRAME-ONE", plain(packet.getName()));
    assertEquals(1, session.trackedElements());
  }

  @Test
  @DisplayName("removing a boss bar stops tracking it")
  void bossBarRemovalStopsTracking() {
    final UUID uuid = UUID.randomUUID();
    final BossBarPacket add = new BossBarPacket();
    add.setUuid(uuid);
    add.setAction(BossBarPacket.ADD);
    add.setName(new ComponentHolder(VERSION, Component.text("%animation:logo%")));
    session.rewrite(add);
    assertEquals(1, session.trackedElements());

    final BossBarPacket remove = new BossBarPacket();
    remove.setUuid(uuid);
    remove.setAction(BossBarPacket.REMOVE);
    session.rewrite(remove);

    assertEquals(0, session.trackedElements());
  }

  @Test
  @DisplayName("a chat packet holding an animation is left exactly as it arrived")
  void chatIsNeverRewritten() {
    final ByteBuf buffer = Unpooled.buffer();
    ProtocolUtils.writeVarInt(buffer, clientboundId(new SystemChatPacket()));
    new ComponentHolder(VERSION, Component.text("<Steve> %animation:logo%")).write(buffer);
    buffer.writeBoolean(false);
    final byte[] before = io.netty.buffer.ByteBufUtil.getBytes(buffer);

    final Object result = session.rewrite(buffer);

    assertSame(buffer, result);
    assertTrue(java.util.Arrays.equals(before, io.netty.buffer.ByteBufUtil.getBytes(buffer)));
    assertEquals(0, session.trackedElements());
  }

  @Test
  @DisplayName("changing server drops everything that belonged to the old one")
  void switchingServerClearsTracking() {
    session.rewrite(headerFooterBuffer("%animation:logo%", ""));
    assertEquals(1, session.trackedElements());

    session.clear();

    assertEquals(0, session.trackedElements());
  }
}
