<script lang="ts" setup>
import { useFetchFileThumbnail } from "~/composables/container/useFetchFileThumbnail";

const props = defineProps<{
  containerAppId: string;
  oid: string;
}>();

const { blobUrl, isLoading, isUnsupported, load, revoke } =
  useFetchFileThumbnail(props.containerAppId, props.oid, 64);

onMounted(() => load());
onUnmounted(() => revoke());
</script>

<template>
  <div class="d-flex align-center justify-center" style="width: 48px; height: 48px">
    <v-skeleton-loader v-if="isLoading" type="image" width="48" height="48" />
    <v-img
      v-else-if="blobUrl"
      :src="blobUrl"
      width="48"
      height="48"
      cover
      class="rounded"
    />
    <v-icon v-else color="grey-lighten-1" size="28">mdi-file-outline</v-icon>
  </div>
</template>
