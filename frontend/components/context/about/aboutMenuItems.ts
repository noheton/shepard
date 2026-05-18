import type { MenuEntry } from "~/components/common/pane-menu/menuListTypes";

export enum AboutFragments {
  VERSION = "version",
  ORGANIZATION = "organization",
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
    name: "Organization",
    fragment: AboutFragments.ORGANIZATION,
    icon: "mdi-domain",
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
