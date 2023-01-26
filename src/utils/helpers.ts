export function getTotalRows(
  entities: number,
  perPage: number,
  currentPage: number,
): number {
  if (entities >= perPage) {
    // there is a next page
    return (currentPage + 1) * perPage;
  }
  // we are on the last page
  return currentPage * perPage;
}

export const dateFormat: Intl.DateTimeFormatOptions = {
  year: "numeric",
  month: "2-digit",
  day: "2-digit",
  hour: "2-digit",
  minute: "2-digit",
  second: "2-digit",
};

export function convertDate(date: Date | undefined | null) {
  if (date) return new Date(date).toLocaleString("en-GB", dateFormat);
}

import {
  PermissionsPermissionTypeEnum,
  SemanticRepositoryTypeEnum,
} from "@dlr-shepard/shepard-client";

export interface PermissionOption {
  value: PermissionsPermissionTypeEnum;
  text: string;
}
export const permissionOptions: PermissionOption[] = [
  {
    value: PermissionsPermissionTypeEnum.Private,
    text: "Private",
  },
  {
    value: PermissionsPermissionTypeEnum.PublicReadable,
    text: "Public Readable",
  },
  {
    value: PermissionsPermissionTypeEnum.Public,
    text: "Public",
  },
];

export interface SemanticRepositoryOption {
  value: SemanticRepositoryTypeEnum;
  text: string;
  disabled?: boolean;
}
export const semanticRepositoryOptions: SemanticRepositoryOption[] = [
  {
    value: SemanticRepositoryTypeEnum.Sparql,
    text: "SPARQL",
  },
  {
    value: SemanticRepositoryTypeEnum.Jskos,
    text: "JSKOS",
    disabled: true,
  },
  {
    value: SemanticRepositoryTypeEnum.Skosmos,
    text: "SKOSMOS",
    disabled: true,
  },
];
export interface FilterOptions {
  perPage: number;
  orderBy: string;
  descending: boolean;
}
export interface FilterChangedData {
  currentPage: number;
  perPage: number;
  orderBy: string;
  descending: boolean;
}
