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

// https://github.com/vuejs/vue-router/issues/3760#issuecomment-1178468293
import { getCurrentInstance } from "vue";
export function useRoute() {
  const instance = getCurrentInstance();
  if (instance) return instance.proxy.$route;
  else return undefined as never;
}
export function useRouter() {
  const instance = getCurrentInstance();
  if (instance) return instance.proxy.$router;
  else return undefined as never;
}

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
