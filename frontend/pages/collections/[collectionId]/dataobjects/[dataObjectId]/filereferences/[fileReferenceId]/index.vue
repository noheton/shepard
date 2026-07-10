<script setup lang="ts">
/**
 * VIEWER-AS-VIEW-RECIPE-RULE-2026-06-29 PR-4 — file-reference detail page is
 * now metadata + Download + Open-as picker. Inline content rendering moved
 * out: every viewer ships as a VIEW_RECIPE shape consumed through
 * /shapes/render. See `memory/feedback_file_viewers_as_view_recipe.md` and
 * `aidocs/16:VIEWER-AS-VIEW-RECIPE-RULE-2026-06-29` for the doctrine.
 *
 * Loads the reference through the unified v2 envelope (UX612-C1) — same
 * shape the videostream + timeseries detail pages use — so the file-kind
 * discriminator (`fileKind`) is in hand for the picker, and the route param
 * is the appId throughout (frontend-v2-only rule).
 */
import {
  ReferencesApi,
  type ShepardFile,
} from "@dlr-shepard/backend-client";
import { useV2ShepardApi } from "~/composables/common/api/useV2ShepardApi";
import { useFetchReferenceV2 } from "~/composables/context/useFetchReferenceV2";
import ViewRecipePicker from "~/components/shapes/ViewRecipePicker.vue";
import { buildInlineImageContentUrl } from "~/utils/fileRenditionUrl";

definePageMeta({ layout: "collection" });

const { routeParams } = useCollectionRouteParams();
const collectionIdStr = routeParams.value.collectionId ?? "";
const dataObjectIdStr = routeParams.value.dataObjectId ?? "";

const showDeleteDialog = ref<boolean>(false);
const showAddAnnotationDialog = ref<boolean>(false);
const showEditDialog = ref<boolean>(false);

const { collection, isAllowedToEditCollection } =
  useFetchCollection(collectionIdStr);
const { dataObject } = useFetchDataObject(collectionIdStr, dataObjectIdStr);

const {
  referenceV2,
  notFound: fileReferenceNotFound,
  refresh: refreshReferenceV2,
} = useFetchReferenceV2(() => routeParams.value.fileReferenceId);

interface FileRefView {
  appId: string;
  /** Numeric Neo4j id from the v2 envelope. Used by TitleAndMetadataDisplay's
   *  legacy entity shape; never on the wire. */
  id: number;
  name: string;
  createdAt: Date;
  createdBy: string;
  updatedAt: Date | null;
  updatedBy: string | null;
  /** "singleton" (FR1b) | "bundle" (FR1a). null defensively. */
  referenceShape: string | null;
  /** Discriminator the picker filters on. */
  fileKind: string | null;
  /** Singleton payload (when referenceShape === "singleton"). */
  file: ShepardFile | null;
  /** Bundle file count, when bundle. */
  bundleFileCount: number | null;
}

const fileReference = computed<FileRefView | undefined>(() => {
  const r = referenceV2.value;
  if (!r) return undefined;
  const payload = (r.payload ?? {}) as {
    file?: ShepardFile | null;
    files?: ShepardFile[] | null;
  };
  const bundleFiles = Array.isArray(payload.files) ? payload.files : null;
  return {
    appId: r.appId ?? routeParams.value.fileReferenceId ?? "",
    id: typeof r.id === "number" ? r.id : 0,
    name: r.name ?? "",
    createdAt: r.createdAt,
    createdBy: r.createdBy,
    updatedAt: r.updatedAt,
    updatedBy: r.updatedBy,
    referenceShape: r.referenceShape ?? null,
    fileKind: r.fileKind ?? null,
    file: payload.file ?? null,
    bundleFileCount: bundleFiles ? bundleFiles.length : null,
  };
});

const fileReferenceAppId = computed(() => fileReference.value?.appId);
const fileReferenceDisplayName = computed(() =>
  fileReference.value ? `File Reference "${fileReference.value.name}"` : "",
);

// ── content URL (signed v2 stream; the picker hands off to /shapes/render,
//    the Download button is a direct content GET for "I just want the bytes"). ─

function v2BaseUrl(): string {
  const config = useRuntimeConfig().public;
  const explicit = config.backendV2ApiUrl as string | undefined;
  if (explicit && explicit.length > 0) return explicit.replace(/\/$/, "");
  return (config.backendApiUrl as string)
    .replace(/\/shepard\/api\/?$/, "")
    .replace(/\/$/, "");
}

const fileContentUrl = computed<string | undefined>(() => {
  const appId = fileReferenceAppId.value;
  if (!appId) return undefined;
  if (fileReference.value?.referenceShape !== "singleton") return undefined;
  return `${v2BaseUrl()}/v2/references/${encodeURIComponent(appId)}/content`;
});

// BUG-DO-DETAIL-I-VIDEOPLAYER-2026-06-29 (sibling) — restore an inline
// default viewer for the common quick-look kinds (image, PDF). PR-4 stripped
// every inline viewer in favour of the picker-only flow; for kinds whose
// default view is "just show it" the empty picker is poor UX. The
// ViewRecipePicker stays available below as the "More views" advanced
// augmentation for richer renderers (URDF, NDT overlay, …).
const { data: session } = useAuth();
const accessToken = computed(() => session.value?.accessToken ?? null);
const showMoreViews = ref(false);

function withAccessToken(url: string): string {
  const t = accessToken.value;
  if (!t) return url;
  const sep = url.includes("?") ? "&" : "?";
  return `${url}${sep}access_token=${encodeURIComponent(t)}`;
}

// TIFF-PREVIEW-SUPPORT: browsers cannot render `image/tiff` natively, so a
// TIFF-backed singleton (fileKind="image", filename ending .tif/.tiff) asks
// the content endpoint for the `?rendition=png` transcode instead of the
// raw bytes. Every other image format keeps requesting the raw content URL
// unchanged — buildInlineImageContentUrl() is a no-op for non-TIFF names.
const inlineImageFilename = computed(
  () => fileReference.value?.file?.filename ?? fileReference.value?.name ?? null,
);

const inlineImageUrl = computed<string | undefined>(() => {
  if (fileReference.value?.fileKind !== "image") return undefined;
  if (!fileContentUrl.value) return undefined;
  const url = buildInlineImageContentUrl(
    fileContentUrl.value,
    inlineImageFilename.value,
  );
  return withAccessToken(url);
});

// TIFF-PREVIEW-SUPPORT: the `?rendition=png` transcode is best-effort — a
// corrupt/unsupported TIFF or a source over the decode-size cap falls back
// to raw TIFF bytes server-side (never a 500), which the browser then can't
// paint either. Surface that gracefully instead of a broken-image icon.
const inlineImagePreviewFailed = ref(false);
watch(inlineImageUrl, () => {
  inlineImagePreviewFailed.value = false;
});

const inlinePdfUrl = computed<string | undefined>(() => {
  if (fileReference.value?.fileKind !== "pdf") return undefined;
  if (!fileContentUrl.value) return undefined;
  return withAccessToken(fileContentUrl.value);
});

// MP4-PROMOTE-VIDEO: singleton FileReference tagged fileKind="video" gets an
// inline player. Unlike image/pdf we hand VideoPlayer the RAW content URL +
// the access token separately — VideoPlayer appends `?access_token=` itself
// (native <video> can't set an Authorization header) and does its own Range
// requests, so we must NOT pre-append the token here (would double-append).
const inlineVideoUrl = computed<string | undefined>(() => {
  if (fileReference.value?.fileKind !== "video") return undefined;
  if (!fileContentUrl.value) return undefined;
  return fileContentUrl.value;
});

const hasInlineDefaultView = computed(
  () => !!inlineImageUrl.value || !!inlinePdfUrl.value || !!inlineVideoUrl.value,
);


// ── metadata helpers ────────────────────────────────────────────────────────

function formatBytes(bytes: number | null | undefined): string {
  if (bytes == null) return "—";
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  if (bytes < 1024 * 1024 * 1024)
    return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
  return `${(bytes / (1024 * 1024 * 1024)).toFixed(2)} GB`;
}

// ── action handlers ─────────────────────────────────────────────────────────

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
  if (fileReference.value) fileReference.value.name = newName;
  refreshReferenceV2();
}

function onPickRecipe(payload: { templateAppId: string }) {
  const focus = fileReferenceAppId.value;
  if (!focus) return;
  navigateTo(
    `/shapes/render?templateAppId=${encodeURIComponent(payload.templateAppId)}` +
      `&focusShepardId=${encodeURIComponent(focus)}`,
  );
}

async function deleteFileReference() {
  const appId = fileReferenceAppId.value;
  if (!appId) return;
  try {
    await useV2ShepardApi(ReferencesApi).value.deleteReference({ appId });
    navigateTo(
      collectionsPath +
        routeParams.value.collectionId +
        dataObjectsPathFragment +
        routeParams.value.dataObjectId,
    );
  } catch (error) {
    handleError(error, "deleteFileReference");
    showDeleteDialog.value = false;
  }
}

watch(fileReference, () => {
  useHead({
    title: (fileReference.value?.name ?? "File reference") + " | shepard",
  });
});
</script>

<template>
  <div style="max-width: 1000px">
    <v-container class="pa-0 fill-height" fluid max-width="1000px">
      <v-row v-if="!!collection && !!dataObject && !!fileReference">
        <v-col cols="12">
          <Breadcrumbs
            :items="[
              {
                title: 'Collections',
                to: collectionsPath,
              },
              {
                title: `${collection.name}`,
                to: collectionsPath + routeParams.collectionId,
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
                title: `${fileReference.name}`,
                to:
                  collectionsPath +
                  routeParams.collectionId +
                  dataObjectsPathFragment +
                  routeParams.dataObjectId +
                  fileReferencesPathFragment +
                  routeParams.fileReferenceId,
              },
            ]"
          />
        </v-col>
        <v-col cols="12">
          <v-container class="pa-0" fluid>
            <v-row no-gutters>
              <TitleAndMetadataDisplay
                :entity="{
                  ...fileReference,
                  name: fileReferenceDisplayName,
                  type: 'File',
                }"
                :on-annotate="onAnnotate"
                :on-delete="isAllowedToEditCollection ? onDelete : undefined"
                :on-edit="
                  isAllowedToEditCollection && fileReferenceAppId
                    ? onEdit
                    : undefined
                "
                id-label="ID"
              />
            </v-row>

            <!-- Metadata card: file-kind discriminator + size + checksum so the
                 user knows what they're holding before they pick a recipe. -->
            <v-row>
              <v-col cols="12" class="d-flex flex-wrap ga-2">
                <v-chip
                  v-if="fileReference.fileKind"
                  size="small"
                  variant="tonal"
                  prepend-icon="mdi-shape-outline"
                  data-test="file-kind-chip"
                >
                  {{ fileReference.fileKind }}
                </v-chip>
                <v-chip
                  v-if="fileReference.referenceShape"
                  size="small"
                  variant="tonal"
                  prepend-icon="mdi-package-variant-closed"
                >
                  {{ fileReference.referenceShape }}
                </v-chip>
                <v-chip
                  v-if="fileReference.file?.fileSize != null"
                  size="small"
                  variant="tonal"
                  prepend-icon="mdi-database-outline"
                >
                  {{ formatBytes(fileReference.file.fileSize) }}
                </v-chip>
                <v-chip
                  v-if="fileReference.file?.md5"
                  size="small"
                  variant="tonal"
                  prepend-icon="mdi-pound"
                >
                  md5:{{ fileReference.file.md5.slice(0, 12) }}…
                </v-chip>
                <v-chip
                  v-if="
                    fileReference.referenceShape === 'bundle' &&
                    fileReference.bundleFileCount != null
                  "
                  size="small"
                  variant="tonal"
                  prepend-icon="mdi-folder-multiple"
                >
                  {{ fileReference.bundleFileCount }} files
                </v-chip>
              </v-col>
            </v-row>

            <!-- BUG-DO-DETAIL-I-VIDEOPLAYER-2026-06-29 (sibling): inline
                 quick-look default for common kinds. Image + PDF are the
                 "just show it" cases; richer kinds (URDF, NDT, …) go through
                 the picker below. -->
            <v-row v-if="inlineImageUrl">
              <v-col cols="12">
                <!-- TIFF-PREVIEW-SUPPORT: the `?rendition=png` transcode is
                     best-effort — fall back to a friendly notice (rather
                     than a broken-image icon) if the browser can't paint
                     what came back, e.g. a source over the decode-size cap. -->
                <v-alert
                  v-if="inlineImagePreviewFailed"
                  type="warning"
                  variant="tonal"
                  density="comfortable"
                  data-testid="inline-image-preview-failed"
                >
                  Preview unavailable for this file — use Download instead.
                </v-alert>
                <v-img
                  v-else
                  :src="inlineImageUrl"
                  :alt="fileReference.name"
                  max-height="600"
                  contain
                  data-testid="inline-image-preview"
                  @error="inlineImagePreviewFailed = true"
                />
              </v-col>
            </v-row>

            <v-row v-if="inlinePdfUrl">
              <v-col cols="12">
                <iframe
                  :src="inlinePdfUrl"
                  style="width: 100%; height: 600px; border: 1px solid rgba(0,0,0,0.12); border-radius: 4px"
                  data-testid="inline-pdf-preview"
                />
              </v-col>
            </v-row>

            <!-- MP4-PROMOTE-VIDEO: inline player for fileKind="video" singleton
                 FileReferences. VideoPlayer streams the content URL natively
                 (Range requests) and appends the access token itself. -->
            <v-row v-if="inlineVideoUrl">
              <v-col cols="12">
                <VideoPlayer
                  :src="inlineVideoUrl"
                  :access-token="accessToken"
                  data-testid="inline-video-preview"
                />
              </v-col>
            </v-row>

            <!-- SCENEGRAPH-NAV-02: in-context "Open in 3D view / Create 3D view"
                 affordance for URDF / RDK FileReferences.
                 `OpenIn3dViewButton` self-qualifies: it fetches annotations and
                 checks for urn:shepard:urdf:* / urn:shepard:rdk:* predicates or
                 filename extension, then either routes to the existing
                 MAPPING_RECIPE play template or mints a new one.
                 Per "tool entry points are in-context first" (CLAUDE.md). -->
            <v-row v-if="fileReference.name && fileReferenceAppId">
              <v-col cols="12">
                <OpenIn3dViewButton
                  :file-reference-name="fileReference.name"
                  :file-reference-app-id="fileReferenceAppId"
                />
              </v-col>
            </v-row>

            <v-row v-if="fileContentUrl">
              <v-col cols="12" class="d-flex flex-wrap ga-2">
                <v-btn
                  :href="fileContentUrl"
                  download
                  variant="tonal"
                  density="comfortable"
                  prepend-icon="mdi-download-outline"
                  size="small"
                >
                  Download
                </v-btn>
                <v-btn
                  variant="text"
                  density="comfortable"
                  size="small"
                  :prepend-icon="showMoreViews ? 'mdi-chevron-up' : 'mdi-chevron-down'"
                  data-testid="file-more-views-toggle"
                  @click="showMoreViews = !showMoreViews"
                >
                  {{ showMoreViews ? "Hide more views" : "More views" }}
                </v-btn>
              </v-col>
            </v-row>

            <!-- Picker: ADVANCED when an inline default exists; PRIMARY when
                 the file-kind has no default quick-look (URDF, KRL, etc.). -->
            <v-expand-transition>
              <v-row v-if="showMoreViews || !hasInlineDefaultView">
                <v-col cols="12">
                  <div class="text-subtitle-2 mb-2">Open as…</div>
                  <ViewRecipePicker
                    :file-kind="fileReference.fileKind"
                    :focus-shepard-id="fileReferenceAppId"
                    @select="onPickRecipe"
                  />
                </v-col>
              </v-row>
            </v-expand-transition>

            <v-row align="center" justify="space-between">
              <v-col>
                <SemanticAnnotationList
                  v-if="fileReferenceAppId"
                  :annotated="
                    new AnnotatedReference(fileReferenceAppId, 'FileReference')
                  "
                  :can-delete="!!isAllowedToEditCollection"
                />
              </v-col>
            </v-row>
          </v-container>
        </v-col>
      </v-row>
      <!-- UI-404-NICE-EMPTY-STATE-REF-PAGES: 404 on the file reference fetch →
           honest empty state instead of an eternal spinner. -->
      <EntityNotFound
        v-else-if="fileReferenceNotFound"
        entity-kind="FileReference"
        :requested-id="routeParams.fileReferenceId ?? ''"
        :parent-route="
          collectionsPath +
          routeParams.collectionId +
          dataObjectsPathFragment +
          routeParams.dataObjectId
        "
      />
      <CenteredLoadingSpinner v-else />
    </v-container>
    <EditFileReferenceDialog
      v-if="showEditDialog && fileReferenceAppId && fileReference"
      v-model:show-dialog="showEditDialog"
      :file-reference-app-id="fileReferenceAppId"
      :current-name="fileReference.name"
      @saved="onRenamed"
    />
    <ConfirmDeleteDialog
      v-if="showDeleteDialog"
      v-model:show-dialog="showDeleteDialog"
      @confirmed="deleteFileReference"
    />
    <AddAnnotationDialog
      v-if="showAddAnnotationDialog && fileReferenceAppId"
      v-model:show-dialog="showAddAnnotationDialog"
      :annotated="
        new AnnotatedReference(fileReferenceAppId, 'FileReference')
      "
    />
  </div>
</template>
