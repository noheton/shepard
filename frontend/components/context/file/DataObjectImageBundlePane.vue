<script setup lang="ts">
/**
 * MFFD-IMAGEBUNDLE-PANE-MOUNT-1 — mounts the ImageBundleViewer scrubber on
 * the DataObject detail page for FileBundleReferences whose name matches an
 * image-bundle heuristic (`.png`, `.jpg`, `.tif` extensions or
 * "image"/"img"/"frame"/"scan" in the name).
 *
 * Props:
 *   dataObjectAppId   — appId of the parent DataObject (informational, passed
 *                        through for future per-DO analytics).
 *   imageBundleAppIds — one or more FileBundleReference appIds whose names
 *                        matched the image heuristic. The pane auto-selects the
 *                        first; when multiple are passed a `v-select` picker
 *                        lets the user switch between them.
 *   containerMongoId  — optional mongoId of the FileContainer backing the
 *                        selected bundle; forwarded to ImageBundleViewer for
 *                        thumbnail/payload URL construction.
 *
 * Groups are discovered lazily via useImageBundleGroups. When the first group
 * resolves the scrubber mounts immediately; a second `v-select` surfaces when
 * a bundle has multiple groups (edge case, but supported).
 */
import { useImageBundleGroups, type BundleGroupIO } from "~/composables/context/useImageBundleGroups";
import ImageBundleViewer from "~/components/common/ImageBundleViewer.vue";

const props = defineProps<{
  /** appId of the parent DataObject (informational). */
  dataObjectAppId: string;
  /** One or more FileBundleReference appIds that match the image heuristic. */
  imageBundleAppIds: string[];
  /** Optional mongoId of the backing FileContainer (for thumbnail URLs). */
  containerMongoId?: string | null;
}>();

// ── bundle picker ─────────────────────────────────────────────────────────────

const selectedBundleAppId = ref<string>(props.imageBundleAppIds[0] ?? "");

// Keep selection in sync if the parent passes a new list (reactive prop change).
watch(
  () => props.imageBundleAppIds,
  (ids) => {
    const first = ids[0];
    if (first !== undefined && !ids.includes(selectedBundleAppId.value)) {
      selectedBundleAppId.value = first;
    }
  },
);

// Bundle picker items — one item per bundle appId.
const bundleItems = computed(() =>
  props.imageBundleAppIds.map((id, i) => ({
    title: `Bundle ${i + 1} (${id.slice(0, 8)}…)`,
    value: id,
  })),
);

// ── groups for the selected bundle ────────────────────────────────────────────

const { groups, loading, error } = useImageBundleGroups(selectedBundleAppId);

const selectedGroupAppId = ref<string | null>(null);

// Auto-select the first group whenever the group list reloads.
watch(groups, (newGroups: BundleGroupIO[]) => {
  const first = newGroups[0];
  selectedGroupAppId.value = first !== undefined ? first.appId : null;
}, { immediate: true });

const groupItems = computed(() =>
  groups.value.map((g: BundleGroupIO, i: number) => ({
    title: g.name || `Group ${i + 1}`,
    value: g.appId,
  })),
);

// The currently selected group's full object (for the name label).
const selectedGroup = computed<BundleGroupIO | null>(
  () => groups.value.find((g: BundleGroupIO) => g.appId === selectedGroupAppId.value) ?? null,
);
</script>

<template>
  <div class="d-flex flex-column ga-3 pa-4" data-testid="image-bundle-pane">
    <!-- Bundle picker — only shown when the DataObject has >1 image bundle -->
    <v-select
      v-if="imageBundleAppIds.length > 1"
      v-model="selectedBundleAppId"
      :items="bundleItems"
      label="Image bundle"
      density="compact"
      variant="outlined"
      hide-details
      data-testid="image-bundle-picker"
    />

    <!-- Group picker — edge case where a single bundle has multiple groups -->
    <v-select
      v-if="!loading && groups.length > 1"
      v-model="selectedGroupAppId"
      :items="groupItems"
      label="Group"
      density="compact"
      variant="outlined"
      hide-details
      data-testid="image-bundle-group-picker"
    />

    <!-- Loading skeleton while groups fetch -->
    <v-skeleton-loader
      v-if="loading"
      type="image"
      height="220"
      data-testid="image-bundle-skeleton"
    />

    <!-- Error state -->
    <v-alert
      v-else-if="error"
      type="error"
      variant="tonal"
      density="compact"
      data-testid="image-bundle-error"
    >
      {{ error }}
    </v-alert>

    <!-- No groups found -->
    <v-alert
      v-else-if="!loading && groups.length === 0"
      type="info"
      variant="tonal"
      density="compact"
      data-testid="image-bundle-empty"
    >
      No groups found in this bundle.
    </v-alert>

    <!-- The scrubber — mounts once bundle + group are resolved -->
    <ImageBundleViewer
      v-else-if="selectedBundleAppId && selectedGroupAppId"
      :bundle-app-id="selectedBundleAppId"
      :group-app-id="selectedGroupAppId"
      :container-mongo-id="containerMongoId ?? null"
      :group-name="selectedGroup?.name ?? null"
    />
  </div>
</template>
