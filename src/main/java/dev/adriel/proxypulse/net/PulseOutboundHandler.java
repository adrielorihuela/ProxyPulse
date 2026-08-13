package dev.adriel.proxypulse.net;

import dev.adriel.proxypulse.session.PlayerSession;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;

/**
 * Sits on a player's connection, just upstream of the proxy's packet encoder, and gives every
 * outgoing message a chance to be rewritten before it is turned into bytes.
 *
 * <p>Being placed before the encoder is what makes the rest of the plugin simple: packets the proxy
 * decoded are still objects here, packets it merely forwards are still buffers, and a rewritten
 * packet can be handed back as either -- the encoder downstream takes care of the wire format for
 * whichever client version is on the other end.
 */
public final class PulseOutboundHandler extends ChannelOutboundHandlerAdapter {

  public static final String NAME = "proxypulse-rewriter";

  private final PlayerSession session;

  public PulseOutboundHandler(final PlayerSession session) {
    this.session = session;
  }

  @Override
  public void write(final ChannelHandlerContext ctx, final Object message,
      final ChannelPromise promise) throws Exception {
    Object outgoing = message;
    try {
      outgoing = session.rewrite(message);
    } catch (final Throwable throwable) {
      // A packet the plugin cannot make sense of must still reach the player untouched: a broken
      // rewrite should cost an animation, never the connection.
      session.reportFailure(throwable);
      outgoing = message;
    }
    super.write(ctx, outgoing, promise);
  }
}
