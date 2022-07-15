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

    <b-alert
      :show="deletedAlert"
      dismissible
      variant="dark"
      @dismissed="deletedAlert = false"
    >
      Successfully deleted
    </b-alert>

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
          <b><GenericName :name="userGroup.name" :word-count="60" /></b>
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

<script lang="ts">
import FilterListLine, {
  type FilterChangedData,
} from "@/components/generic/FilterListLine.vue";
import GenericCreateModal from "@/components/generic/GenericCreateModal.vue";
import GenericName from "@/components/generic/GenericName.vue";
import Loading from "@/components/generic/Loading.vue";
import UserGroupService from "@/services/userGroupService";
import { emitter } from "@/utils/event-bus";
import { totalRows } from "@/utils/helpers";
import type {
  GetAllUserGroupsOrderByEnum,
  UserGroup,
} from "@dlr-shepard/shepard-client";
import { defineComponent } from "vue";

interface UserGroupData {
  userGroupList?: UserGroup[];
  deletedAlert: boolean;
  perPage: number;
  currentPage: number;
  orderBy: string;
  descending: boolean;
}

export default defineComponent({
  components: { GenericCreateModal, GenericName, Loading, FilterListLine },
  data() {
    return {
      userGroupList: undefined,
      deletedAlert: false,
      perPage: 10,
      currentPage: 1,
      orderBy: "createdAt",
      descending: false,
    } as UserGroupData;
  },
  computed: {
    totalRows(): number {
      if (this.userGroupList)
        return totalRows(
          this.userGroupList.length,
          this.perPage,
          this.currentPage,
        );
      else return 0;
    },
  },
  mounted() {
    this.retrieveUserGroups(0);
  },
  methods: {
    filterChanged(options: FilterChangedData) {
      this.currentPage = options.currentPage;
      this.perPage = options.currentSize;
      this.descending = options.descending;
      this.orderBy = options.orderBy;
      this.retrieveUserGroups();
    },
    retrieveUserGroups(page?: number) {
      const nextPage = page || this.currentPage;
      const nextOrderBy = this
        .orderBy as keyof typeof GetAllUserGroupsOrderByEnum as GetAllUserGroupsOrderByEnum;
      UserGroupService.getAllUserGroups({
        size: this.perPage,
        page: nextPage - 1,
        orderBy: nextOrderBy,
        orderDesc: this.descending,
      })
        .then(response => {
          this.userGroupList = response;
        })
        .catch(e => {
          const error = "Error while fetching Usergroups: " + e.statusText;
          console.log(error);
          emitter.emit("error", error);
        });
    },
    createUserGroup(newName: string) {
      UserGroupService.createUserGroup({
        userGroup: { name: newName, usernames: [] } as UserGroup,
      })
        .then(response => {
          this.$router.push({
            name: "UserGroup",
            params: {
              usergroupId: String(response.id),
            },
          });
        })
        .catch(e => {
          const error = "Error while creating a usergroup: " + e.statusText;
          console.log(error);
          emitter.emit("error", error);
        });
    },
  },
});
</script>
