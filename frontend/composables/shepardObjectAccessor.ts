import {
  type Permissions,
  type ResponseError,
  type Roles,
  type User,
  UserApi,
} from "@dlr-shepard/backend-client";
import type { Ref } from "vue";
import { useShepardApi } from "./common/api/useShepardApi";

export abstract class ShepardObjectAccessor<TId extends number | string = number> {
  id: TId;
  roles: Ref<Roles | undefined>;
  permissions: Ref<Permissions | undefined>;
  owner: Ref<User | undefined>;

  isAllowedToEditData: ComputedRef<boolean> = computed(() => {
    return !!this.roles.value?.owner || !!this.roles.value?.writer;
  });

  isAllowedToEditPermissions: ComputedRef<boolean> = computed(() => {
    return !!this.roles.value?.owner || !!this.roles.value?.manager;
  });

  constructor(id: TId) {
    this.id = id;
    this.roles = ref<Roles>();
    this.permissions = ref();
    this.owner = ref();
  }

  abstract fetchData(): Promise<void>;

  abstract delete(): Promise<void>;

  abstract fetchRoles(): Promise<void>;

  abstract fetchPermissions(): Promise<void>;

  abstract updatePermissions(updatedPermissions: Permissions): Promise<void>;

  async fetchOwner() {
    try {
      if (!this.permissions) {
        await this.fetchPermissions();
      }
      const ownerName = this.permissions.value!.owner!;
      const ownerValue = await useShepardApi(UserApi).value.getUser({
        username: ownerName,
      });
      this.owner = ref(ownerValue);
    } catch (error) {
      handleError(error as ResponseError, "fetching owner");
      throw error;
    }
  }
}

// V2-SWEEP-003-2: changed TId to string — container routes now carry UUID-v7 appId
// or numeric string (V1-EXCEPTION). Accessors detect via /^\d+$/ and branch.
export abstract class ContainerAccessor extends ShepardObjectAccessor<string> {
  isAllowedToDelete: ComputedRef<boolean> = computed(() => {
    return !!this.roles.value?.owner;
  });
}
