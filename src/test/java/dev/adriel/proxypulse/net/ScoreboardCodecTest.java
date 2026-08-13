package dev.adriel.proxypulse.net;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.proxy.protocol.ProtocolUtils;
import com.velocitypowered.proxy.protocol.packet.chat.ComponentHolder;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ScoreboardCodecTest {

  private static final ProtocolVersion VERSION = ProtocolVersion.MINECRAFT_1_21_5;

  private static void writeComponent(final ByteBuf buffer, final String text) {
    new ComponentHolder(VERSION, Component.text(text)).write(buffer);
  }

  private static String plain(final Component component) {
    return PlainTextComponentSerializer.plainText().serialize(component);
  }

  private static ByteBuf objectivePacket(final String name, final String title) {
    final ByteBuf buffer = Unpooled.buffer();
    ProtocolUtils.writeVarInt(buffer, 0x63);
    ProtocolUtils.writeString(buffer, name);
    buffer.writeByte(0); // create
    writeComponent(buffer, title);
    ProtocolUtils.writeVarInt(buffer, 0); // render type
    buffer.writeBoolean(false); // no number format
    return buffer;
  }

  private static ByteBuf teamPacket(final String name, final boolean visibilityAsEnum) {
    final ByteBuf buffer = Unpooled.buffer();
    ProtocolUtils.writeVarInt(buffer, 0x66);
    ProtocolUtils.writeString(buffer, name);
    buffer.writeByte(0); // create
    writeComponent(buffer, "display");
    buffer.writeByte(0x03); // friendly flags
    if (visibilityAsEnum) {
      ProtocolUtils.writeVarInt(buffer, 0);
      ProtocolUtils.writeVarInt(buffer, 0);
    } else {
      ProtocolUtils.writeString(buffer, "always");
      ProtocolUtils.writeString(buffer, "always");
    }
    ProtocolUtils.writeVarInt(buffer, 15); // colour
    writeComponent(buffer, "prefix %animation:logo%");
    writeComponent(buffer, "suffix");
    ProtocolUtils.writeVarInt(buffer, 2); // members
    ProtocolUtils.writeString(buffer, "Alice");
    ProtocolUtils.writeString(buffer, "Bob");
    return buffer;
  }

  @Test
  @DisplayName("an objective's title is found, and the fields after it are copied through")
  void objectiveTitleIsParsed() {
    final ByteBuf packet = objectivePacket("sidebar", "Server %animation:logo%");
    final ScoreboardCodec.Parsed parsed = ScoreboardCodec.parseObjective(packet, VERSION);

    assertEquals("objective:sidebar", parsed.key());
    assertEquals(1, parsed.components().size());
    assertEquals("Server %animation:logo%", plain(parsed.components().get(0)));

    final ByteBuf rebuilt = parsed.rebuild(List.of(Component.text("Server X")), VERSION);
    final ScoreboardCodec.Parsed reparsed = ScoreboardCodec.parseObjective(rebuilt, VERSION);
    assertEquals("Server X", plain(reparsed.components().get(0)));
    // The trailing render type and number format survived the round trip untouched.
    assertEquals(packet.readableBytes() - lengthOf("Server %animation:logo%"),
        rebuilt.readableBytes() - lengthOf("Server X"));
  }

  @Test
  @DisplayName("removing an objective carries no text and is left alone")
  void objectiveRemovalIsIgnored() {
    final ByteBuf buffer = Unpooled.buffer();
    ProtocolUtils.writeVarInt(buffer, 0x63);
    ProtocolUtils.writeString(buffer, "sidebar");
    buffer.writeByte(1); // remove
    assertNull(ScoreboardCodec.parseObjective(buffer, VERSION));
  }

  @Test
  @DisplayName("a team's display name, prefix and suffix are all found")
  void teamTextIsParsed() {
    final ScoreboardCodec.Parsed parsed =
        ScoreboardCodec.parseTeam(teamPacket("line1", true), VERSION);

    assertEquals("team:line1", parsed.key());
    assertEquals(List.of("display", "prefix %animation:logo%", "suffix"),
        parsed.components().stream().map(ScoreboardCodecTest::plain).toList());
  }

  @Test
  @DisplayName("the older team layout is recognised without being told which one to expect")
  void olderTeamLayoutIsRecognised() {
    final ScoreboardCodec.Parsed parsed =
        ScoreboardCodec.parseTeam(teamPacket("line1", false), VERSION);

    assertEquals(List.of("display", "prefix %animation:logo%", "suffix"),
        parsed.components().stream().map(ScoreboardCodecTest::plain).toList());
  }

  @Test
  @DisplayName("a rebuilt team packet still parses, with the members intact")
  void teamRoundTripsThroughARebuild() {
    final ByteBuf original = teamPacket("line1", true);
    final ScoreboardCodec.Parsed parsed = ScoreboardCodec.parseTeam(original, VERSION);

    final ByteBuf rebuilt = parsed.rebuild(
        List.of(Component.text("display"), Component.text("prefix one"), Component.text("suffix")),
        VERSION);
    final ScoreboardCodec.Parsed reparsed = ScoreboardCodec.parseTeam(rebuilt, VERSION);

    assertEquals("team:line1", reparsed.key());
    assertEquals("prefix one", plain(reparsed.components().get(1)));
    // The member list is the tail segment, so it must have come through byte for byte.
    final String tail = new String(ByteBufUtil.getBytes(rebuilt), java.nio.charset.StandardCharsets.UTF_8);
    assertTrue(tail.contains("Alice") && tail.contains("Bob"));
  }

  @Test
  @DisplayName("moving members around carries no text and is left alone")
  void teamMemberUpdateIsIgnored() {
    final ByteBuf buffer = Unpooled.buffer();
    ProtocolUtils.writeVarInt(buffer, 0x66);
    ProtocolUtils.writeString(buffer, "line1");
    buffer.writeByte(3); // add members
    ProtocolUtils.writeVarInt(buffer, 1);
    ProtocolUtils.writeString(buffer, "Alice");

    assertNull(ScoreboardCodec.parseTeam(buffer, VERSION));
  }

  @Test
  @DisplayName("nonsense is refused rather than half parsed")
  void garbageIsRefused() {
    final ByteBuf buffer = Unpooled.buffer();
    ProtocolUtils.writeVarInt(buffer, 0x66);
    buffer.writeBytes(new byte[] {1, 2, 3, 4, 5, 6, 7, 8});
    assertNull(ScoreboardCodec.parseTeam(buffer, VERSION));
  }

  @Test
  @DisplayName("the reader index is left where it was found, so a refused packet still sends")
  void parsingDoesNotConsumeTheBuffer() {
    final ByteBuf packet = teamPacket("line1", true);
    final int before = packet.readerIndex();
    final int readable = packet.readableBytes();

    ScoreboardCodec.parseTeam(packet, VERSION);

    assertEquals(before, packet.readerIndex());
    assertEquals(readable, packet.readableBytes());
  }

  private static int lengthOf(final String text) {
    final ByteBuf buffer = Unpooled.buffer();
    writeComponent(buffer, text);
    return buffer.readableBytes();
  }
}
