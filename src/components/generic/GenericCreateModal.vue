<script setup lang="ts">
import { permissionOptions as pOptions } from "@/utils/helpers";
import { PermissionsPermissionTypeEnum } from "@dlr-shepard/shepard-client";
import { ref, type Ref } from "vue";

const props = defineProps({
  modalId: {
    type: String,
    default: "GenericCreateModal",
  },
  modalName: {
    type: String,
    default: "GenericCreateModal",
  },
});
const permissionOptions = pOptions;

const emit = defineEmits(["create"]);
const newObject: Ref<{ name: string; perms: PermissionsPermissionTypeEnum }> =
  ref({ name: "", perms: PermissionsPermissionTypeEnum.Private });

function handlePrepare() {
  newObject.value = { name: "", perms: PermissionsPermissionTypeEnum.Private };
}

function handleOK() {
  emit("create", newObject.value);
}
</script>

<template>
  <b-modal
    :id="props.modalId"
    ref="modal"
    size="lg"
    :title="props.modalName"
    lazy
    @show="handlePrepare()"
    @ok="handleOK()"
  >
    <b-form-group>
      <b-container>
        <b-row class="mb-3">
          <b-col cols="3"> Name </b-col>
          <b-col cols="9">
            <b-form-input
              v-model="newObject.name"
              placeholder="Name"
              required
            ></b-form-input>
          </b-col>
        </b-row>

        <b-row class="mb-3">
          <b-col cols="3"> Permission </b-col>
          <b-col cols="9">
            <b-form-select
              v-model="newObject.perms"
              class="mb-3"
              :options="permissionOptions"
            ></b-form-select>
          </b-col>
        </b-row>
      </b-container>
    </b-form-group>
  </b-modal>
</template>
