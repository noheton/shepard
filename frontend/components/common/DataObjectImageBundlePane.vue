<script setup lang="ts">
/**
 * DataObjectImageBundlePane — mounts ImageBundleViewer on the DataObject detail
 * page for FileBundleReferences whose name pattern matches image extensions.
 *
 * Accepts the appId of a FileBundleReference (imageBundleAppId) detected by the
 * parent page and:
 *   1. Fetches the bundle's group list from GET /v2/bundles/{imageBundleAppId}/groups.
 *   2. When the bundle has multiple groups, renders a v-select group picker.
 *   3. When a group is selected (or auto-selected for single-group bundles),
 *      renders ImageBundleViewer.vue with the selected group's appId + bundleAppId.
 *   4. Shows loading and error states.
 *
 * Task: MFFD-IMAGEBUNDLE-PANE-MOUNT-1
 */
import ImageBundleViewer from "~/components/common/ImageBundleViewer.vue";

const props = defineProps<{
  imageBundleAppId: string;
  dataObjectAppId: string;
}>();

// ── FileGroupIO wire shape (from GET /v2/bundles/{id}/groups) ─────────────────
interface FileGroupSummary {
  appId: string;
  name: string | null;
}

const { data: session } = useAuth();

function v2BaseUrl(): string {
  const config = useRuntimeConfig().public;
  const explicit = config.backendV2ApiUrl as string | undefined;
  if (explicit && explicit.length > 0) return explicit.replace(/\/$/, "");
  return (config.backendApiUrl as string)
    .replace(/\/shepard\/api\/?$/, "")
    .replace(/\/$/, "");
}

// ── State ─────────────────────────────────────────────────────────────────────
const groups = ref<FileGroupSummary[]>([]);
const selectedGroupAppId = ref<string | null>(null);
const isLoading = ref(false);
const hasError = ref(false);

// ── Fetch groups ──────────────────────────────────────────────────────────────
async function fetchGroups(): Promise<void> {
  if (!props.imageBundleAppId) return;
  isLoading.value = true;
  hasError.value = false;
  groups.value = [];
  selectedGroupAppId.value = null;
  try {
    const url = `${v2BaseUrl()}/v2/bundles/${encodeURIComponent(props.imageBundleAppId)}/groups`;
    const accessToken = session.value?.accessToken;
    const response = await fetch(url, {
      headers: {
        Authorization: `Bearer ${accessToken}`,
        Accept: "application/json",
      },
    });
    if (!response.ok) {
      hasError.value = true;
      return;
    }
    const data = (await response.json()) as FileGroupSummary[];
    groups.value = data;
    // Auto-select the first group.
    if (data.length > 0 && data[0]) {
      selectedGroupAppId.value = data[0].appId;
    }
  } catch {
    hasError.value = true;
  } finally {
    isLoading.value = false;
  }
}

watch(
  () => props.imageBundleAppId,
  () => { void fetchGroups(); },
  { immediate: true },
);

// ── Derived state ─────────────────────────────────────────────────────────────
const hasMultipleGroups = computed(() => groups.value.length > 1);

const selectedGroup = computed<FileGroupSummary | null>(() => {
  if (!selectedGroupAppId.value) return null;
  return groups.value.find(g => g.appId === selectedGroupAppId.value) ?? null;
});

const groupPickerItems = computed(() =>
  groups.value.map(g => ({
    title: g.name ?? g.appId,
    value: g.appId,
  })),
);
</script>

<template>
  <div class="data-object-image-bundle-pane" data-testid="image-bundle-pane">
    <!-- Loading skeleton -->
    <CenteredLoadingSpinner
      v-if="isLoading"
      data-testid="image-bundle-pane-loading"
    />

    <!-- Error state -->
    <v-alert
      v-else-if="hasError"
      type="warning"
      variant="tonal"
      density="compact"
      data-testid="image-bundle-pane-error"
    >
      Could not load image bundle groups. Check your permissions or try refreshing.
    </v-alert>

    <!-- Empty groups state -->
    <v-alert
      v-else-if="!isLoading && groups.length === 0"
      type="info"
      variant="tonal"
      density="compact"
      data-testid="image-bundle-pane-empty"
    >
      No image groups found in this bundle.
    </v-alert>

    <!-- Loaded state -->
    <template v-else>
      <!-- Group picker — only shown when more than one group exists -->
      <v-select
        v-if="hasMultipleGroups"
        v-model="selectedGroupAppId"
        :items="groupPickerItems"
        label="Select image group"
        density="compact"
        variant="outlined"
        hide-details
        class="mb-3"
        style="max-width: 360px"
        data-testid="image-bundle-group-picker"
      />

      <!-- Image scrubber -->
      <ImageBundleViewer
        v-if="selectedGroupAppId && selectedGroup"
        :bundle-app-id="imageBundleAppId"
        :group-app-id="selectedGroupAppId"
        :group-name="selectedGroup.name ?? undefined"
        data-testid="image-bundle-viewer-mounted"
      />
    </template>
  </div>
</template>

<style scoped>
.data-object-image-bundle-pane {
  width: 100%;
  padding-top: 8px;
}
</style>
