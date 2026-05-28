# Frontend lint config notes

## Root cause

`eslint` was missing from `frontend/package.json` `devDependencies`. The
`@nuxt/eslint@1.15.2` module declares `eslint ^9.0.0 || ^10.0.0` as a peer
dependency, but the peer was never installed because no direct dependency
required it. As a result:

- The local `node_modules/.bin/eslint` shim was never created.
- The npm script `eslint .` fell back to whatever `eslint` happened to be on
  `PATH` — on this dev box that was an ancient ESLint 6.4.0, which does not
  understand the flat-config `eslint.config.mjs` and emits

  > ESLint couldn't find a configuration file. To set up a configuration file
  > for this project, please run: `eslint --init`.

- The same broken-peer state also meant npm skipped materialising
  `@nuxt/eslint-config` and `@nuxt/eslint-plugin` (deps of `@nuxt/eslint`),
  so even invoking the right eslint binary would not have resolved
  `./.nuxt/eslint.config.mjs`.

## Fix

Single change in `frontend/package.json` `devDependencies`:

```json
"eslint": "^9.18.0"
```

ESLint 9 was chosen because:

- `@nuxt/eslint@1.15.2`'s peer requires `^9.0.0 || ^10.0.0`.
- The flat-config wrapper at `frontend/eslint.config.mjs` (re-exporting
  `./.nuxt/eslint.config.mjs`) is a flat config — ESLint 8 still requires
  `ESLINT_USE_FLAT_CONFIG=true` to honour it; ESLint 9 treats flat config as
  the default. Pinning to 9 avoids that opt-in flag.
- `^9.18.0` is a moderate pin: it allows minor / patch updates within the
  major and stays well within the peer-dep range. We are not jumping to the
  10.x line because `@nuxt/eslint` 1.15.2 has not been tested against it.

After `npm install`, the previously-skipped transitive deps
(`@nuxt/eslint-config`, `@nuxt/eslint-plugin`, the typescript-eslint stack,
`vue-eslint-parser`, etc.) install correctly because the peer is now
satisfied. No other version pins were needed — the typescript-eslint /
parser stack arrives through `@nuxt/eslint-config` at the version Nuxt's
flat config expects, and pinning them at the top level risks skew.

## What the operator should expect

Running `npm run lint` in `frontend/` now:

- Resolves the local `node_modules/.bin/eslint` (version 9.x).
- Loads the flat config at `frontend/eslint.config.mjs` which delegates to
  the Nuxt-generated `.nuxt/eslint.config.mjs`.
- Iterates the configured glob and reports lint findings against existing
  source.
- Exits 0 if clean, 1 if errors are found.

## Known pending lint debt (not in scope of this fix)

At the time of the fix, `npm run lint` reports **149 problems
(111 errors, 38 warnings)** on pre-existing code. Approximate categorisation:

- `import/first` — test files mixing imports with `vi.mock(...)` factory
  calls before the import block (vitest pattern; common in `tests/unit/*`).
- `@typescript-eslint/no-unused-vars` — leftover destructured names in
  fixtures.
- `@typescript-eslint/no-dynamic-delete` — `delete obj[key]` in mock
  setup helpers.
- `@typescript-eslint/ban-ts-comment` — bare `@ts-expect-error` without
  the required 10+ char description.
- `@typescript-eslint/no-explicit-any` — test scaffolding `any` usage.
- Unused `eslint-disable` directives in `utils/colormap.ts` — rules no
  longer fire there; the comments can be deleted.

These are pre-existing and out of scope for the lint-runs-at-all fix.
Filing as backlog rows is appropriate; do not silence the rules globally.

## Vitest impact

`npx vitest run` shows the same pre-existing failure pattern (1 failed
file / 5 failed tests in `useFetchRecentCollections.test.ts`) before and
after the eslint pin. No new test regressions introduced by this fix.
