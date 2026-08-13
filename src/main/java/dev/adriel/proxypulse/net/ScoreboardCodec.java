package dev.adriel.proxypulse.net;

import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.proxy.protocol.ProtocolUtils;
import com.velocitypowered.proxy.protocol.packet.chat.ComponentHolder;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;

/**
 * Reads and rebuilds the two scoreboard packets, which the proxy itself never decodes.
 *
 * <p>The packets are not fully understood here, and deliberately so. Only the fields in front of
 * each piece of text are parsed; everything after the last one is copied through byte for byte.
 * That keeps the codec indifferent to fields that come and go between Minecraft versions, and it
 * means a field this code has never heard of cannot corrupt anything.
 *
 * <p>The team packet is the exception, because two of its fields changed shape between versions and
 * they sit <em>between</em> the pieces of text. Rather than deciding from the protocol number --
 * which would silently produce garbage the day it is wrong -- both layouts are attempted and the
 * one whose remaining bytes parse as a well formed entity list, ending exactly at the end of the
 * packet, is the one used. If neither does, the packet is left alone.
 */
public final class ScoreboardCodec {

  private ScoreboardCodec() {
  }

  /**
   * A scoreboard packet broken into the text it carries and the bytes around that text.
   *
   * @param key identifies the element across packets, so its animation can be refreshed
   * @param components the text found, in the order it appears
   * @param segments the untouched bytes; there is always one more segment than component, and they
   *     interleave: segment, component, segment, component, ... segment
   */
  public record Parsed(String key, List<Component> components, List<byte[]> segments) {

    /** Rebuilds the packet with new text in place of the old. */
    public ByteBuf rebuild(final List<Component> rendered, final ProtocolVersion version) {
      final ByteBuf out = Unpooled.buffer();
      for (int index = 0; index < segments.size(); index++) {
        out.writeBytes(segments.get(index));
        if (index < rendered.size()) {
          new ComponentHolder(version, rendered.get(index)).write(out);
        }
      }
      return out;
    }
  }

  /** Parses an objective packet, or returns null when it carries no text to rewrite. */
  public static Parsed parseObjective(final ByteBuf buffer, final ProtocolVersion version) {
    final int start = buffer.readerIndex();
    try {
      ProtocolUtils.readVarInt(buffer);
      final String objectiveName = ProtocolUtils.readString(buffer);
      final byte method = buffer.readByte();
      if (method == 1) {
        // Removing an objective carries nothing but its name.
        return null;
      }

      final int textStart = buffer.readerIndex();
      final Component title = ComponentHolder.read(buffer, version).getComponent();
      final int textEnd = buffer.readerIndex();

      return new Parsed("objective:" + objectiveName, List.of(title),
          List.of(copy(buffer, start, textStart - start),
              copy(buffer, textEnd, buffer.writerIndex() - textEnd)));
    } catch (final RuntimeException exception) {
      return null;
    } finally {
      buffer.readerIndex(start);
    }
  }

  /**
   * Parses a team packet, whose display name, prefix and suffix are where sidebar lines live.
   *
   * @return the parsed packet, or null when there is nothing to rewrite or the layout was not
   *     recognised
   */
  public static Parsed parseTeam(final ByteBuf buffer, final ProtocolVersion version) {
    for (final boolean modernLayout : new boolean[] {true, false}) {
      final Parsed parsed = tryParseTeam(buffer, version, modernLayout);
      if (parsed != null) {
        return parsed;
      }
    }
    return null;
  }

  private static Parsed tryParseTeam(final ByteBuf buffer, final ProtocolVersion version,
      final boolean visibilityAsEnum) {
    final int start = buffer.readerIndex();
    try {
      ProtocolUtils.readVarInt(buffer);
      final String teamName = ProtocolUtils.readString(buffer);
      final byte method = buffer.readByte();
      if (method != 0 && method != 2) {
        // Only creating and updating a team carry text; the rest just move members around.
        return null;
      }

      final List<byte[]> segments = new ArrayList<>();
      final List<Component> components = new ArrayList<>();

      int segmentStart = start;
      segmentStart = readComponent(buffer, version, segments, components, segmentStart);

      buffer.readByte(); // friendly flags
      if (visibilityAsEnum) {
        ProtocolUtils.readVarInt(buffer); // name tag visibility
        ProtocolUtils.readVarInt(buffer); // collision rule
      } else {
        ProtocolUtils.readString(buffer);
        ProtocolUtils.readString(buffer);
      }
      ProtocolUtils.readVarInt(buffer); // colour

      segmentStart = readComponent(buffer, version, segments, components, segmentStart);
      segmentStart = readComponent(buffer, version, segments, components, segmentStart);

      // Whatever is left has to be the member list, exactly filling the packet. If it does not,
      // this layout guess was wrong and the numbers read so far were meaningless.
      final int membersStart = buffer.readerIndex();
      final int members = ProtocolUtils.readVarInt(buffer);
      if (members < 0 || members > 4096) {
        return null;
      }
      for (int index = 0; index < members; index++) {
        ProtocolUtils.readString(buffer);
      }
      if (buffer.isReadable()) {
        return null;
      }

      segments.add(copy(buffer, membersStart, buffer.writerIndex() - membersStart));
      return new Parsed("team:" + teamName, List.copyOf(components), List.copyOf(segments));
    } catch (final RuntimeException exception) {
      return null;
    } finally {
      buffer.readerIndex(start);
    }
  }

  /** Reads one component, remembering the untouched bytes that came before it. */
  private static int readComponent(final ByteBuf buffer, final ProtocolVersion version,
      final List<byte[]> segments, final List<Component> components, final int segmentStart) {
    final int textStart = buffer.readerIndex();
    segments.add(copy(buffer, segmentStart, textStart - segmentStart));
    components.add(ComponentHolder.read(buffer, version).getComponent());
    return buffer.readerIndex();
  }

  private static byte[] copy(final ByteBuf buffer, final int index, final int length) {
    return ByteBufUtil.getBytes(buffer, index, length);
  }
}
