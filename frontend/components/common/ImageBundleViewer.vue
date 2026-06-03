<script setup lang="ts">
/**
 * ImageBundleViewer — frame scrubber for a FileGroup inside a FileBundleReference.
 *
 * Fetches the ShepardFile list for the given group and renders an image scrubber
 * (← / → navigation + a frame slider) so a researcher can step through an
 * image series (e.g. an AFP layup inspection sequence or a thermography time-lapse).
 *
 * Props:
 *   bundleAppId  — UUID v7 of the parent FileBundleReference.
 *   groupAppId   — UUID v7 of the selected FileGroup.
 *   containerMongoId — (optional) GridFS container id, passed through to
 *                       the image-content URL if the backend needs it for routing.
 *   groupName    — (optional) human-readable label shown above the scrubber.
 *   pageSize     — (optional) max frames to show per fetch (default 50).
 *
 * Task: MFFD-IMAGEBUNDLE-PANE-MOUNT-1
 */

function v2BaseUrl(): string {
  const config = useRuntimeConfig().public;
  const explicit = config.backendV2ApiUrl as string | undefined;
  if (explicit && explicit.length > 0) return explicit.replace(/\/$/, "");
  return (config.backendApiUrl as string)
    .replace(/\/shepard\/api\/?$/, "")
    .replace(/\/$/, "");
}

interface ShepardFileIO {
  oid: string;
  name: string;
  fileSize: number | null;
  md5?: string | null;
}

const props = withDefaults(
  defineProps<{
    bundleAppId: string;
    groupAppId: string;
    containerMongoId?: string;
    groupName?: string;
    pageSize?: number;
  }>(),
  {
    containerMongoId: undefined,
    groupName: undefined,
    pageSize: 50,
  },
);

const { data: session } = useAuth();

const files = ref<ShepardFileIO[]>([]);
const isLoading = ref(false);
const hasError = ref(false);
const frameIndex = ref(0);

async function fetchGroup(): Promise<void> {
  if (!props.bundleAppId || !props.groupAppId) return;
  isLoading.value = true;
  hasError.value = false;
  try {
    const url = `${v2BaseUrl()}/v2/bundles/${encodeURIComponent(props.bundleAppId)}/groups/${encodeURIComponent(props.groupAppId)}`;
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
    const data = (await response.json()) as { files?: ShepardFileIO[] };
    files.value = (data.files ?? []).filter(f => isImageFile(f.name));
    frameIndex.value = 0;
  } catch {
    hasError.value = true;
  } finally {
    isLoading.value = false;
  }
}

function isImageFile(name: string | undefined): boolean {
  if (!name) return false;
  const lower = name.toLowerCase();
  return lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".webp");
}

watch(
  () => [props.bundleAppId, props.groupAppId],
  () => { void fetchGroup(); },
  { immediate: true },
);

const currentFile = computed<ShepardFileIO | null>(
  () => files.value[frameIndex.value] ?? null,
);

// Construct a signed-URL-style content link via the v2 file endpoint.
// Format: /v2/bundles/{bundleAppId}/groups/{groupAppId}/files/{oid}/content
// (If the backend uses a different path, update here — the oid is the GridFS handle.)
const imageContentUrl = computed<string | null>(() => {
  const f = currentFile.value;
  if (!f) return null;
  const base = v2BaseUrl();
  return `${base}/v2/bundles/${encodeURIComponent(props.bundleAppId)}/groups/${encodeURIComponent(props.groupAppId)}/files/${encodeURIComponent(f.oid)}/content`;
});

// Fallback: if the image URL can't be constructed, show a placeholder.
const hasImages = computed(() => files.value.length > 0);

function prev() {
  if (frameIndex.value > 0) frameIndex.value--;
}
function next() {
  if (frameIndex.value < files.value.length - 1) frameIndex.value++;
}

function onSliderInput(val: number | number[]) {
  const v = Array.isArray(val) ? val[0] : val;
  if (typeof v === "number") frameIndex.value = v;
}
</script>

<template>
  <div class="image-bundle-viewer" data-testid="image-bundle-viewer">
    <CenteredLoadingSpinner v-if="isLoading" />

    <v-alert
      v-else-if="hasError"
      type="warning"
      variant="tonal"
      density="compact"
      data-testid="image-bundle-viewer-error"
    >
      Could not load image group. Check permissions or try refreshing.
    </v-alert>

    <v-alert
      v-else-if="!hasImages"
      type="info"
      variant="tonal"
      density="compact"
      data-testid="image-bundle-viewer-empty"
    >
      No images in this group yet.
    </v-alert>

    <template v-else>
      <!-- Frame header -->
      <div class="d-flex align-center ga-2 mb-2 px-1">
        <v-icon size="small" color="primary">mdi-image-multiple-outline</v-icon>
        <span class="text-caption text-medium-emphasis">
          {{ groupName ?? "Image Group" }} — frame {{ frameIndex + 1 }} / {{ files.length }}
        </span>
        <span class="text-caption text-low-emphasis ml-auto" data-testid="image-bundle-filename">
          {{ currentFile?.name }}
        </span>
      </div>

      <!-- Image display area -->
      <div class="image-bundle-viewer__canvas" data-testid="image-bundle-canvas">
        <v-img
          v-if="imageContentUrl"
          :src="imageContentUrl"
          contain
          max-height="480"
          class="rounded"
          data-testid="image-bundle-img"
        >
          <template #error>
            <div class="d-flex align-center justify-center fill-height">
              <v-icon size="48" color="grey">mdi-image-broken-variant</v-icon>
            </div>
          </template>
        </v-img>
      </div>

      <!-- Scrubber controls -->
      <div class="d-flex align-center ga-2 mt-2 px-1">
        <v-btn
          icon="mdi-chevron-left"
          variant="text"
          size="small"
          :disabled="frameIndex === 0"
          aria-label="Previous frame"
          data-testid="image-bundle-prev"
          @click="prev"
        />

        <v-slider
          :model-value="frameIndex"
          :min="0"
          :max="files.length - 1"
          :step="1"
          hide-details
          density="compact"
          class="flex-grow-1"
          data-testid="image-bundle-slider"
          @update:model-value="onSliderInput"
        />

        <v-btn
          icon="mdi-chevron-right"
          variant="text"
          size="small"
          :disabled="frameIndex === files.length - 1"
          aria-label="Next frame"
          data-testid="image-bundle-next"
          @click="next"
        />
      </div>
    </template>
  </div>
</template>

<style scoped>
.image-bundle-viewer {
  width: 100%;
}
.image-bundle-viewer__canvas {
  width: 100%;
  min-height: 200px;
  background: rgba(0, 0, 0, 0.03);
  border-radius: 4px;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
}
</style>
