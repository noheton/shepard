<script lang="ts" setup>
import {
  useFetchFileThumbnail,
  isImageFilename,
} from "~/composables/container/useFetchFileThumbnail";

const props = defineProps<{
  containerAppId: string;
  oid: string;
  // UI-009: filename is optional so existing callers don't break, but when
  // present we use the extension to skip the thumbnail fetch for non-image
  // files. Eliminates the per-row 404 noise on .txt / .rdk / .pdf rows.
  filename?: string;
}>();

const { blobUrl, isLoading, load, revoke } = useFetchFileThumbnail(
  props.containerAppId,
  props.oid,
  64,
);

// Only fetch a thumbnail for image-typed files. Anything else falls through
// to the generic file-outline icon without ever touching the network.
const canHaveThumbnail = computed(() => isImageFilename(props.filename));

onMounted(() => {
  if (canHaveThumbnail.value) {
    void load();
  }
});
onUnmounted(() => revoke());
</script>

<template>
  <div class="d-flex align-center justify-center" style="width: 48px; height: 48px">
    <template v-if="canHaveThumbnail">
      <v-skeleton-loader v-if="isLoading" type="image" width="48" height="48" />
      <v-img
        v-else-if="blobUrl"
        :src="blobUrl"
        width="48"
        height="48"
        cover
        class="rounded"
      />
      <v-icon v-else color="grey-lighten-1" size="28">mdi-image-outline</v-icon>
    </template>
    <v-icon v-else color="grey-lighten-1" size="28">mdi-file-outline</v-icon>
  </div>
</template>
