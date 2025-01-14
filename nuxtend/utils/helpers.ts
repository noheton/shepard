import {
  PermissionType,
  SemanticRepositoryType,
} from "@dlr-shepard/backend-client";

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
};

export const dateTimeFormat: Intl.DateTimeFormatOptions = {
  year: "numeric",
  month: "short",
  day: "numeric",
  hour: "2-digit",
  minute: "2-digit",
};

export function parseDateFromNanos(nanos: number): Date {
  return new Date(nanos / 1000000);
}

export function toShortDateTimeString(date: Date | undefined | null) {
  if (date) return date.toLocaleString("en-GB", dateTimeFormat);
}

export function toShortDateString(date: Date | null) {
  return date?.toLocaleDateString("en-UK", {
    year: "numeric",
    month: "short",
    day: "numeric",
  });
}

export function isNumeric(text: string): boolean {
  return !isNaN(+text) && text != "";
}

export function getQueryParam(key: string) {
  const urlSearchParams = new URLSearchParams(window.location.search);
  return urlSearchParams.get(key);
}
export function setQueryParam(key: string, value: string) {
  const urlSearchParams = new URLSearchParams(window.location.search);
  urlSearchParams.set(key, value);
  const resolved = window.location.href.split("?")[0];
  history.replaceState({}, "", resolved + "?" + urlSearchParams.toString());
}
export function removeQueryParam(key: string) {
  const urlSearchParams = new URLSearchParams(window.location.search);
  urlSearchParams.delete(key);
  const resolved = window.location.href.split("?")[0];
  history.replaceState({}, "", resolved + "?" + urlSearchParams.toString());
}

export interface PermissionOption {
  value: PermissionType;
  text: string;
}
export const permissionOptions: PermissionOption[] = [
  {
    value: PermissionType.Private,
    text: "Private",
  },
  {
    value: PermissionType.PublicReadable,
    text: "Public Readable",
  },
  {
    value: PermissionType.Public,
    text: "Public",
  },
];

export interface SemanticRepositoryOption {
  value: SemanticRepositoryType;
  text: string;
  disabled?: boolean;
}
export const semanticRepositoryOptions: SemanticRepositoryOption[] = [
  {
    value: SemanticRepositoryType.Sparql,
    text: "SPARQL",
  },
  {
    value: SemanticRepositoryType.Jskos,
    text: "JSKOS",
    disabled: true,
  },
  {
    value: SemanticRepositoryType.Skosmos,
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
