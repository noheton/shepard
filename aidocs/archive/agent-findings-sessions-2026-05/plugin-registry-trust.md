---
stage: decommissioned
last-stage-change: 2026-05-23
---

# Plugin Registry Trust Mechanisms — Convenience-First Survey

**Scope**: v1 build-time registry inclusion. DLR publishes
`https://shepard.dlr.de/plugins/registry.ttl` listing vetted plugins
with `sourceRepo` + `pinnedVersion`/`pinnedCommit` + `moderatedBy`.
Operators read it as advisory source-of-truth and bake the listed
version into their image build.

## §1 — The Trust Surface

Two things need integrity:

1. **The registry document** (`registry.ttl`). If an attacker rewrites
   it, they can redirect a `pinnedCommit` to a different SHA inside
   a legitimate repo fork they control, or swap `sourceRepo` to a
   typosquat.
2. **The plugin source at the pinned ref**. Here the threat largely
   collapses: **a git commit SHA is already a content-addressed
   integrity hash**. If the registry says `sourceRepo: git@…/foo` +
   `pinnedCommit: abc123…`, an attacker cannot substitute different
   code at that SHA without breaking git's Merkle chain. The
   operator's `git clone && git checkout abc123` is self-verifying.

This reframing matters: the load-bearing question in v1 is **only**
"is the registry document I fetched the one DLR published?" The
JAR-signing layer (TUF target metadata, SRI on artifacts) is mostly
redundant with what git gives us for free.

Attacker scenarios actually in scope:

- **A1**: MITM on the HTTPS fetch of `registry.ttl` (defeated by TLS).
- **A2**: Compromise of `shepard.dlr.de` web root, registry rewritten
  in place — pinned commit now points at attacker's commit in an
  attacker-controlled fork.
- **A3**: Compromise of the moderator's CI / signing identity.

A2 is the gotcha that pure-HTTPS doesn't catch.

## §2 — Survey

| Mechanism | Moderator (1–5) | Operator (1–5) | Federation | Key rotation | Audit log | DLR-realistic | Composes w/ CI gates |
|---|---|---|---|---|---|---|---|
| **HTTPS only, pinned cert** | 1 | 1 | yes | n/a (TLS PKI handles) | no | yes | cleanly |
| **PGP detached sig (.asc)** | 3 | 3 | yes | manual (republish key, rotate WKD) | no | yes | cleanly |
| **Sigstore / cosign keyless** | 2 | 2 | yes (per-registry identity claim) | clean (short-lived certs, no long-lived key) | yes (Rekor) | maybe (needs OIDC provider) | cleanly |
| **GitHub artifact attestations** | 1 (if on GH) | 2 | yes | clean | yes (Sigstore-backed) | no (DLR runs internal GitLab) | cleanly |
| **TUF** | 5 | 4 | awkward (per-repo roots) | clean (designed for it) | yes | no — disproportionate | cleanly but heavy |
| **SRI hash in operator config** | 1 | 3 (must update on every registry change) | yes | n/a | no | yes | cleanly |
| **GPG-signed git tags on registry repo** | 2 | 2 | yes | manual but bounded | yes (git history) | yes (GitLab GPG already used) | cleanly |
| **Hybrid: signed git registry + sigstore over .ttl** | 2 | 2 | yes | clean | yes (git + Rekor) | yes | cleanly |

Notes on the discards:

- **TUF**: designed for adversarial repository compromise with full
  role-rotation hierarchy (root/targets/snapshot/timestamp). PEP 458
  took years and a grant to land on PyPI. For a curated whitelist
  with ~dozens of entries, this is rocket-launcher-for-mosquito.
- **SRI hash in operator config**: operator must edit
  `application.properties` every time the registry updates — kills
  the "subscribe and forget" ergonomic. Useful as a *belt-and-braces*
  layer for the bit-identity-required-by-regulator case (LegacyPlugin
  rows in a EN 9100 environment), not as the default.
- **GitHub attestations**: brilliant if you're already on GitHub
  Actions; DLR's primary forge is internal GitLab, so the OIDC issuer
  that backs the keyless cert isn't available out-of-the-box.

## §3 — Recommendation (convenience-first)

**Lean**: ship registry-as-signed-git-repo in v1, layer sigstore in v2
if pain materialises.

Concretely:

1. **DLR publishes the registry as a git repo**, not just a static
   `.ttl` on `shepard.dlr.de`. The canonical URL becomes
   `https://gitlab.dlr.de/shepard/plugin-registry` (or the
   `dlr-shepard` group on a public forge). The HTTPS endpoint at
   `shepard.dlr.de/plugins/registry.ttl` is a convenience mirror
   pointing at `HEAD:registry.ttl`.
2. **Moderator workflow**: edit `registry.ttl`, `git commit -S`
   (signed with the moderator's GitLab GPG key — DLR already has
   GitLab GPG verification working), `git push`. That's it. No
   separate signing step, no key-server choreography.
3. **Operator workflow**: in `:RegistryConfig`, the operator
   registers `{ url, expectedSignerKeyIds }`. The build-time
   fetcher does `git clone --depth=1 <url>`, checks the HEAD
   commit's signature against the allowed key ring, then reads
   `registry.ttl`. One config block per registry; subscribe-and-
   forget thereafter.
4. **Layered defence**: each plugin row's `pinnedCommit` carries
   its own content-addressed integrity. Trust chain is *signed
   registry commit → pinned plugin commit*. Both are git's native
   primitives.

**Why this wins on convenience**:

- Moderator effort: a normal `git commit -S` they already do for
  every other DLR repo. Zero new tooling.
- Operator effort: one URL + one or more key-IDs in the admin
  config. Same pattern as APT's `signed-by=/usr/share/keyrings/…`.
- Federation: trivial — each registry has its own repo URL and
  its own allowed key-IDs. Lab-local registries, community
  registries, all the same shape.
- Audit trail: the registry's git log *is* the audit log. "When
  did this plugin enter the moderated set, who approved it"
  becomes `git log --follow registry.ttl`.
- Composes cleanly with all six CLAUDE.md security gates: the
  fetched plugin source still goes through gitleaks +
  dependency-review + OWASP + Trivy + SpotBugs + CodeQL in the
  operator's image build. Signing the registry says nothing about
  the plugin's code quality — that's why the CI gates stay.
- DLR-realistic in 2026: GitLab GPG signing is live infrastructure.
  No new key management, no Fulcio/Rekor stand-up.

**v2 upgrade path** (when runtime loading lands): add `cosign
verify-blob` over the resolved `registry.ttl` and over each plugin
JAR. Sigstore's identity-claim model layers on top of the signed-git
foundation without replacing it. Operators who don't need it ignore
it; regulated-environment operators turn it on via a `:RegistryConfig`
flag.

## §4 — `[NEEDS-CLARIFICATION]`

1. **Does DLR publish the registry from GitLab CI, GitHub Actions,
   or a human commit?** Determines whether sigstore-keyless-via-OIDC
   is "free" in v2 or requires standing up an OIDC provider for an
   internal GitLab instance.
2. **Is `shepard.dlr.de` operated by the same team as the registry
   curators?** If co-located, HTTPS-only is weaker than it looks
   (A2 scenario); if separated, the signed-git approach is even
   stronger because the publish path and the host are different
   attack surfaces.
3. **For regulated-environment operators (EN 9100, EASA Part 21
   shops), do they require a documented bit-identity guarantee on
   the registry document itself**, or is the pinned-commit guarantee
   on each plugin sufficient? Answer determines whether SRI-on-
   registry becomes a v1 `:RegistryConfig` opt-in or stays a v2
   nice-to-have.

## §5 — Decision-Deferral Note

These choices become load-bearing only when **runtime plugin loading
from the registry** lands (the v2 conversation in
`project_plugin_categories.md` T4). At that point:

- JAR-level signature verification at classloader load becomes
  non-negotiable (today the operator's image build runs the CI gates
  on the source; at runtime the JAR is opaque).
- Sigstore/cosign on the JAR artifact is the obvious answer — same
  identity-claim model as the registry document, and there's an
  ecosystem (cosign, policy-controller) to reuse.
- TUF *might* re-enter the conversation if the registry grows to
  hundreds of plugins with hot-swap semantics. The role hierarchy
  earns its complexity at that scale; it does not at v1.

Until then, the recommendation above keeps the moderator's workflow
to "edit, commit, push" and the operator's workflow to "one config
block, then forget."
