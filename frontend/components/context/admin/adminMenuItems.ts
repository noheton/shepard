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
  PERMISSION_AUDIT_LOG = "permission-audit-log",
  UNHIDE = "unhide",
  LEGACY_V1 = "legacy-v1",
  // ----- placeholder fragments (no-UI-gap roll-out 2026-05-24) -----
  FILE_MIGRATION = "file-migration",
  SQL_TIMESERIES = "sql-timeseries",
  NOTIFICATIONS_ADMIN = "notifications-admin",
  INSTANCE_ADMINS = "instance-admins",
  USERS_ORCID = "users-orcid",
  USERS_GIT = "users-git",
  AI_CONFIG = "ai-config",
  BACKUP = "backup",
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
  {
    name: "Permission Audit Log",
    fragment: AdminFragments.PERMISSION_AUDIT_LOG,
    icon: "mdi-shield-account-outline",
  },
  {
    name: "Unhide",
    fragment: AdminFragments.UNHIDE,
    icon: "mdi-web-sync",
  },
  {
    name: "Legacy v1",
    fragment: AdminFragments.LEGACY_V1,
    icon: "mdi-history",
  },
  // ----- placeholder menu entries (no-UI-gap roll-out 2026-05-24) -----
  {
    name: "File migration",
    fragment: AdminFragments.FILE_MIGRATION,
    icon: "mdi-database-arrow-right-outline",
  },
  {
    name: "SQL timeseries",
    fragment: AdminFragments.SQL_TIMESERIES,
    icon: "mdi-database-search-outline",
  },
  {
    name: "Notifications",
    fragment: AdminFragments.NOTIFICATIONS_ADMIN,
    icon: "mdi-bell-cog-outline",
  },
  {
    name: "Instance admins",
    fragment: AdminFragments.INSTANCE_ADMINS,
    icon: "mdi-shield-crown-outline",
  },
  {
    name: "User ORCID",
    fragment: AdminFragments.USERS_ORCID,
    icon: "mdi-account-key-outline",
  },
  {
    name: "User git credentials",
    fragment: AdminFragments.USERS_GIT,
    icon: "mdi-source-branch-plus",
  },
  {
    name: "AI configuration",
    fragment: AdminFragments.AI_CONFIG,
    icon: "mdi-robot-outline",
  },
  {
    name: "Backup",
    fragment: AdminFragments.BACKUP,
    icon: "mdi-backup-restore",
  },
];
