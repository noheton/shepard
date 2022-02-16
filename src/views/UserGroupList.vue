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

    <GenericCreateModal
      modal-id="create-usergroup-modal"
      modal-name="Create User Group"
      @create="createUserGroup($event)"
    />
  </div>
</template>

<script lang="ts">
import GenericCreateModal from "@/components/generic/GenericCreateModal.vue";
import GenericName from "@/components/generic/GenericName.vue";
import Loading from "@/components/generic/Loading.vue";
import { UserGroupVue } from "@/utils/api-mixin";
import { emitter } from "@/utils/event-bus";
import { UserGroup } from "@dlr-shepard/shepard-client";
import Vue, { VueConstructor } from "vue";

interface UserGroupData {
  userGroupList?: UserGroup[];
  deletedAlert: boolean;
}

export default (
  Vue as VueConstructor<Vue & InstanceType<typeof UserGroupVue>>
).extend({
  components: { GenericCreateModal, GenericName, Loading },
  mixins: [UserGroupVue],
  data() {
    return {
      userGroupList: undefined,
      deletedAlert: false,
    } as UserGroupData;
  },
  mounted() {
    this.retrieveUserGroups();
  },
  methods: {
    retrieveUserGroups() {
      this.userGroupApi
        ?.getAllUserGroups()
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
      this.userGroupApi
        ?.createUserGroup({
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
