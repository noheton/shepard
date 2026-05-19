import type { MenuEntry } from "~/components/common/pane-menu/menuListTypes";

export enum AdminFragments {
  FEATURE_TOGGLES = "feature-toggles",
  PLUGINS = "plugins",
  INSTANCE_HEALTH = "instance-health",
  TEMPLATES = "templates",
  SEMANTIC_REPOSITORIES = "semantic-repositories",
  USER_GROUPS = "user-groups",
  INSTANCE_ROR = "instance-ror",
  STORAGE_OVERVIEW = "storage-overview",
}

export const AdminMenuEntries: MenuEntry[] = [
  {
    name: "Feature Toggles",
    fragment: AdminFragments.FEATURE_TOGGLES,
    icon: "mdi-toggle-switch-outline",
  },
  {
    name: "Plugins",
    fragment: AdminFragments.PLUGINS,
    icon: "mdi-puzzle-outline",
  },
  {
    name: "Instance Health",
    fragment: AdminFragments.INSTANCE_HEALTH,
    icon: "mdi-heart-pulse",
  },
  {
    name: "Storage Overview",
    fragment: AdminFragments.STORAGE_OVERVIEW,
    icon: "mdi-database-eye-outline",
  },
  {
    name: "Templates",
    fragment: AdminFragments.TEMPLATES,
    icon: "mdi-file-document-multiple-outline",
  },
  {
    name: "Semantic Repositories",
    fragment: AdminFragments.SEMANTIC_REPOSITORIES,
    icon: "mdi-library-outline",
  },
  {
    name: "User Groups",
    fragment: AdminFragments.USER_GROUPS,
    icon: "mdi-account-multiple-outline",
  },
  {
    name: "Research Organization",
    fragment: AdminFragments.INSTANCE_ROR,
    icon: "mdi-office-building",
  },
];
