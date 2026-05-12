import type { MenuEntry } from "~/components/common/pane-menu/menuListTypes";

export enum AdminFragments {
  FEATURE_TOGGLES = "feature-toggles",
}

export const AdminMenuEntries: MenuEntry[] = [
  {
    name: "Feature Toggles",
    fragment: AdminFragments.FEATURE_TOGGLES,
    icon: "mdi-toggle-switch-outline",
  },
];
