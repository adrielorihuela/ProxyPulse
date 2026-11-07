# ProxyPulse

**Define an animation once on the proxy. Use it from every server on the network.**

> [!WARNING]
> Version 1.0.0 is not ready for production. It has never been tested with a player connected, and
> scoreboard rewriting has [known bugs](https://github.com/adrielorihuela/ProxyPulse/issues?q=is%3Aissue+is%3Aopen+label%3AP0). Do not run it on a
> live network until v1.0.1. See [docs/HANDOFF.md](docs/HANDOFF.md) for the current state.

ProxyPulse is a [Velocity](https://papermc.io/software/velocity) plugin that intercepts the text
your backend servers send to players and fills in placeholders before it reaches them. A backend
writes `%animation:logo%` in its tab list, scoreboard, boss bar or title, and the proxy replaces it
with the current frame of an animation defined in a single file — then keeps re-sending it as the
animation moves.

The point is that the animation lives in **one place**. Ten servers can show the same animated logo
without ten copies of it, and changing it means editing one file on the proxy instead of ten
backends.

```
    backend server                  proxy                        player
   ┌───────────────┐        ┌──────────────────────┐        ┌─────────────┐
   │ header:       │        │  %animation:logo%    │        │             │
   │ %animation:   │ ─────► │      resolved to     │ ─────► │  Birdflop   │  ← gradient moves
   │      logo%    │        │   frame 7 of 14      │        │             │     20×/second
   └───────────────┘        └──────────┬───────────┘        └─────────────┘
                                       │ every 50 ms, re-sent only if it changed
                                       └───────────────────────────────────►
```

---

## What it does

- **Animations from one file.** The same `animations.yml` format the TAB plugin uses, read by the
  proxy and available to every backend through `%animation:name%`.
- **Where text appears.** Tab list header, footer and player names, scoreboard titles, team
  prefixes and suffixes, boss bars, titles, subtitles and the action bar. Other places, such as
  entity names or inventory titles, are not covered.
- **Never in chat.** Chat packets are explicitly refused, so a player cannot type an animation into
  chat and have the proxy render it for everyone. There are tests that fail if that ever changes.
- **Network placeholders.** Player counts across the network or on a named server, the player's real
  ping, their client version and brand — things no single backend can answer.
- **An optional global tab list.** One list showing everyone on the network and which server they
  are on, with per-server exceptions. Off by default.

## Requirements

| | |
|---|---|
| Proxy | Velocity **4.x**. Only loading and console commands were checked, on 4.0.0; forks are untested |
| Java | **25+** |
| Backends | Any server software. ProxyPulse needs nothing installed on them. |
| Clients | Not tested with any client yet; [support is being defined](https://github.com/adrielorihuela/ProxyPulse/issues/18). By design, tab names and the global tab list need 1.19.3+, the global tab list sorts rows on 1.21.2+, and scoreboard rewriting needs 1.20.2+. Titles before 1.17 are [likely broken](https://github.com/adrielorihuela/ProxyPulse/issues/5). |

## Install

1. Download `ProxyPulse-1.0.0.jar` from the
   [releases page](https://github.com/adrielorihuela/ProxyPulse/releases).
2. Drop it in your proxy's `plugins/` folder. There is nothing else to install — every dependency is
   inside the jar.
3. Start the proxy. `plugins/proxypulse/config.yml` and `animations.yml` are written out on first
   start.

## Using an animation

Define it once on the proxy, in `plugins/proxypulse/animations.yml`:

```yaml
logo:
  change-interval: 50        # milliseconds between frames
  texts:
  - "<gradient:#5481C8:#54C8EB>Birdflop</gradient>"
  - "<gradient:#5493D1:#54DAF4>Birdflop</gradient>"
  - "<gradient:#54A5D9:#545EB6>Birdflop</gradient>"
```

Then use it from any backend, wherever that backend puts text on screen:

```yaml
# an example backend's own config -- any plugin that sets a tab header will do
header: "%animation:logo%"
```

Frames accept MiniMessage (`<gradient>`, `<rainbow>`, `<#hex>`, `<bold>`) and legacy `&` codes, and
the two can be mixed in one line. Placeholders work inside frames too.

Every player on the network sees the same frame at the same moment, because the frame is derived
from the clock rather than from a counter — so a player who joins mid-cycle picks the animation up
where everyone else already is, and two servers showing the same logo stay in step.

## Placeholders

Anything PlaceholderAPI already exposes on a backend (TPS, world, coordinates) is deliberately
absent: the server that owns that information answers it better. What is here is what only the proxy
knows.

| Placeholder | What it is |
|---|---|
| `%animation:<name>%` | The current frame of an animation |
| `%network_online%` | Players connected to the whole network |
| `%network_max%` | The slot count the proxy advertises |
| `%server_count%` | How many backends are registered |
| `%server_online%` | Players on the player's own server |
| `%server_online:<name>%` | Players on a named server |
| `%server_status:<name>%` | `online` if the server is registered, even when it is down ([#12](https://github.com/adrielorihuela/ProxyPulse/issues/12)); otherwise `unknown` |
| `%server%` | The player's current server |
| `%server_alias%` | That server's friendly name from `config.yml` |
| `%player_server%` | The server of the player a tab row is about |
| `%player%` / `%player_uuid%` | Name and uuid |
| `%ping%` | Real ping between the player and the proxy |
| `%ping_colored%` | The same, coloured green, yellow or red |
| `%client_version%` | e.g. `1.21.5` |
| `%client_brand%` | e.g. `vanilla`, `fabric` |
| `%locale%` | The client's language |
| `%proxy_version%`, `%proxy_uptime%` | About the proxy itself |
| `%date%`, `%time%` | Current date and time |

An unknown placeholder is left on screen exactly as written, rather than being blanked — so a typo
looks like a typo instead of like a missing feature.

## The global tab list

Off by default. Turning it on replaces every server's tab list with one list covering the network:

```yaml
global-tablist:
  enabled: true
  disabled-servers:
    - "minigames"          # these keep their own tab list
  suppress-backend-tablist: true
  name-format: "%player% <dark_gray>[<gray>%player_server%<dark_gray>]"
  sort-by:
    - "server"
    - "name"
  header:
    - "%animation:logo%"
    - "<gray>Players online: <white>%network_online%"
  footer:
    - "<gray>Your ping: <white>%ping%ms"
```

While it is on, the tab list packets the backends send are dropped rather than forwarded — two lists
fighting over the same screen is worse than either alone. A player who walks into a server on
`disabled-servers` gets the injected rows taken back, and that server's own list takes over.

## Commands

`/proxypulse` (also `/pulse`, `/pp`), permission `proxypulse.admin`:

| Command | |
|---|---|
| `reload` | Re-read `config.yml` and `animations.yml` without a restart |
| `animations` | List every loaded animation, its frame count and speed |
| `placeholders` | List every placeholder the proxy understands |
| `status` | What the plugin is currently doing |
| `preview <text>` | Render a line exactly as a backend's text would be rendered, for you |

`preview` is the fastest answer to *"why is my placeholder not showing?"* — if it renders there, the
proxy understands it and the problem is in how the backend is sending the text.

## How it works

Velocity does not hand plugins raw packets, so ProxyPulse inserts a handler into each player's
connection, just upstream of the proxy's packet encoder. At that point a packet is either an object
the proxy decoded or the untouched buffer it is forwarding, and a rewritten packet can be handed
back as either — the encoder downstream still produces the right wire format for whatever client
version is on the other end.

Three decisions are worth knowing about:

**Only the original text is remembered.** When a packet with a placeholder goes past, the session
stores the text *as the backend wrote it*, placeholders intact. A rendered copy would be stale the
moment the clock moved; the original stays true for as long as the element is on screen. Every tick
the plugin re-renders from it and sends the packet again only if the result actually changed.

**Packet ids come from the proxy, not from a table here.** For everything except the scoreboard, the
id of a packet for a given client version is read out of Velocity's own registry, so new Minecraft
versions renumbering packets costs nothing to follow. The scoreboard is the exception — the proxy
has no reason to decode one — and its two ids are the only hand-maintained mapping in the project.
Unknown or newer protocol versions are left alone instead of rewritten with ids that may have moved.

**The scoreboard codec does not try to understand the whole packet.** It currently has
[known bugs](https://github.com/adrielorihuela/ProxyPulse/issues/1) in how it refreshes teams and objectives. It parses only the fields in front
of each piece of text and copies everything after the last one through byte for byte. Where a field
changed shape between versions and sits *between* two pieces of text, both layouts are attempted and
the one whose leftover bytes parse as a well-formed member list — ending exactly at the end of the
packet — is the one used. If neither does, the packet goes out untouched.

Text with no placeholder in it is never tracked, so a network that uses one animation pays for one
animation.

## Building

```bash
./gradlew build
```

The build downloads the Velocity proxy jar once (pinned by SHA-256) because the plugin compiles
against the proxy's internal packet classes; it is used at compile time only and never bundled. The
finished plugin lands in `build/libs/`.

## Licence

[MIT](LICENSE).
