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

export function toDateTimeString(
  timestampInNanoseconds: number,
  options: Intl.DateTimeFormatOptions,
) {
  return parseDateFromNanos(timestampInNanoseconds).toLocaleTimeString(
    "en-GB",
    options,
  );
}

export function toFormattedDouble(num: string, decimal: number) {
  return parseFloat(num)
    .toFixed(decimal)
    .replace(/\.?0+$/, "");
}

// Returns date string in format: dd.mm.yyyy, hh:MM:ss:SSSS
export function toDateTimeStringWithMilliSeconds(date: Date) {
  const dd = String(date.getDate()).padStart(2, "0");
  const mm = String(date.getMonth() + 1).padStart(2, "0");
  const yyyy = date.getFullYear();
  const hh = String(date.getHours()).padStart(2, "0");
  const MM = String(date.getMinutes()).padStart(2, "0");
  const ss = String(date.getSeconds()).padStart(2, "0");
  const SSS = String(date.getMilliseconds()).padStart(3, "0");

  return `${dd}.${mm}.${yyyy}, ${hh}:${MM}:${ss}:${SSS}`;
}

export function toShortDateString(date: Date | null) {
  return date?.toLocaleDateString("en-UK", {
    year: "numeric",
    month: "short",
    day: "numeric",
  });
}

/**
 * Gets current date in the following format: YYYYMMDD
 * @returns string
 */
export function currentDateShortForm(): string {
  const date = new Date();
  const year = date.getFullYear();
  const month = (date.getMonth() + 1).toString().padStart(2, "0");
  const day = date.getDate().toString().padStart(2, "0");
  return `${year}${month}${day}`;
}

export function isNumeric(text: string): boolean {
  return !isNaN(+text) && text != "";
}

export function getQueryParam(key: string) {
  const urlSearchParams = useRequestURL().searchParams;
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

export function formatSemanticAnnotation(
  propertyName: string,
  valueName: string,
) {
  return [propertyName, valueName].join("::");
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
