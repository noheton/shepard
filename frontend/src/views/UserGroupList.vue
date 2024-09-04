<script setup lang="ts">
import FilterListLine from "@/components/generic/FilterListLine.vue";
import GenericCreateModal from "@/components/generic/GenericCreateModal.vue";
import GenericName from "@/components/generic/GenericName.vue";
import Loading from "@/components/generic/Loading.vue";
import type {
  PermissionType,
  ResponseError,
  UserGroup,
  UserGroupAttributes,
} from "@/generated/openapi";
import UserGroupService from "@/services/userGroupService";
import { handleError } from "@/utils/error-handling";
import {
  getTotalRows,
  type FilterChangedData,
  type FilterOptions,
} from "@/utils/helpers";
import { useStorage, useTitle } from "@vueuse/core";
import { computed, onMounted, ref } from "vue";
import { useRouter } from "vue2-helpers/vue-router";

const router = useRouter();

const userGroupList = ref<UserGroup[]>();

const filterOptions = useStorage<FilterOptions>("usergroup-filter-options", {
  perPage: 10,
  orderBy: "createdAt",
  descending: false,
});
const currentPage = ref(1);
const totalRows = computed(() => {
  if (userGroupList.value)
    return getTotalRows(
      userGroupList.value.length,
      filterOptions.value.perPage,
      currentPage.value,
    );
  else return 0;
});
function filterChanged(options: FilterChangedData) {
  currentPage.value = options.currentPage;
  filterOptions.value.perPage = options.perPage;
  filterOptions.value.descending = options.descending;
  filterOptions.value.orderBy = options.orderBy;
  retrieveUserGroups();
}

function retrieveUserGroups(page?: number) {
  const nextPage = page || currentPage.value;
  const nextOrderBy = filterOptions.value
    .orderBy as keyof typeof UserGroupAttributes as UserGroupAttributes;
  UserGroupService.getAllUserGroups({
    size: filterOptions.value.perPage,
    page: nextPage - 1,
    orderBy: nextOrderBy,
    orderDesc: filterOptions.value.descending,
  })
    .then(response => {
      userGroupList.value = response;
    })
    .catch(e => {
      handleError(e as ResponseError, "fetching Usergroups");
    });
}

function createUserGroup(options: { name: string; perms: PermissionType }) {
  UserGroupService.createUserGroup({
    userGroup: { name: options.name, usernames: [] },
  })
    .then(async response => {
      if (response.id) {
        const perms = await UserGroupService.getUserGroupPermissions({
          userGroupId: response.id,
        });
        perms.permissionType = options.perms;
        await UserGroupService.editUserGroupPermissions({
          userGroupId: response.id,
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
  <div class="view">
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

    <h4 class="title">User Groups</h4>

    <FilterListLine
      :max-objects="totalRows"
      :current-page="currentPage"
      :filter-options="filterOptions"
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
      :per-page="filterOptions.perPage"
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
