---
paths:
  - "src/test/**"
---

Moved from CLAUDE.md § Conventions on 2026-11-07.

- Tests use JUnit 5 with `@DisplayName` sentences describing the behaviour. There is no mocking
  library: `PlayerSessionTest` stubs `Player` with `java.lang.reflect.Proxy` (Mockito cannot mock it
  on Java 25) and subclasses `ProxyPulse`, which is why that class is not `final`.
- Tests must not depend on the wall clock; pass explicit timestamps or use single-frame animations.
