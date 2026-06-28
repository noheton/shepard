import type { MenuEntry } from "~/components/common/pane-menu/menuListTypes";

export enum AdminFragments {
  FEATURE_TOGGLES = "feature-toggles",
  PLUGINS = "plugins",
  INSTANCE_HEALTH = "instance-health",
  TEMPLATES = "templates",
  SEMANTIC_REPOSITORIES = "semantic-repositories",
  ONTOLOGY_BUNDLES = "ontology-bundles",
  SPARQL_PLAYGROUND = "sparql-playground",
  USER_GROUPS = "user-groups",
  INSTANCE_ROR = "instance-ror",
  STORAGE_OVERVIEW = "storage-overview",
  PERMISSION_AUDIT_LOG = "permission-audit-log",
  ACTIVITY_LOG = "activity-log",
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
  ONTOLOGY_ALIGNMENT = "ontology-alignment",
  // SEMA-V6-014
  SEMANTIC_CONFIG = "semantic-config",
  // FE-PROV-INSTANCE-REGISTRY
  INSTANCE_REGISTRY = "instance-registry",
  // J1e
  JUPYTER = "jupyter",
  // UI-GAP-3
  CONFIG_OVERVIEW = "config-overview",
  // MFFD-BATCH-01
  BATCH_CREATE = "batch-create",
  // MISSING-aas-ui Slice 3
  AAS_CONFIG = "aas-config",
}

export const AdminMenuEntries: MenuEntry[] = [
  {
    name: "Feature Toggles",
    fragment: AdminFragments.FEATURE_TOGGLES,
    icon: "mdi-toggle-switch-outline",
  },
  // UI-GAP-3
  {
    name: "Runtime Config Registry",
    fragment: AdminFragments.CONFIG_OVERVIEW,
    icon: "mdi-tune-vertical-variant",
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
    name: "Ontology Bundles",
    fragment: AdminFragments.ONTOLOGY_BUNDLES,
    icon: "mdi-owl",
  },
  {
    name: "SPARQL Playground",
    fragment: AdminFragments.SPARQL_PLAYGROUND,
    icon: "mdi-database-search-outline",
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
    name: "Activity Log",
    fragment: AdminFragments.ACTIVITY_LOG,
    icon: "mdi-history",
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
  {
    name: "Ontology Alignment",
    fragment: AdminFragments.ONTOLOGY_ALIGNMENT,
    icon: "mdi-graph-outline",
  },
  // SEMA-V6-014
  {
    name: "Semantic Config",
    fragment: AdminFragments.SEMANTIC_CONFIG,
    icon: "mdi-tag-multiple-outline",
  },
  // FE-PROV-INSTANCE-REGISTRY
  {
    name: "Instance Registry",
    fragment: AdminFragments.INSTANCE_REGISTRY,
    icon: "mdi-map-marker-multiple-outline",
  },
  // J1e
  {
    name: "JupyterHub link-out",
    fragment: AdminFragments.JUPYTER,
    icon: "mdi-jupyter",
  },
  // MFFD-BATCH-01
  {
    name: "Bulk DataObject creation",
    fragment: AdminFragments.BATCH_CREATE,
    icon: "mdi-layers-plus",
  },
  // MISSING-aas-ui Slice 3
  {
    name: "AAS Integration",
    fragment: AdminFragments.AAS_CONFIG,
    icon: "mdi-layers-triple-outline",
  },
];
