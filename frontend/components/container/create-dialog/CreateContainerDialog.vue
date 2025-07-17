<script lang="ts" setup>
import {
  PermissionType,
  type ContainerType,
  type FileContainer,
  type TimeseriesContainer,
  type StructuredDataContainer,
  type SpatialDataContainer,
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
  (e: "container-created", value: number, type: ContainerType): void;
}>();

const permissionType = ref<PermissionType>(PermissionType.Private);
const containerType = ref<ContainerType>("FILE");
const containerName = ref<string>("");

const isValid = ref(true);
useTemplateRef("form");

async function saveChanges() {
  if (isValid.value === false) return;

  let newContainer:
    | FileContainer
    | TimeseriesContainer
    | StructuredDataContainer
    | SpatialDataContainer
    | undefined = undefined;

  if (containerType.value == "FILE") {
    newContainer = await useCreateFileContainer(
      containerName.value,
      permissionType.value,
    );
  }
  if (containerType.value == "STRUCTUREDDATA") {
    newContainer = await useCreateStructuredDataContainer(
      containerName.value,
      permissionType.value,
    );
  }
  if (containerType.value == "TIMESERIES") {
    newContainer = await useCreateTimeseriesContainer(
      containerName.value,
      permissionType.value,
    );
  }
  if (containerType.value == "SPATIALDATA") {
    newContainer = await useCreateSpatialDataContainer(
      containerName.value,
      permissionType.value,
    );
  }

  if (!newContainer) return;
  emit("container-created", newContainer.id, newContainer.type);
  showDialog.value = false;
}
</script>

<template>
  <FormDialog
    v-model:show-dialog="showDialog"
    :submit-disabled="!isValid"
    title="Create Container"
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
  </FormDialog>
</template>
