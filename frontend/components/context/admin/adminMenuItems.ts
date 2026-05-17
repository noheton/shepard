import type { MenuEntry } from "~/components/common/pane-menu/menuListTypes";

export enum AdminFragments {
  FEATURE_TOGGLES = "feature-toggles",
  INSTANCE_HEALTH = "instance-health",
}

export const AdminMenuEntries: MenuEntry[] = [
  {
    name: "Feature Toggles",
    fragment: AdminFragments.FEATURE_TOGGLES,
    icon: "mdi-toggle-switch-outline",
  },
  {
    name: "Instance Health",
    fragment: AdminFragments.INSTANCE_HEALTH,
    icon: "mdi-heart-pulse",
  },
];
