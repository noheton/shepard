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
  group "install plugin $1"
  ( cd "plugins/$1" && "$MVN" -B -Dmaven.test.skip=true install -q )
  endgroup
}

stub_install() {
  # -Dmaven.test.skip=true (NOT -DskipTests): the backend's *test* sources
  # reference plugin packages (git adapters, spatialdata model, …) that are
  # absent under -DnoPlugins, so test COMPILATION — not just execution — must be
  # skipped or the stub-install fails to compile. -DskipTests only skips
  # execution, which is the bug that previously lurked in ci.yml's stub step.
  group "stub-install $1 (-DnoPlugins)"
  ( cd "$1" && "$MVN" -B -DnoPlugins -Dmaven.test.skip=true -Dquarkus.build.skip=true install -q )
  endgroup
}

# ── Tier 0 — standalone pure-Java parsers, NO backend dependency ──────────────
# These are always-on (non-profile) compile deps of the backend core (the v2
# svdx/thermography services call their parsers directly), so they MUST be in
# ~/.m2 before the backend stub-install or `mvn install` on backend fails at
# dependency resolution.
install_plugin fileformat-svdx
install_plugin fileformat-thermography

# ── Backend + CLI stubs (no plugins) so the remaining plugins can compile ─────
stub_install backend
stub_install cli

# ── Tier 1 — plugins that depend only on the backend stub ─────────────────────
for p in \
  minter-local minter-datacite minter-epic \
  unhide kip hdf5 git file-s3 aas importer \
  spatiotemporal wiki-writer analytics-ts
do
  install_plugin "$p"
done

# ── Tier 2 — plugins that depend on tier-1 siblings ───────────────────────────
install_plugin ai           # provided dep on wiki-writer
install_plugin video
install_plugin v1-compat    # dep on wiki-writer
install_plugin vis-trace3d  # dep on analytics-ts, v1-compat, video, wiki-writer

echo "All shepard plugins installed."
