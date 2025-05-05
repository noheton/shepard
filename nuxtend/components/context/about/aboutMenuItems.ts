import type { MenuEntry } from "../../common/navMenu/menuListTypes";

export enum AboutFragments {
  VERSION = "version",
  HEALTH = "health",
  DOCUMENTATION = "documentation",
}

export const AboutMenuEntries: MenuEntry[] = [
  {
    name: "Version",
    fragment: AboutFragments.VERSION,
    icon: "mdi-information-outline",
  },
  {
    name: "System Health",
    fragment: AboutFragments.HEALTH,
    icon: "mdi-check-circle-outline",
  },
  {
    name: "Documentation",
    fragment: AboutFragments.DOCUMENTATION,
    icon: "mdi-file-document-outline",
  },
];
