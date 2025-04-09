<script setup lang="ts">
import {
  PermissionType,
  type ContainerType,
} from "@dlr-shepard/backend-client";
import { useCreateFileContainer } from "~/composables/data/useCreateFileContainer";
import { useCreateSpatialDataContainer } from "~/composables/data/useCreateSpatialDataContainer";
import { useCreateStructuredDataContainer } from "~/composables/data/useCreateStructuredDataContainer";
import { useCreateTimeseriesContainer } from "~/composables/data/useCreateTimeseriesContainer";

const showDialog = defineModel<boolean>("showDialog", {
  required: true,
  default: false,
});
const emit = defineEmits<{
  (e: "container-created", value: number): void;
}>();

const permissionType = ref<PermissionType>(PermissionType.Private);
const containerType = ref<ContainerType>("FILE");
const containerName = ref<string>("");

const isValid = ref(true);
const form = useTemplateRef("form");

async function saveChanges() {
  if (isValid.value === false) return;

  if (containerType.value == "FILE") {
    const newFileContainer = await useCreateFileContainer(
      containerName.value,
      permissionType.value,
    );
    if (!newFileContainer) return;
    emit("container-created", newFileContainer.id);
    showDialog.value = false;
  }

  if (containerType.value == "STRUCTUREDDATA") {
    const newStructuredDataContainer = await useCreateStructuredDataContainer(
      containerName.value,
      permissionType.value,
    );
    if (!newStructuredDataContainer) return;
    emit("container-created", newStructuredDataContainer.id);
    showDialog.value = false;
  }

  if (containerType.value == "TIMESERIES") {
    const newTimeseriesContainer = await useCreateTimeseriesContainer(
      containerName.value,
      permissionType.value,
    );
    if (!newTimeseriesContainer) return;
    emit("container-created", newTimeseriesContainer.id);
    showDialog.value = false;
  }
  if (containerType.value == "SPATIALDATA") {
    const newTimeseriesContainer = await useCreateSpatialDataContainer(
      containerName.value,
      permissionType.value,
    );
    if (!newTimeseriesContainer) return;
    emit("container-created", newTimeseriesContainer.id);
    showDialog.value = false;
  }
}
</script>

<template>
  <Dialog
    v-model:show-dialog="showDialog"
    title="Create Container"
    :submit-disabled="!isValid"
    @submit="saveChanges"
  >
    <template #form>
      <v-form ref="form" v-model="isValid">
        <v-row>
          <v-col class="pt-9">
            <ContainerTypeInput v-model:container-type="containerType" />
          </v-col>
        </v-row>
        <v-row>
          <v-col>
            <v-divider />
          </v-col>
        </v-row>
        <v-row>
          <v-col>
            <NameInput v-model:name="containerName" />
          </v-col>
        </v-row>
        <v-row>
          <v-col>
            <PermissionTypeInput v-model:permission-type="permissionType" />
          </v-col>
        </v-row>
        <v-row>
          <v-col class="pt-1">
            <MandatoryFieldHint />
          </v-col>
        </v-row>
      </v-form>
    </template>
  </Dialog>
</template>
