<script setup lang="ts">
import FilterListLine from "@/components/generic/FilterListLine.vue";
import GenericCreateModal from "@/components/generic/GenericCreateModal.vue";
import GenericName from "@/components/generic/GenericName.vue";
import Loading from "@/components/generic/Loading.vue";
import UserGroupService from "@/services/userGroupService";
import { handleError } from "@/utils/error-handling";
import { getTotalRows, type FilterChangedData } from "@/utils/helpers";
import type {
  GetAllUserGroupsOrderByEnum,
  PermissionsPermissionTypeEnum,
  ResponseError,
  UserGroup,
} from "@dlr-shepard/shepard-client";
import { useTitle } from "@vueuse/core";
import { computed, onMounted, ref } from "vue";
import { useRouter } from "vue2-helpers/vue-router";

const router = useRouter();

const userGroupList = ref<UserGroup[]>();

const perPage = ref(10);
const currentPage = ref(1);
const orderBy = ref("createdAt");
const descending = ref(false);
const totalRows = computed(() => {
  if (userGroupList.value)
    return getTotalRows(
      userGroupList.value.length,
      perPage.value,
      currentPage.value,
    );
  else return 0;
});

function filterChanged(options: FilterChangedData) {
  currentPage.value = options.currentPage;
  perPage.value = options.currentSize;
  descending.value = options.descending;
  orderBy.value = options.orderBy;
  retrieveUserGroups();
}

function retrieveUserGroups(page?: number) {
  const nextPage = page || currentPage.value;
  const nextOrderBy =
    orderBy.value as keyof typeof GetAllUserGroupsOrderByEnum as GetAllUserGroupsOrderByEnum;
  UserGroupService.getAllUserGroups({
    size: perPage.value,
    page: nextPage - 1,
    orderBy: nextOrderBy,
    orderDesc: descending.value,
  })
    .then(response => {
      userGroupList.value = response;
    })
    .catch(e => {
      handleError(e as ResponseError, "fetching Usergroups");
    });
}

function createUserGroup(options: {
  name: string;
  perms: PermissionsPermissionTypeEnum;
}) {
  UserGroupService.createUserGroup({
    userGroup: { name: options.name, usernames: [] },
  })
    .then(async response => {
      if (response.id) {
        const perms = await UserGroupService.getUserGroupPermissions({
          usergroupId: response.id,
        });
        perms.permissionType = options.perms;
        await UserGroupService.editUserGroupPermissions({
          usergroupId: response.id,
          permissions: perms,
        });

        router.push({
          name: "UserGroup",
          params: {
            usergroupId: String(response.id),
          },
        });
      }
    })
    .catch(e => {
      handleError(e as ResponseError, "creating a usergroup");
    });
}

onMounted(() => {
  retrieveUserGroups(0);
  useTitle("User Groups | shepard");
});
</script>

<template>
  <div class="component">
    <b-button-group class="float-right">
      <b-button
        v-b-modal.create-usergroup-modal
        v-b-tooltip.hover
        title="Create UserGroup"
        variant="primary"
      >
        <CreateIcon />
      </b-button>
    </b-button-group>

    <h4 class="mb-4">User Groups</h4>

    <FilterListLine
      :max-objects="totalRows"
      :default-page="currentPage"
      :default-size="perPage"
      :default-descending="descending"
      :default-order-by="orderBy"
      @filter-changed="filterChanged($event)"
    />
    <div v-if="userGroupList == undefined">
      <Loading />
    </div>
    <div v-else>
      <b-list-group class="mb-2">
        <b-list-group-item
          v-for="(userGroup, index) in userGroupList"
          :key="index"
          :to="String(userGroup.id)"
          append
        >
          <b><GenericName :name="userGroup.name || ''" :word-count="60" /></b>
          ID: {{ userGroup.id }}
        </b-list-group-item>
      </b-list-group>
    </div>

    <b-pagination
      v-model="currentPage"
      :total-rows="totalRows"
      :per-page="perPage"
      align="center"
      size="sm"
      @change="retrieveUserGroups($event)"
    ></b-pagination>

    <GenericCreateModal
      modal-id="create-usergroup-modal"
      modal-name="Create User Group"
      @create="createUserGroup($event)"
    />
  </div>
</template>
