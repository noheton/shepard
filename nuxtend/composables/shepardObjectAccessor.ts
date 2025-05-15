import {
  type Permissions,
  type ResponseError,
  type Roles,
  type User,
  UserApi,
} from "@dlr-shepard/backend-client";
import type { Ref } from "vue";
import { useShepardApi } from "./common/api/useShepardApi";

export abstract class ShepardObjectAccessor {
  id: number;
  roles: Ref<Roles | undefined>;
  permissions: Ref<Permissions | undefined>;
  owner: Ref<User | undefined>;

  isAllowedToEditData: ComputedRef<boolean> = computed(() => {
    return !!this.roles.value?.owner || !!this.roles.value?.writer;
  });

  isAllowedToEditPermissions: ComputedRef<boolean> = computed(() => {
    return !!this.roles.value?.owner || !!this.roles.value?.manager;
  });

  constructor(id: number) {
    this.id = id;
    this.roles = ref<Roles>();
    this.permissions = ref();
    this.owner = ref();
  }

  abstract fetchData(): Promise<void>;

  abstract delete(): Promise<void>;

  abstract fetchRoles(): Promise<void>;

  // add getApi method

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
      handleError(error as ResponseError, "fetching user");
      throw error;
    }
  }
}

export abstract class ContainerAccessor extends ShepardObjectAccessor {
  isAllowedToDelete: ComputedRef<boolean> = computed(() => {
    return !!this.roles.value?.owner;
  });
}
