/**
 * Empty-state copy helpers for the provenance log component.
 *
 * Extracted from `DataObjectProvLog.vue` so the branching can be
 * unit-tested without mounting the component (the rest of the file
 * leans on Nuxt auto-imports and a `~/composables/...` path that the
 * Vitest harness doesn't currently resolve).
 *
 * Two distinct empty states:
 *   1. No activities at all on this entity — explain the capture
 *      scope so the user doesn't read "empty" as "broken".
 *      Surfaces the v1-numeric-id + capture-reads constraints
 *      documented in RDM-2026-05-24-004.
 *   2. Activities exist but the in-page action-chip / text filter
 *      narrowed them out — direct the user to widen the filter.
 *
 * See `aidocs/agent-findings/rdm-004-provenance-empty-fix-2026-05-24.md`
 * for the root-cause analysis and the three follow-up backlog rows
 * (PROV-RESOLVER-PATHWALK / PROV-V1-NUMERIC-LOOKUP /
 * PROV-CAPTURE-READS-DECISION).
 */

export function emptyStateLabel(activityCount: number): string {
  return activityCount === 0
    ? "No provenance events recorded yet"
    : "No matching provenance events";
}

export function emptyStateHint(activityCount: number): string {
  if (activityCount === 0) {
    return (
      "Provenance capture is currently scoped to write actions " +
      "(create / update / delete). Reads aren't recorded by default, " +
      "and write activity targeted via legacy numeric-id paths may not " +
      "yet resolve to this entity. See aidocs/55 (PROV1) for the roadmap."
    );
  }
  return "Adjust the action chips or clear the filter to see other events.";
}
