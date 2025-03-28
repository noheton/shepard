<script setup lang="ts">
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
    container?: { title: string; id: number; to: string };
  };
  onDelete?: () => void;
  onDownload?: (name: string) => void;
  onAnnotate?: () => void;
}
const props = defineProps<TitleAndMetadataDisplayProps>();
const createdAt = toShortDateString(props.entity.createdAt);
const updatedAt = props.entity.updatedAt
  ? toShortDateString(props.entity.updatedAt)
  : undefined;
</script>

<template>
  <v-container fluid class="pt-0 pl-0 pr-0">
    <v-row no-gutters>
      <v-col cols="12" class="ml-n1 pb-6">
        <h1 class="text-h1">{{ entity.name }}</h1>
      </v-col>
    </v-row>
    <v-row class="text-body-2 text-medium-emphasis">
      <v-col>
        <div v-if="entity.type">
          <strong>Type:</strong>
          {{ entity.type }}
        </div>
        <div>
          <strong>ID:</strong>
          {{ entity.id }}
        </div>
      </v-col>
      <v-col>
        <div>
          <strong>Created:</strong>
          {{ createdAt }} by
          {{ entity.createdBy }}
        </div>
      </v-col>
      <v-col v-if="entity.updatedBy">
        <div>
          <strong>Updated:</strong>
          {{ updatedAt }} by
          {{ entity.updatedBy }}
        </div>
      </v-col>
      <v-col v-if="entity.container">
        <div>
          <strong>Container:</strong>
          <span>
            <a :href="entity.container.to" target="_blank">
              {{ entity.container.title }}
            </a>
            (ID: {{ entity.container.id }})
          </span>
        </div>
      </v-col>
      <v-col class="text-right">
        <v-btn
          v-if="onDelete"
          rounded="lg"
          class="mx-2"
          variant="flat"
          color="treeview"
          icon
          @click="onDelete"
        >
          <v-icon>mdi-delete</v-icon>
        </v-btn>
        <v-btn
          v-if="onDownload"
          rounded="lg"
          class="mx-2"
          variant="flat"
          color="treeview"
          icon
          @click="onDownload(entity.name)"
        >
          <v-icon>mdi-download</v-icon>
        </v-btn>
        <v-btn
          v-if="onAnnotate"
          rounded="lg"
          class="mx-2"
          variant="flat"
          color="treeview"
          icon
          @click="onAnnotate"
        >
          <v-icon>mdi-tag</v-icon>
        </v-btn>
      </v-col>
    </v-row>
  </v-container>
</template>
