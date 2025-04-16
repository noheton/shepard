<script setup lang="ts">
import MetadataColumn from "./MetadataColumn.vue";
import MetadataContainerField from "./MetadataContainerField.vue";
import MetadataField from "./MetadataTextField.vue";

interface TitleAndMetadataDisplayProps {
  idLabel: string;
  entity: {
    id: number;
    name: string;
    createdAt: Date;
    createdBy: string;
    updatedAt: Date | null;
    updatedBy: string | null;
    type?: string;
    container?: {
      title: string;
      id: number;
      path: string;
      availability?: "available" | "deleted" | "forbidden" | "error";
    };
  };
  onDelete?: () => void;
  onDownload?: (name: string) => void;
  onAnnotate?: () => void;
}
defineProps<TitleAndMetadataDisplayProps>();
</script>

<template>
  <v-container fluid class="pt-0 pl-0 pr-0">
    <v-row no-gutters>
      <v-col cols="12" class="ml-n1 pb-6">
        <h1 class="text-h1">{{ entity.name }}</h1>
      </v-col>
    </v-row>
    <v-row no-gutters class="justify-start flex-nowrap">
      <MetadataColumn>
        <MetadataField v-if="entity.type" label="Type" :text="entity.type" />
        <MetadataField label="ID" :text="entity.id.toString() ?? ''" />
      </MetadataColumn>

      <MetadataColumn>
        <MetadataCreatedField
          :created-at="entity.createdAt"
          :created-by="entity.createdBy"
        />
      </MetadataColumn>

      <MetadataColumn v-if="entity.updatedAt && entity.updatedBy">
        <MetadataUpdatedField
          :updated-at="entity.updatedAt"
          :updated-by="entity.updatedBy"
        />
      </MetadataColumn>

      <MetadataColumn v-if="entity.container">
        <MetadataContainerField
          :container-name="entity.container.title"
          :container-id="entity.container.id"
          :availability="entity.container.availability ?? 'available'"
          :container-path="entity.container.path"
        />
      </MetadataColumn>

      <v-col class="flex-shrink-1 d-flex justify-end">
        <v-btn
          v-if="onDelete"
          rounded="lg"
          class="mx-2"
          variant="flat"
          color="treeview"
          icon="mdi-delete"
          @click="onDelete"
        />
        <v-btn
          v-if="onDownload"
          rounded="lg"
          class="mx-2"
          variant="flat"
          color="treeview"
          icon="mid-download"
          @click="onDownload(entity.name)"
        />
        <v-btn
          v-if="onAnnotate"
          rounded="lg"
          class="mx-2"
          variant="flat"
          color="treeview"
          icon="mdi-tag"
          @click="onAnnotate"
        />
      </v-col>
    </v-row>
  </v-container>
</template>
