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
  EDITOR_PREFERRED_JUPYTER: "editor.preferredJupyter",
} as const;

// Descriptors for the above keys — typed and validated in the future U1c layer.
export const SETTING_DESCRIPTORS: SettingDescriptor[] = [
  {
    key: SETTING_KEYS.UI_ADVANCED_MODE,
    label: "Advanced mode",
    description: "Shows advanced features like container management and low-level data views.",
    type: "boolean",
    defaultValue: false,
  },
  {
    key: SETTING_KEYS.EDITOR_PREFERRED_JUPYTER,
    label: "JupyterHub base URL",
    description: "Set your JupyterHub base URL to enable 'Open in JupyterHub' buttons.",
    type: "string",
  },
];

export function useSettingDescriptor() {
  function findDescriptor(key: string): SettingDescriptor | undefined {
    return SETTING_DESCRIPTORS.find(d => d.key === key);
  }
  return { findDescriptor };
}
