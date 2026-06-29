<script setup lang="ts">
/**
 * REF-VIDEO-DETAIL-PAGE — appId-keyed detail page for a VideoStreamReference.
 *
 * Mirrors the timeseries-ref detail page shape (V2CONV pattern): the route
 * param is the v2 appId (UUID v7), the reference is loaded via the unified
 * GET /v2/references/{appId} envelope, the per-kind payload carries video
 * metadata (durationSeconds, width, height, codec, …). Numeric ids are only
 * resolved at call time for the (currently none required) v1 fallback path.
 *
 * The page renders:
 *   - Breadcrumbs: Collections › collection.name › DataObject.name › videoRef.name
 *   - TitleAndMetadataDisplay header with Annotate / Edit / Delete actions
 *   - Inline <VideoPlayer> against /v2/references/{appId}/content (range-aware)
 *   - SemanticAnnotationList (SEMA-V6 polymorphic annotations)
 *   - Edit dialog: EditVideoStreamReferenceDialog (rename only)
 *   - Delete dialog: raw fetch DELETE
 *       /v2/data-objects/{doAppId}/video-stream-references/{appId}
 *     (same path the DataObjectDataReferencesTable uses for video rows)
 */
import VideoPlayer from "~/components/common/VideoPlayer.vue";
import EditVideoStreamReferenceDialog from "~/components/context/dataobject/EditVideoStreamReferenceDialog.vue";
import { useFetchReferenceV2 } from "~/composables/context/useFetchReferenceV2";

definePageMeta({ layout: "collection" });

interface VideoRefView {
  appId: string;
  id: number;
  name: string;
  createdAt: Date;
  createdBy: string;
  updatedAt: Date | null;
  updatedBy: string | null;
  mimeType?: string | null;
  fileSizeBytes?: number | null;
  durationSeconds?: number | null;
  width?: number | null;
  height?: number | null;
  frameRate?: number | null;
  videoCodec?: string | null;
  audioCodec?: string | null;
}

const { routeParams } = useCollectionRouteParams();
const collectionIdStr = routeParams.value.collectionId ?? "";
const dataObjectIdStr = routeParams.value.dataObjectId ?? "";

const { collection, isAllowedToEditCollection } =
  useFetchCollection(collectionIdStr);
const { dataObject } = useFetchDataObject(collectionIdStr, dataObjectIdStr);

// Load the reference through the unified v2 envelope (UX612-C1 / V2CONV) —
// the same shape the timeseries-ref page uses post-V2CONV.
const { referenceV2, notFound: referenceNotFound, refresh: refreshReferenceV2 } =
  useFetchReferenceV2(() => routeParams.value.videoStreamReferenceId);

const videoReference = computed<VideoRefView | undefined>(() => {
  const r = referenceV2.value;
  if (!r) return undefined;
  const p = (r.payload ?? {}) as Record<string, unknown>;
  return {
    appId: r.appId ?? routeParams.value.videoStreamReferenceId ?? "",
    id: typeof r.id === "number" ? r.id : 0,
    name: r.name ?? "",
    createdAt: r.createdAt,
    createdBy: r.createdBy,
    updatedAt: r.updatedAt,
    updatedBy: r.updatedBy,
    mimeType: (p.mimeType as string | null | undefined) ?? null,
    fileSizeBytes: (p.fileSizeBytes as number | null | undefined) ?? null,
    durationSeconds: (p.durationSeconds as number | null | undefined) ?? null,
    width: (p.width as number | null | undefined) ?? null,
    height: (p.height as number | null | undefined) ?? null,
    frameRate: (p.frameRate as number | null | undefined) ?? null,
    videoCodec: (p.videoCodec as string | null | undefined) ?? null,
    audioCodec: (p.audioCodec as string | null | undefined) ?? null,
  };
});

const videoReferenceAppId = computed<string | undefined>(
  () => videoReference.value?.appId,
);

// ── content URL + access token (mirror VideoStreamReferencesPane pattern) ────

function v2BaseUrl(): string {
  const config = useRuntimeConfig().public;
  const explicit = config.backendV2ApiUrl as string | undefined;
  if (explicit && explicit.length > 0) return explicit.replace(/\/$/, "");
  return (config.backendApiUrl as string)
    .replace(/\/shepard\/api\/?$/, "")
    .replace(/\/$/, "");
}

const { data: session } = useAuth();
const accessToken = computed(() => session.value?.accessToken ?? null);

const videoContentUrl = computed<string | undefined>(() => {
  const appId = videoReferenceAppId.value;
  if (!appId) return undefined;
  return `${v2BaseUrl()}/v2/references/${encodeURIComponent(appId)}/content`;
});

// ── dialog state ────────────────────────────────────────────────────────────

const showDeleteDialog = ref<boolean>(false);
const showAddAnnotationDialog = ref<boolean>(false);
const showEditDialog = ref<boolean>(false);

function onAnnotate() {
  showAddAnnotationDialog.value = true;
}

function onEdit() {
  showEditDialog.value = true;
}

function onDelete() {
  showDeleteDialog.value = true;
}

function onRenamed(newName: string) {
  if (videoReference.value) videoReference.value.name = newName;
  // Re-fetch to capture any server-side updates beyond `name`.
  refreshReferenceV2();
}

// ── delete (raw fetch — mirrors DataObjectDataReferencesTable line ~175) ────

async function deleteVideoReference() {
  const appId = videoReferenceAppId.value;
  const doAppId = dataObject.value?.appId;
  if (!appId || !doAppId) return;
  const token = accessToken.value;
  if (!token) {
    handleError(new Error("Not authenticated"), "deleteVideoReference");
    return;
  }
  const url =
    `${v2BaseUrl()}/v2/data-objects/${encodeURIComponent(doAppId)}` +
    `/video-stream-references/${encodeURIComponent(appId)}`;
  try {
    const response = await fetch(url, {
      method: "DELETE",
      headers: { Authorization: `Bearer ${token}` },
    });
    if (response.ok || response.status === 204) {
      emitSuccess(
        `Successfully deleted video reference "${videoReference.value?.name ?? appId}"`,
      );
      navigateTo(
        collectionsPath +
          routeParams.value.collectionId +
          dataObjectsPathFragment +
          routeParams.value.dataObjectId,
      );
    } else {
      handleError(`HTTP ${response.status}`, "delete video reference");
      showDeleteDialog.value = false;
    }
  } catch (err) {
    handleError(err, "delete video reference");
    showDeleteDialog.value = false;
  }
}

// ── metadata helpers ────────────────────────────────────────────────────────

function formatDuration(seconds: number | null | undefined): string {
  if (seconds == null) return "—";
  const total = Math.floor(seconds);
  const h = Math.floor(total / 3600);
  const m = Math.floor((total % 3600) / 60);
  const s = total % 60;
  const mm = String(m).padStart(2, "0");
  const ss = String(s).padStart(2, "0");
  return h > 0 ? `${h}:${mm}:${ss}` : `${mm}:${ss}`;
}

function formatBytes(bytes: number | null | undefined): string {
  if (bytes == null) return "—";
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  if (bytes < 1024 * 1024 * 1024)
    return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
  return `${(bytes / (1024 * 1024 * 1024)).toFixed(2)} GB`;
}

function formatBitrate(view: VideoRefView): string {
  if (!view.fileSizeBytes || !view.durationSeconds || view.durationSeconds <= 0)
    return "—";
  const bps = (view.fileSizeBytes * 8) / view.durationSeconds;
  if (bps < 1000) return `${Math.round(bps)} bps`;
  if (bps < 1_000_000) return `${(bps / 1000).toFixed(1)} Kbps`;
  return `${(bps / 1_000_000).toFixed(2)} Mbps`;
}

const videoReferenceDisplayName = computed(() =>
  videoReference.value ? `Video Reference "${videoReference.value.name}"` : "",
);

watch(videoReference, () => {
  useHead({
    title: (videoReference.value?.name ?? "Video reference") + " | shepard",
  });
});
</script>

<template>
  <div style="max-width: 1000px">
    <v-container class="pa-0 fill-height" fluid max-width="1000px">
      <v-row v-if="!!collection && !!dataObject && !!videoReference">
        <v-col cols="12">
          <Breadcrumbs
            :items="[
              {
                title: 'Collections',
                to: collectionsPath,
              },
              {
                title: `${collection.name}`,
                to: collectionsPath + collection.id,
              },
              {
                title: dataObject.name,
                to:
                  collectionsPath +
                  routeParams.collectionId +
                  dataObjectsPathFragment +
                  routeParams.dataObjectId,
              },
              {
                title: `${videoReference.name}`,
                to:
                  collectionsPath +
                  routeParams.collectionId +
                  dataObjectsPathFragment +
                  routeParams.dataObjectId +
                  videoStreamReferencesPathFragment +
                  routeParams.videoStreamReferenceId,
              },
            ]"
          />
        </v-col>
        <v-col cols="12">
          <v-container class="pa-0" fluid>
            <v-row no-gutters>
              <TitleAndMetadataDisplay
                :entity="{
                  ...videoReference,
                  name: videoReferenceDisplayName,
                  type: 'Video',
                }"
                :on-annotate="onAnnotate"
                :on-delete="isAllowedToEditCollection ? onDelete : undefined"
                :on-edit="
                  isAllowedToEditCollection && videoReferenceAppId
                    ? onEdit
                    : undefined
                "
                id-label="ID"
              />
            </v-row>

            <v-row v-if="videoContentUrl">
              <v-col cols="12">
                <!-- Inline player — same pattern as VideoStreamReferencesPane.
                     The accessToken travels as ?access_token=… (JWTFilter reads
                     it as a query-param fallback for native <video src>). -->
                <VideoPlayer
                  :src="videoContentUrl"
                  :access-token="accessToken"
                />
              </v-col>
            </v-row>

            <!-- Metadata chips (mirror VideoStreamReferencesPane) -->
            <v-row>
              <v-col cols="12" class="d-flex flex-wrap ga-2">
                <v-chip
                  v-if="videoReference.durationSeconds != null"
                  size="small"
                  variant="tonal"
                  prepend-icon="mdi-timer-outline"
                >
                  {{ formatDuration(videoReference.durationSeconds) }}
                </v-chip>
                <v-chip
                  v-if="videoReference.width != null && videoReference.height != null"
                  size="small"
                  variant="tonal"
                  prepend-icon="mdi-monitor-screenshot"
                >
                  {{ videoReference.width }}&times;{{ videoReference.height }}
                </v-chip>
                <v-chip
                  v-if="videoReference.videoCodec"
                  size="small"
                  variant="tonal"
                  prepend-icon="mdi-code-tags"
                >
                  {{ videoReference.videoCodec }}
                </v-chip>
                <v-chip
                  v-if="videoReference.audioCodec"
                  size="small"
                  variant="tonal"
                  prepend-icon="mdi-volume-high"
                >
                  {{ videoReference.audioCodec }}
                </v-chip>
                <v-chip
                  v-if="videoReference.frameRate != null"
                  size="small"
                  variant="tonal"
                  prepend-icon="mdi-play-speed"
                >
                  {{
                    videoReference.frameRate.toFixed(
                      videoReference.frameRate % 1 === 0 ? 0 : 2,
                    )
                  }} fps
                </v-chip>
                <v-chip
                  v-if="videoReference.fileSizeBytes != null"
                  size="small"
                  variant="tonal"
                  prepend-icon="mdi-database-outline"
                >
                  {{ formatBytes(videoReference.fileSizeBytes) }}
                </v-chip>
                <v-chip
                  v-if="
                    videoReference.fileSizeBytes != null &&
                    videoReference.durationSeconds != null
                  "
                  size="small"
                  variant="tonal"
                  prepend-icon="mdi-speedometer"
                >
                  {{ formatBitrate(videoReference) }}
                </v-chip>
              </v-col>
            </v-row>

            <v-row v-if="videoContentUrl">
              <v-col cols="12">
                <v-btn
                  :href="videoContentUrl"
                  download
                  variant="tonal"
                  density="comfortable"
                  prepend-icon="mdi-download-outline"
                  size="small"
                >
                  Download
                </v-btn>
              </v-col>
            </v-row>

            <v-row align="center" justify="space-between">
              <v-col>
                <SemanticAnnotationList
                  v-if="videoReferenceAppId"
                  :annotated="
                    new AnnotatedReference(
                      videoReferenceAppId,
                      'VideoStreamReference',
                    )
                  "
                  :can-delete="!!isAllowedToEditCollection"
                />
              </v-col>
            </v-row>
          </v-container>
        </v-col>
      </v-row>
      <!-- 404 → honest empty state (UU1 / UI-404-NICE-EMPTY-STATE-REF-PAGES). -->
      <EntityNotFound
        v-else-if="referenceNotFound"
        entity-kind="VideoStreamReference"
        :requested-id="routeParams.videoStreamReferenceId ?? ''"
        :parent-route="
          collectionsPath +
          routeParams.collectionId +
          dataObjectsPathFragment +
          routeParams.dataObjectId
        "
      />
      <CenteredLoadingSpinner v-else />
    </v-container>

    <EditVideoStreamReferenceDialog
      v-if="showEditDialog && videoReferenceAppId && videoReference"
      v-model:show-dialog="showEditDialog"
      :video-stream-reference-app-id="videoReferenceAppId"
      :current-name="videoReference.name"
      @saved="onRenamed"
    />
    <ConfirmDeleteDialog
      v-if="showDeleteDialog"
      v-model:show-dialog="showDeleteDialog"
      @confirmed="deleteVideoReference"
    />
    <AddAnnotationDialog
      v-if="showAddAnnotationDialog && videoReferenceAppId"
      v-model:show-dialog="showAddAnnotationDialog"
      :annotated="
        new AnnotatedReference(videoReferenceAppId, 'VideoStreamReference')
      "
    />
  </div>
</template>
