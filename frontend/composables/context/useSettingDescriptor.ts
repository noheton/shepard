/**
 * SettingDescriptor — typed metadata for a user preference key.
 *
 * Consumed by the preferences pane (U1d) to provide validation and
 * UI hints without hardcoding key names in components.
 *
 * The typed-validation layer is deferred (U1c skeleton only).
 */
export interface SettingDescriptor<T = unknown> {
  key: string;
  label: string;
  description?: string;
  type: "string" | "boolean" | "number";
  defaultValue?: T;
  // Returns null if valid, error message string if invalid.
  validate?: (value: T) => string | null;
}

/** Well-known setting keys — extend as new preferences are introduced. */
export const SETTING_KEYS = {
  UI_ADVANCED_MODE: "ui.advancedMode",
} as const;

// Descriptors for the above keys — typed and validated in the future U1c layer.
//
// Note (task #240, 2026-05-30): the per-user `editor.preferredJupyter` setting
// was removed in favour of the admin-configurable `:JupyterConfig` singleton
// (`/v2/admin/jupyter/config`, composable `useJupyterConfig`). The
// "Open in JupyterHub" affordance now reads from the public sister endpoint
// `/v2/jupyter/config` — a single instance-wide hub URL.
export const SETTING_DESCRIPTORS: SettingDescriptor[] = [
  {
    key: SETTING_KEYS.UI_ADVANCED_MODE,
    label: "Advanced mode",
    description: "Shows advanced features like container management and low-level data views.",
    type: "boolean",
    defaultValue: false,
  },
];

export function useSettingDescriptor() {
  function findDescriptor(key: string): SettingDescriptor | undefined {
    return SETTING_DESCRIPTORS.find(d => d.key === key);
  }
  return { findDescriptor };
}
