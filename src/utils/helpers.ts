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

import { PermissionsPermissionTypeEnum } from "@dlr-shepard/shepard-client";
export const permissionOptions: {
  value: PermissionsPermissionTypeEnum;
  text: string;
}[] = [
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
