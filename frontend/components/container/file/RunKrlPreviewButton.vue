<script setup lang="ts">
/**
 * KRL-INTERPRETER-06 — "Run / preview" button on a .src FileReference page.
 *
 * Conditional render: only shown when `fileReferenceName` ends with `.src`
 * (case-insensitive). Mounting on the FileReference detail page is done
 * via the existing reference-actions slot — see
 *   frontend/pages/collections/[collectionId]/dataobjects/[dataObjectId]/
 *     filereferences/[fileReferenceId]/index.vue
 *
 * Disabled state when the caller lacks write on the parent collection
 * (mirrors the `:on-edit="fileReference.appId ? onEdit : undefined"` gate
 * pattern used on the same page).
 */
import type { FileReference } from "@dlr-shepard/backend-client";
import { isKrlSrcFile } from "./runKrlPreviewButtonHelpers";

interface Props {
  fileReference: FileReference;
  collectionId: number;
  dataObjectId: number;
  dataObjectAppId: string;
  dataObjectPath: string;
  /** Whether the caller has write access on the parent collection. */
  canEdit: boolean;
}

const props = defineProps<Props>();
const showDialog = ref(false);

const isSrc = computed(() => isKrlSrcFile(props.fileReference?.name ?? ""));
</script>

<template>
  <span v-if="isSrc" class="krl-run-button-wrap">
    <v-tooltip :disabled="canEdit" location="bottom">
      <template #activator="{ props: tooltipProps }">
        <span v-bind="tooltipProps">
          <v-btn
            color="primary"
            variant="flat"
            prepend-icon="mdi-play-box-multiple-outline"
            :disabled="!canEdit"
            data-test="krl-run-preview-button"
            @click="showDialog = true"
          >
            Run / preview
          </v-btn>
        </span>
      </template>
      You need write access on this collection to run the KRL interpreter.
    </v-tooltip>

    <RunKrlPreviewDialog
      v-if="showDialog && fileReference"
      v-model:show-dialog="showDialog"
      :src-file-reference="fileReference"
      :collection-id="collectionId"
      :data-object-id="dataObjectId"
      :data-object-app-id="dataObjectAppId"
      :data-object-path="dataObjectPath"
    />
  </span>
</template>
