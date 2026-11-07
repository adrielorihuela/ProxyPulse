# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

A Velocity 4 plugin that rewrites the text backend servers send to players, replacing
`%animation:name%` and network placeholders before the packet reaches the client, and re-sending
those packets as animations advance. See `README.md` for user-facing behaviour.

## How to work in this repo

You are the orchestrator. Your job is to understand, plan, delegate and verify; not to do what a subagent does cheaper.

1. Before touching code, delegate exploration to `explorer` (Haiku). Ask for paths and a summary, not full file contents.
2. Write a spec of 5 to 15 lines: goal, files, acceptance criteria, verification command. Delegate execution to `implementer` (Sonnet).
3. Hand the diff to `reviewer` (Opus High). If REJECTED, send the list back to the implementer. At most two rounds.
4. If it still fails after two rounds, ask before raising effort to xhigh or switching to Fable; explain why in one line.
5. Do yourself only: architecture decisions, changes that cross modules, and the final synthesis.
6. Never turn on ultracode or workflows unless asked; when they are asked for, subagents run on Haiku.
7. Suggest `/clear` when the next task is unrelated to the current one.
8. At the start of the first session each week, run the check in `.claude/lean-check.md` (one skill load; it updates only if the synced preset moved).
9. `/init` in this project is the LeanFlow version (`.claude/skills/init/`); use it to refresh this file.

Verification commands for this repo:

```bash
./gradlew build
```

For a narrower check while iterating: `./gradlew test --tests '*ClassName*'`.

## Estado actual

v1.0.0 is published but not production ready: no player has ever connected to it. When the user says "dónde nos quedamos", "sigue" or "arregla los issues", read [docs/HANDOFF.md](docs/HANDOFF.md), run `gh issue list`, and follow pinned issue #23. Reply in Spanish.

## Commands

```bash
./gradlew build                                   # compile, test, and produce the shaded jar
./gradlew test                                    # tests only
./gradlew test --tests '*ScoreboardCodecTest*'    # one test class
./gradlew test --tests '*ScoreboardCodecTest.teamTextIsParsed'   # one test method
```

The finished plugin is `build/libs/ProxyPulse-<version>.jar`. Targets Java 25 (`release = 25`), so a
JDK 25 toolchain is required.

## Hard constraints

- Compiles against Velocity internals (`com.velocitypowered.proxy.*`): the proxy jar is pinned by URL and SHA-256 in `gradle.properties`, compile-only, never bundled. Change both properties together.
- Do not reintroduce PacketEvents: its Adventure 4 serializer cannot load on Velocity 4's Adventure 5 (retrooper/packetevents#1500).
- Send refreshed packets only through `PlayerSession.send`; `connection.write` re-enters the handler and freezes every animation after one frame.
- `TrackedElement.originals` keeps the backend's text with placeholders intact; never store a rendered copy as the source of truth.
- Chat is never rewritten. Any new packet type must keep `ChatIsNeverRewrittenTest` meaningful.
- `ScoreboardIds` is the one hand-maintained id table, and a wrong id corrupts the connection. Known bug: `protocol > newest + 1` lets 776 (26.2) use the unverified 26.1 ids.
- `ScoreboardCodec` copies unknown fields byte for byte; never choose the team layout from the protocol number alone.

## Conventions

- Two-space indent, `final` on parameters and locals, Javadoc on classes and non-obvious methods.
- Comments explain *why*, especially where a decision protects against a specific failure — that is
  the house style here and reviewers expect it.
- Commits are small and sequential, with a body explaining the reasoning rather than the diff.

## Where things live

- [docs/HANDOFF.md](docs/HANDOFF.md): current state, decisions and next steps
- [docs/architecture.md](docs/architecture.md): packet path, ids and rendering
- [.claude/skills/test-on-proxy/SKILL.md](.claude/skills/test-on-proxy/SKILL.md): running against the local proxy
- [.claude/rules/tests.md](.claude/rules/tests.md): test conventions, loaded for `src/test/**`
- `.claude/agents/`: `explorer`, `implementer`, `reviewer`
