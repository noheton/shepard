<template>
  <div class="component">
    <b-input-group class="component">
      <!-- Dirty fix: https://stackoverflow.com/a/57044265 -->
      <b-form-input
        placeholder="Please enter a name for the object you want to create"
        class="fixed-height"
        :value="newName"
        @change="value => (newName = value)"
        @keyup.enter="createEntity()"
      ></b-form-input>
      <b-input-group-append>
        <b-button variant="primary" @click="createEntity()"> Create </b-button>
      </b-input-group-append>
    </b-input-group>

    <b-list-group class="component">
      <b-list-group-item v-for="(entity, index) in entities" :key="index">
        <b-link :to="String(entity.id)" append>
          <b>{{ entity.name }}</b> ID: {{ entity.id }}
          <CreatedByLine
            :created-by="entity.createdBy"
            :created-at="entity.createdAt"
          />
        </b-link>
        <b-button-group>
          <b-button
            v-b-tooltip.hover
            title="Open"
            variant="light"
            :to="String(entity.id)"
            append
          >
            <OpenIcon />
          </b-button>
          <b-button
            v-b-modal.delete-confirmation-modal
            v-b-tooltip.hover
            title="Delete"
            variant="dark"
            @click="currentEntityId = entity.id"
          >
            <DeleteIcon />
          </b-button>
        </b-button-group>
      </b-list-group-item>
    </b-list-group>

    <DeleteConfirmationModal
      v-if="currentEntityId"
      modal-id="delete-confirmation-modal"
      modal-name="Confirm to delete"
      :modal-text="
        'Do you really want do delete the entity with id ' +
        currentEntityId +
        '?'
      "
      @confirmation="deleteEntity(currentEntityId)"
    />
  </div>
</template>

<script lang="ts">
import DeleteConfirmationModal from "@/components/DeleteConfirmationModal.vue";
import CreatedByLine from "@/components/generic/CreatedByLine.vue";
import Vue from "vue";

interface GenericEntityListData {
  currentEntityId?: number;
  newName: string;
}

export default Vue.extend({
  components: {
    CreatedByLine,
    DeleteConfirmationModal,
  },
  props: {
    entities: {
      type: Array,
      default: function () {
        return [];
      },
    },
  },
  data() {
    return {
      currentEntityId: undefined,
      newName: "",
    } as GenericEntityListData;
  },
  methods: {
    createEntity() {
      this.$emit("createEntity", this.newName);
      this.newName = "";
    },
    deleteEntity(id: number) {
      this.$emit("deleteEntity", id);
      this.currentEntityId = undefined;
    },
  },
});
</script>

<style scoped>
.fixed-height {
  height: 40px;
}
.list-group-item a {
  color: #495057;
  float: left;
}
.list-group-item .btn-group {
  float: right;
}
</style>
