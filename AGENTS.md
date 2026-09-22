# AGENTS.md

Working rules for any agent or contributor on this project.
These are binding. Violations waste time and produce wrong experimental results.

## R7 — Consequence descriptions must be factual

Do not use words more severe than the actual phenomenon (e.g. "CPU burned
up"). Write: CPU saturation / high CPU usage / chunk desync / data
corruption. Exaggeration devalues the real red lines.

## R8 — Be rigorous and strict

- Write expected results before an experiment; check them after.
- Never deploy on a failed build.
- One variable per round.
- Verify the code is actually running first (version marker, flush, logs).
- Switch to measurement after two failed theories.
- Log shared state before touching it.
- Public repo: only usable code is committed.

## The five iron rules (project history)

1. Establish the domain ledger before touching anything.
2. Verify the code actually runs (flush, no deploy on build failure, version
   line, jar marker).
3. After two failed theories, stop theorising and measure.
4. One variable per round.
5. Log shared state in the first round.

## Key facts

- Engine is fully local-domain; real domain = BigInteger (exact) + double
  (sampling).
- Fork jar must be identity-patched (wide + continuity + epoch flags) and
  kept in sync with the mod's patch set. Mismatch = double conversion.
- Config is `world/farlands.properties`; invalid values halt the JVM by
  design (`POLICY VIOLATION`).
- Push only when the user's proxy is on; the user controls it.
- Chinese files: use read/write/edit tools, never PowerShell
  Get-Content/Set-Content. Fork copies need UTF-8 BOM.

## References

- Red lines R1-R8: `docs/USAGE.md` (zh) / `docs/USAGE.en.md` (en)
- Config reference + presets: `docs/CONFIG.md` / `docs/CONFIG.en.md`
- Roadmap and lessons: `docs/ROADMAP.md`
- Review: `docs/REVIEW.md`
