/**
 * V2CONV-B5-FE — pure helpers for InterpretAsTrajectoryButton.
 *
 * Extracted so unit tests can import without mounting Vue / Vuetify. The
 * picker helpers are the converged successors of the deleted
 * runKrlPreviewHelpers (which fed the bespoke /v2/krl/interpret dialog).
 */

/** `.src` (KUKA Robot Language) / `.krl` source-file extension test. Case-insensitive. */
export function isKrlSrcFile(name: string | undefined | null): boolean {
  if (!name) return false;
  const lower = name.toLowerCase();
  return lower.endsWith(".src") || lower.endsWith(".krl");
}

/** `.urscript` / `.script` (Universal Robots) source-file extension test. Case-insensitive. */
export function isUrScriptFile(name: string | undefined | null): boolean {
  if (!name) return false;
  const lower = name.toLowerCase();
  return lower.endsWith(".urscript") || lower.endsWith(".script");
}

export function isTrajectoryFormValid(state: {
  urdfFileAppId: string | null;
  targetDataObjectAppId: string;
  timeseriesContainerAppId: string;
}): boolean {
  return (
    !!state.urdfFileAppId &&
    state.urdfFileAppId.trim().length > 0 &&
    state.targetDataObjectAppId.trim().length > 0 &&
    state.timeseriesContainerAppId.trim().length > 0
  );
}

/**
 * Pure filter: extract URDF FileReferences from a candidate list, mapping to the
 * {title, value} shape v-autocomplete expects.
 */
export function urdfPickerOptions(
  refs: Array<{ name: string; appId?: string | null }>,
): Array<{ title: string; value: string }> {
  return refs
    .filter(r => r.name.toLowerCase().endsWith(".urdf"))
    .map(r => ({ title: r.name, value: r.appId ?? "" }))
    .filter(o => o.value !== "");
}

export function datPickerOptions(
  refs: Array<{ name: string; appId?: string | null }>,
): Array<{ title: string; value: string }> {
  return refs
    .filter(r => r.name.toLowerCase().endsWith(".dat"))
    .map(r => ({ title: r.name, value: r.appId ?? "" }))
    .filter(o => o.value !== "");
}
