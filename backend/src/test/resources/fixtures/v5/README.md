# v5 wire-fidelity fixture corpus

This directory holds canonical JSON responses for `/shepard/api/...` endpoints —
the upstream-frozen surface per `CLAUDE.md §"API-version policy"`. The fixtures
**define the wire contract** the fork keeps with `gitlab.com/dlr-shepard/shepard 5.2.0`.

See `backend/src/test/java/de/dlr/shepard/integrationtests/wirefidelity/V5WireFidelityTest.java`
for the test infrastructure that consumes these fixtures.

## Layout

```
fixtures/v5/
├── README.md                              (this file)
├── collections/
│   ├── create.response.json
│   ├── get-single.response.json
│   └── list.response.json
├── dataobjects/
│   ├── create.response.json
│   └── get-single.response.json
├── filecontainers/
│   ├── create.response.json
│   └── get-single.response.json
├── timeseriescontainers/
│   ├── create.response.json
│   └── get-single.response.json
├── structureddatacontainers/
│   ├── create.response.json
│   └── get-single.response.json
├── filereferences/
│   └── create.response.json
├── timeseriesreferences/
│   └── create.response.json
├── structureddatareferences/
│   └── create.response.json
├── users/
│   ├── me.response.json
│   └── list.response.json
└── permissions/
    └── collection-permissions.response.json
```

Each `.response.json` is a JSON document whose dynamic fields (`id`, `appId`,
`createdAt`, `createdBy`, `updatedAt`, …) have been replaced with sentinel
placeholders. See `V5JsonNormalizer` javadoc for the full sentinel list.

## Adding / updating fixtures

See the class javadoc on `V5WireFidelityTest` — short version:

1. Subclass `V5WireFidelityTest`, write a test that issues a deterministic
   request and calls `assertWireMatches(slug, fixtureId, response)`.
2. Run once with `-Dshepard.fixtures.record=true` to generate the file.
3. Review the generated JSON to confirm every dynamic field is replaced with
   a `<<…>>` sentinel.
4. Run again without the system property to confirm the assertion passes.

**Updating a fixture is a wire-contract change.** Re-record only when an
intentional, reviewed change has been approved. Cross-reference the change in
the same PR's `aidocs/34-upstream-upgrade-path.md` row and call it out in
`docs/reference/v5-cross-instance-quirks.md`.
