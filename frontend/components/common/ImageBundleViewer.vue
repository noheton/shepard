<script lang="ts" setup>
/**
 * MFFD-IMAGEBUNDLE-SCRUBBER-1 — generic frame-scrubber for a single
 * FileGroup inside a FileBundleReference.
 *
 * Layout:
 *   • Large preview of the currently-selected frame at the top.
 *   • A range slider underneath (frame N of TOTAL).
 *   • Virtualised thumbnail strip below — only the visible thumbnails
 *     are rendered, lazy-loaded via `loading="lazy"`.
 *
 * The component fetches frames page-by-page from
 * `GET /v2/bundles/{bundleAppId}/groups/{groupAppId}/files` and
 * caches one page at a time — moving the slider triggers a refetch
 * only when crossing a page boundary.
 *
 * Auth: the thumbnail/content endpoints under `/v2/containers/{appId}/payload/...`
 * are JWT-protected. The `<img src>` element cannot send a custom
 * Authorization header, so the URLs carry `?access_token=…` per
 * MFFD-VIDEOREF-SCALE-1 (JWTFilter reads it as a fallback).
 *
 * Out of scope for v1:
 *   • Compare-mode across bundles (MFFD-IMAGEBUNDLE-COMPARE-1).
 *   • Per-frame annotation overlays (defer to N-series).
 */
import {
  planPageForFrame,
  needsRefetch,
  frameLabel,
} from "~/utils/imageBundleScrubber";
import { withAccessTokenQueryParam } from "~/utils/videoUrl";

interface ShepardFile {
  oid?: string | null;
  filename?: string | null;
}

interface PagedFiles {
  items: ShepardFile[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

const props = defineProps<{
  bundleAppId: string;
  groupAppId: string;
  /**
   * appId of the FileContainer backing this bundle — used for thumbnail/payload
   * URLs via the unified /v2/containers/{appId}/payload/... endpoints
   * (APISIMP-CONT-NS-COLLAPSE; the per-kind /v2/file-containers/{mongoId}/...
   * routes were removed).
   */
  containerAppId?: string | null;
  /** Display name (shown above the slider). */
  groupName?: string | null;
  /** Override the default page size (default 200). */
  pageSize?: number;
}>();

const PAGE_SIZE = computed(() => props.pageSize ?? 200);

const { data: session } = useAuth();
const accessToken = computed(() => session.value?.accessToken ?? null);

function v2BaseUrl(): string {
  const config = useRuntimeConfig().public;
  const explicit = config.backendV2ApiUrl as string | undefined;
  if (explicit && explicit.length > 0) return explicit.replace(/\/$/, "");
  return (config.backendApiUrl as string)
    .replace(/\/shepard\/api\/?$/, "")
    .replace(/\/$/, "");
}

// ── state ──────────────────────────────────────────────────────────────────

const totalFrames = ref<number>(0);
const cachedPage = ref<number | null>(null);
const cachedItems = ref<ShepardFile[]>([]);
const selectedFrame = ref<number>(0);
const isLoading = ref<boolean>(false);
const fetchError = ref<string | null>(null);

// ── fetching ───────────────────────────────────────────────────────────────

async function fetchPage(page: number, size: number): Promise<PagedFiles | null> {
  const token = accessToken.value;
  if (!token) {
    fetchError.value = "Not authenticated";
    return null;
  }
  const url =
    `${v2BaseUrl()}/v2/bundles/${encodeURIComponent(props.bundleAppId)}` +
    `/groups/${encodeURIComponent(props.groupAppId)}` +
    `/files?page=${page}&size=${size}`;
  isLoading.value = true;
  fetchError.value = null;
  try {
    const r = await fetch(url, {
      headers: {
        Authorization: `Bearer ${token}`,
        Accept: "application/json",
      },
    });
    if (!r.ok) {
      const text = await r.text().catch(() => "");
      fetchError.value = `HTTP ${r.status}: ${text.slice(0, 200)}`;
      return null;
    }
    return (await r.json()) as PagedFiles;
  } catch (err) {
    fetchError.value = err instanceof Error ? err.message : "Network error";
    return null;
  } finally {
    isLoading.value = false;
  }
}

async function ensurePageFor(frameIndex: number): Promise<void> {
  const plan = planPageForFrame(frameIndex, totalFrames.value, PAGE_SIZE.value);
  if (!needsRefetch(cachedPage.value, plan)) return;
  const paged = await fetchPage(plan.page, plan.pageSize);
  if (!paged) return;
  totalFrames.value = Number(paged.totalElements) || 0;
  cachedPage.value = paged.page;
  cachedItems.value = paged.items;
}

// ── derived: selected frame's file ────────────────────────────────────────

const selectedFile = computed<ShepardFile | null>(() => {
  if (totalFrames.value === 0) return null;
  const plan = planPageForFrame(
    selectedFrame.value,
    totalFrames.value,
    PAGE_SIZE.value,
  );
  if (cachedPage.value !== plan.page) return null;
  return cachedItems.value[plan.offsetInPage] ?? null;
});

// ── URLs ──────────────────────────────────────────────────────────────────

function thumbnailUrl(oid: string | null | undefined): string {
  if (!props.containerAppId || !oid) return "";
  // TH1a — PNG thumbnail at <= 200 px longest side.
  const url =
    `${v2BaseUrl()}/v2/containers/${encodeURIComponent(props.containerAppId)}` +
    `/payload/${encodeURIComponent(oid)}/thumbnail?size=200`;
  return withAccessTokenQueryParam(url, accessToken.value);
}

function fullSizeUrl(oid: string | null | undefined): string {
  if (!props.containerAppId || !oid) return "";
  const url =
    `${v2BaseUrl()}/v2/containers/${encodeURIComponent(props.containerAppId)}` +
    `/payload/${encodeURIComponent(oid)}`;
  return withAccessTokenQueryParam(url, accessToken.value);
}

// ── lifecycle / watchers ──────────────────────────────────────────────────

watch(
  () => [props.bundleAppId, props.groupAppId, props.containerAppId],
  () => {
    totalFrames.value = 0;
    cachedPage.value = null;
    cachedItems.value = [];
    selectedFrame.value = 0;
    ensurePageFor(0);
  },
  { immediate: true },
);

watch(selectedFrame, async (next) => {
  await ensurePageFor(next);
});

const a11yLabel = computed(() => frameLabel(selectedFrame.value, totalFrames.value));
</script>

<template>
  <div class="image-bundle-viewer">
    <div v-if="groupName" class="text-subtitle-2 mb-2">
      {{ groupName }}
      <span class="text-low-emphasis ml-1">({{ totalFrames }} frames)</span>
    </div>

    <v-alert v-if="fetchError" type="error" variant="tonal" density="compact" class="mb-2">
      {{ fetchError }}
    </v-alert>

    <!-- Large preview -->
    <div class="preview">
      <img
        v-if="selectedFile?.oid"
        :src="fullSizeUrl(selectedFile.oid)"
        :alt="selectedFile.filename ?? a11yLabel"
        class="preview-img"
        loading="lazy"
      >
      <div v-else-if="isLoading" class="preview-loading">
        <v-progress-circular indeterminate color="white" size="32" />
      </div>
      <div v-else class="preview-empty">No frame</div>
    </div>

    <!-- Scrubber slider -->
    <div v-if="totalFrames > 0" class="scrubber mt-2">
      <v-slider
        v-model="selectedFrame"
        :min="0"
        :max="Math.max(0, totalFrames - 1)"
        :step="1"
        thumb-label
        :aria-label="a11yLabel"
        hide-details
        density="compact"
      />
      <div class="text-caption text-medium-emphasis text-center">
        {{ a11yLabel }}
        <span v-if="selectedFile?.filename" class="ml-2">— {{ selectedFile.filename }}</span>
      </div>
    </div>

    <!-- Virtualised thumbnail strip -->
    <div v-if="cachedItems.length > 0" class="thumb-strip mt-3" data-testid="thumb-strip">
      <v-virtual-scroll
        :items="cachedItems"
        :item-height="64"
        :item-width="64"
        max-height="84"
        horizontal
      >
        <template #default="{ item, index }">
          <button
            type="button"
            class="thumb"
            :class="{ active: cachedPage !== null && cachedPage * PAGE_SIZE + index === selectedFrame }"
            :aria-label="`Go to frame ${index + 1} on page ${cachedPage}`"
            @click="cachedPage !== null && (selectedFrame = cachedPage * PAGE_SIZE + index)"
          >
            <img
              v-if="(item as ShepardFile).oid"
              :src="thumbnailUrl((item as ShepardFile).oid)"
              :alt="`thumb ${index}`"
              loading="lazy"
            >
          </button>
        </template>
      </v-virtual-scroll>
    </div>
  </div>
</template>

<style scoped lang="scss">
.image-bundle-viewer {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.preview {
  display: flex;
  align-items: center;
  justify-content: center;
  background: #000;
  border-radius: 4px;
  min-height: 180px;
  max-height: 480px;
  overflow: hidden;
}

.preview-img {
  max-width: 100%;
  max-height: 480px;
  object-fit: contain;
}

.preview-loading,
.preview-empty {
  color: rgba(255, 255, 255, 0.7);
  font-size: 0.9em;
}

.scrubber {
  width: 100%;
}

.thumb-strip {
  width: 100%;
  border-radius: 4px;
  overflow: hidden;
  background: rgba(0, 0, 0, 0.04);
  padding: 4px;
}

.thumb {
  width: 60px;
  height: 60px;
  margin: 0 2px;
  border: 2px solid transparent;
  border-radius: 4px;
  background: #222;
  padding: 0;
  cursor: pointer;
  overflow: hidden;

  &.active {
    border-color: rgb(var(--v-theme-primary));
  }

  img {
    width: 100%;
    height: 100%;
    object-fit: cover;
    display: block;
  }
}
</style>
