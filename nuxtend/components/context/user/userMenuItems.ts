import type { MenuEntry } from "~/components/common/pane-menu/menuListTypes";

export enum UserFragments {
  PROFILE = "profile",
}

export const UserMenuEntries: MenuEntry[] = [
  {
    name: "Profile",
    fragment: UserFragments.PROFILE,
    icon: "mdi-account-outline",
  },
];
