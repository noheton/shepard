import type { MenuEntry } from "~/components/common/pane-menu/menuListTypes";

export enum ConfigurationFragments {
  SEMANTIC_REPOSITORIES = "semanticrepositories",
}

export const ConfigurationMenuEntries: MenuEntry[] = [
  {
    name: "Semantic Repository",
    fragment: ConfigurationFragments.SEMANTIC_REPOSITORIES,
    icon: "mdi-library-outline",
  },
];
