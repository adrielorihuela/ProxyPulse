---
name: test-on-proxy
description: "Deploys the built plugin to the local Velocity proxy in testenv/ and drives it from the console. Use whenever the user wants to run, smoke-test or verify ProxyPulse on a real proxy, or check that it loads and reloads."
---

Moved from CLAUDE.md § Running it against a real proxy on 2026-11-07.

### Running it against a real proxy

`testenv/` (gitignored) holds a local Velocity 4 proxy and a Paper backend used for manual
verification. Deploy and drive it from the console over stdin:

```bash
cp build/libs/ProxyPulse-1.0.0.jar testenv/proxy/plugins/ && cd testenv/proxy && (printf 'proxypulse status\n'; sleep 15; printf 'end\n') | java -jar velocity.jar
```

Anything past "does it load and reload" needs a real Minecraft client: nothing is tracked until a
player connects and a backend sends text.
