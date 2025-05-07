import type { MenuEntry } from "~/components/common/pane-menu/menuListTypes";

export enum UserFragments {
  PROFILE = "profile",
  API_KEYS = "api-keys",
  SUBSCRIPTIONS = "subscriptions",
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
    name: "Subscriptions",
    fragment: UserFragments.SUBSCRIPTIONS,
    icon: "mdi-bell-outline",
  },
];
