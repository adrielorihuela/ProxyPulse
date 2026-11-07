# ProxyPulse architecture

> Known bugs in this design are tracked in [issues](https://github.com/adrielorihuela/ProxyPulse/issues). The most serious: refreshes resend the
> original scoreboard create packet ([#1](https://github.com/adrielorihuela/ProxyPulse/issues/1)), team updates are not parsed ([#2](https://github.com/adrielorihuela/ProxyPulse/issues/2)),
> removals are not untracked ([#3](https://github.com/adrielorihuela/ProxyPulse/issues/3)), and a failed `rewriteRaw` leaves the reader index moved
> ([#5](https://github.com/adrielorihuela/ProxyPulse/issues/5)).

Moved from CLAUDE.md § Architecture on 2026-11-07.

## Architecture

### The dependency that shapes everything

Velocity exposes no packet API, so the plugin compiles against the **proxy's own internal classes**
(`com.velocitypowered.proxy.*`). `build.gradle.kts` downloads the proxy jar (pinned by SHA-256 in
`gradle.properties`) and puts it on the compile classpath only — it is never bundled. Bumping the
Velocity version means changing both properties together.

PacketEvents was tried first and abandoned: it ships an Adventure 4 serializer that cannot load
against the Adventure 5 Velocity 4 ships (upstream issue retrooper/packetevents#1500). Do not
reintroduce it. Commits `028bf1c` and `76de213` record the reasoning.

### The packet path

`SessionManager` (a Velocity event listener) inserts `PulseOutboundHandler` into each player's Netty
pipeline with `addAfter(MINECRAFT_ENCODER, ...)`. In pipeline order that is *after* the encoder,
which for an **outgoing** packet means *before* it — so `PlayerSession.rewrite(Object)` sees either:

- a **`MinecraftPacket` object** for packets the proxy decodes (`BossBarPacket`,
  `UpsertPlayerInfoPacket`, `RemovePlayerInfoPacket`) — mutate or replace it;
- a **raw `ByteBuf`** for packets it only forwards (tab list header/footer, titles, action bar, all
  scoreboard packets) — the packet id VarInt is still at the head.

`rewrite` returning `null` drops the packet (used by the global tab list to suppress the backend's
own list).

### Three invariants that are easy to break

**Refreshed packets must be sent via `PlayerSession.send`**, which starts the write at the handler's
own `ChannelHandlerContext` so it skips this session's handler. An ordinary `connection.write` sends
the packet back through the handler that produced it, where the already-rendered text contains no
placeholders, the element stops being tracked, and every animation freezes after one frame. See
commit `7dfe5dd`.

**Only the original text is stored.** `TrackedElement.originals` holds the text as the backend wrote
it, placeholders intact; rendering happens fresh each tick. Never store a rendered copy as the
source of truth.

**Chat is never rewritten.** `PacketIds.isChat` is checked first in `rewriteRaw`, and
`ChatIsNeverRewrittenTest` asserts across every supported protocol version that no chat id overlaps
one the plugin acts on. Any new packet type must keep that true.

### Packet ids

`PacketIds` reads ids out of Velocity's own `StateRegistry` per protocol version, so version bumps
need no maintenance. `ScoreboardIds` is the **one hand-maintained table** in the project — the proxy
never registers scoreboard packets — and it is meant to return `UNSUPPORTED` for protocol versions newer than
its last entry rather than guess, because a wrong id corrupts the connection rather than just the
text. Known bug: the check is `protocol > newest + 1`, so protocol 776 (26.2) currently gets the
unverified 26.1 ids. New Minecraft versions need a new row, derived from a published protocol listing
(ViaVersion's `ClientboundPackets*` enums are ordered by id).

`ScoreboardCodec` parses only the fields in front of each component and copies the rest through byte
for byte. For the team packet, whose middle fields changed shape between versions, it tries both
layouts and accepts the one whose remaining bytes parse as a member list ending exactly at the end
of the packet. Keep that shape: never decide a layout from the protocol number alone.

### Rendering

`PlaceholderEngine` uses Adventure's `replaceText` on the component tree, never a flattened string,
so the backend's colours, hover and click events survive. `TextParser` translates legacy `&` codes
into MiniMessage tags and then parses once, and caches parsed text (cleared on reload).

`AnimationTicker` is a single 50 ms thread; every `TrackedElement` computes a `signature` from the
frame indices of the animations it uses plus the current refresh bucket, and a tick that produces
the same signature does no rendering at all. Frame choice is a pure function of the wall clock
(`Animation.frameAt`), which is why every player on the network sees the same frame.
