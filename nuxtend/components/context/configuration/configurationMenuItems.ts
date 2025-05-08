import type { MenuEntry } from "~/components/common/pane-menu/menuListTypes";

export enum ConfigurationFragments {
  SEMANTIC_REPOSITORIES = "semanticrepositories",
  USER_GROUPS = "userGroups",
}

export const ConfigurationMenuEntries: MenuEntry[] = [
  {
    name: "Semantic Repository",
    fragment: ConfigurationFragments.SEMANTIC_REPOSITORIES,
    icon: "mdi-library-outline",
  },
  {
    name: "User Groups",
    fragment: ConfigurationFragments.USER_GROUPS,
    icon: "mdi-account-multiple-outline",
  },
];
