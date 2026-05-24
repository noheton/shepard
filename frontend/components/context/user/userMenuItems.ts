import type { MenuEntry } from "~/components/common/pane-menu/menuListTypes";

export enum UserFragments {
  PROFILE = "profile",
  API_KEYS = "api-keys",
  SUBSCRIPTIONS = "subscriptions",
  GIT_CREDENTIALS = "git-credentials",
  MCP = "mcp",
  // ----- placeholder fragments (no-UI-gap roll-out 2026-05-24) -----
  AI_SETTINGS = "ai-settings",
}

export const UserMenuEntries: MenuEntry[] = [
  {
    name: "Profile",
    fragment: UserFragments.PROFILE,
    icon: "mdi-account-outline",
  },
  {
    name: "Api Keys",
    fragment: UserFragments.API_KEYS,
    icon: "mdi-key-outline",
  },
  {
    name: "MCP",
    fragment: UserFragments.MCP,
    icon: "mdi-robot-outline",
  },
  {
    name: "Subscriptions",
    fragment: UserFragments.SUBSCRIPTIONS,
    icon: "mdi-bell-outline",
  },
  {
    name: "Git Credentials",
    fragment: UserFragments.GIT_CREDENTIALS,
    icon: "mdi-source-repository",
  },
  // ----- placeholder menu entry (no-UI-gap roll-out 2026-05-24) -----
  {
    name: "AI Settings",
    fragment: UserFragments.AI_SETTINGS,
    icon: "mdi-robot-outline",
  },
];
