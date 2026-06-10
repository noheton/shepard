#!/usr/bin/env bash
#
# Single source of truth for installing every in-tree shepard plugin the
# backend depends on, into the local Maven repo, in dependency order.
#
# Used by BOTH `make build-plugins` and the GitHub workflows
# (.github/workflows/ci.yml, codeql.yml). Before this script existed the plugin
# install list was duplicated (and drifted) between the Makefile and each
# workflow, which is how `fileformat-svdx`/`-thermography` (always-on backend
# compile deps) ended up missing from CI → "Could not find artifact" → red main.
#
# The authoritative plugin set is the union of:
#   * the backend's always-on main <dependencies> (fileformat-svdx/-thermography),
#   * the backend's `with-plugins` profile (active unless -DnoPlugins).
# Keep this list in sync with backend/pom.xml.
#
# Env:
#   MVN   maven wrapper/binary to use (default: ./backend/mvnw)
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
MVN="${MVN:-$ROOT/backend/mvnw}"

# Fold output in GitHub Actions; no-op locally.
group()   { if [ -n "${GITHUB_ACTIONS:-}" ]; then echo "::group::$1"; else echo "── $1"; fi; }
endgroup(){ if [ -n "${GITHUB_ACTIONS:-}" ]; then echo "::endgroup::"; fi; }

install_plugin() {
  # -DnoPlugins is LOAD-BEARING here. The backend's `with-plugins` profile is
  # activated by `!noPlugins`, and Maven RE-EVALUATES that activation when it
  # resolves the backend dependency POM in *this plugin's* build context. Without
  # -DnoPlugins, every plugin's `provided` backend dep + `test` backend:jar:tests
  # dep transitively drag in ALL sibling plugins (wiki-writer via provided,
  # the rest via test) — an unbootstrappable web from an empty ~/.m2. With
  # -DnoPlugins the profile stays inactive during resolution, so each plugin
  # needs only the two always-on Tier-0 fileformat parsers + the backend stub.
  # Verified: no plugin imports sibling-plugin classes, so this is safe (the
  # earlier "Tier-2 depends on siblings" notion was the leak, not a real dep).
  group "install plugin $1"
  ( cd "plugins/$1" && "$MVN" -B -DnoPlugins -Dmaven.test.skip=true install -q )
  endgroup
}

cli_testjar_stub() {
  # minter-epic, minter-datacite and unhide test-depend on
  # shepard-admin:jar:tests (the CLI test-jar). -Dmaven.test.skip would skip test
  # COMPILATION and never emit it; use -DskipTests so the test-jar is produced
  # (CLI test sources don't reference plugin packages, so -DnoPlugins is fine).
  group "stub-install cli + test-jar (-DnoPlugins -DskipTests)"
  ( cd cli && "$MVN" -B -DnoPlugins -DskipTests -Dquarkus.build.skip=true install -q )
  endgroup
}

backend_testjar_stub() {
  # The 17 plugins test-depend on backend:jar:tests, so the test-jar must exist
  # in ~/.m2 before they install. Building it needs test COMPILATION (-DskipTests,
  # not -Dmaven.test.skip), but 4 backend test files reference in-plugin packages
  # (git adapters / spatialdata) that aren't present under -DnoPlugins — a
  # bootstrap cycle. The bootstrap-testjar profile (-DbootstrapTestjar, see
  # backend/pom.xml) excludes those 4 from THIS compile; the real CI test run
  # rebuilds + runs them with the plugins present.
  group "stub-install backend + test-jar (-DnoPlugins -DbootstrapTestjar)"
  ( cd backend && "$MVN" -B -DnoPlugins -DbootstrapTestjar -DskipTests -Dquarkus.build.skip=true install -q )
  endgroup
}

# ── Tier 0 — standalone pure-Java parsers, NO backend dependency ──────────────
# fileformat-thermography has been upgraded from Tier-0 to Tier-1 (V2CONV-A6):
# it now carries ThermographyV2Rest + services + IOs and depends on the backend
# provided dep. It now lives in the Tier-1 block below.
# fileformat-svdx has been upgraded to Tier-1 (with-plugins profile dep) per
# V2CONV-A6 — it now lives in the Tier-1 block below.

# ── Backend (with test-jar) + CLI stubs so the remaining plugins can compile ──
backend_testjar_stub
cli_testjar_stub

# ── Tier 1 — plugins that depend only on the backend stub ─────────────────────
# fileformat-svdx upgraded from Tier-0 to Tier-1 (V2CONV-A6): it now carries
# SvdxIngestRest + SvdxCsvIngestionService and depends on the backend provided dep.
for p in \
  fileformat-svdx \
  fileformat-thermography \
  fileformat-cad \
  fileformat-robotics \
  minter-local minter-datacite minter-epic \
  unhide kip hdf5 git file-s3 aas importer \
  spatiotemporal wiki-writer analytics-ts
do
  install_plugin "$p"
done

# ── Tier 2 — historically thought to depend on tier-1 siblings, but with
#    -DnoPlugins each resolves against only the backend stub + Tier-0 parsers
#    (verified: none import sibling-plugin classes). Kept in a separate group
#    only for readability; install order among plugins is no longer load-bearing.
install_plugin ai
install_plugin video
install_plugin v1-compat
install_plugin vis-trace3d
install_plugin krl-interpreter

echo "All shepard plugins installed."
