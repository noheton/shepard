#!/usr/bin/env bash
#
# CG1b — v1-shelf scope smoke test.
#
# Asserts that the OpenAPI Generator output under clients/{java,python,
# typescript} corresponds to the byte-frozen /shepard/api/... shelf only —
# i.e. has zero references to /v2/ paths or operation-name shapes. The
# regression fence behind ADR-0022's "still-maintained legacy generator"
# posture: if a future change accidentally retargets the input back to the
# combined OpenAPI doc, this test fails loudly in CI.
#
# Usage:
#     ./clients/tests/v1-scope-smoke-test.sh [language]
#
# `language` is one of: java, python, typescript. With no argument, runs
# all three sequentially.
#
# Exit codes:
#   0 — every checked client is v1-only
#   1 — a /v2/ leak was detected (or the positive sanity-check failed)
#
# The test is grep-and-shape based, not import-graph based, so it works
# pre-build (i.e. immediately after generation) without needing a Java /
# Python / Node toolchain.

set -euo pipefail

readonly REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
readonly CLIENTS_DIR="${REPO_ROOT}/clients"

# Files that legitimately contain "/v2/" outside the generator output —
# excluded from the leak grep so a documentation reference here doesn't
# fail the fence.
declare -ar EXCLUDE_DOC_PATHS=(
    "README.md"
    ".openapi-generator/FILES"
)

red() { printf '\033[31m%s\033[0m\n' "$*"; }
green() { printf '\033[32m%s\033[0m\n' "$*"; }
yellow() { printf '\033[33m%s\033[0m\n' "$*"; }

# Positive sanity check: assert the generated client mentions at least one
# known-good v1-shelf endpoint or class. Without this, an empty generator
# output would pass the leak check trivially.
#
# We look for case-insensitive "collection" — the upstream API has had a
# CollectionsApi / collections_api / CollectionsApi.ts class since 5.0.x.
# Any future rename should update this constant rather than weaken the
# fence.
readonly V1_CANARY_PATTERN="[Cc]ollection"

# The /v2/ leak pattern we forbid. `/v2/` is unambiguous in OpenAPI
# Generator output — operation method names, path constants, and Javadoc
# all contain the literal substring.
readonly V2_LEAK_PATTERN="/v2/"

check_language() {
    local lang="$1"
    local lang_dir="${CLIENTS_DIR}/${lang}"

    if [[ ! -d "${lang_dir}" ]]; then
        yellow "[skip] ${lang}: directory ${lang_dir} not present (generator hasn't run for this language)"
        return 0
    fi

    echo "Checking ${lang} client at ${lang_dir} ..."

    # Build the grep --exclude argument list dynamically from
    # EXCLUDE_DOC_PATHS so the leak check ignores legitimate doc
    # references.
    local exclude_args=()
    for pattern in "${EXCLUDE_DOC_PATHS[@]}"; do
        exclude_args+=("--exclude=${pattern}")
    done

    # Leak check: any /v2/ occurrence in the generator output is a fail.
    local leaks
    if leaks="$(grep -r -F -l "${exclude_args[@]}" -- "${V2_LEAK_PATTERN}" "${lang_dir}" 2>/dev/null)"; then
        red "[fail] ${lang}: /v2/ leak detected in:"
        echo "${leaks}" | sed 's/^/    /'
        echo
        red "       The CG1b legacy generator must target the /shepard/api/ shelf only."
        red "       Re-check the --input-spec value in .gitlab/ci/clients/${lang}.gitlab-ci.yml"
        red "       — it should point at openapi_v1.json (the slicer output), not openapi.json."
        return 1
    fi

    # Positive sanity check: the generator produced at least one v1-shelf
    # class / module / path reference. Catches the "we accidentally
    # emitted an empty client" failure mode.
    if ! grep -r -q -l "${V1_CANARY_PATTERN}" "${lang_dir}" 2>/dev/null; then
        red "[fail] ${lang}: positive sanity check failed — no '${V1_CANARY_PATTERN}'"
        red "       reference found anywhere under ${lang_dir}."
        red "       The generator output looks empty — verify the input spec was the"
        red "       v1-shelf doc and not e.g. an empty file."
        return 1
    fi

    green "[ok]   ${lang}: v1-shelf scope confirmed (no /v2/ leak; canary '${V1_CANARY_PATTERN}' present)"
    return 0
}

main() {
    local langs=()
    if [[ $# -ge 1 ]]; then
        langs=("$1")
    else
        langs=(java python typescript)
    fi

    local failed=0
    for lang in "${langs[@]}"; do
        if ! check_language "${lang}"; then
            failed=1
        fi
    done

    echo
    if [[ ${failed} -eq 0 ]]; then
        green "All checked clients are v1-shelf only — CG1b scope fence green."
        return 0
    else
        red "CG1b v1-shelf scope fence: FAILED. See messages above."
        return 1
    fi
}

main "$@"
