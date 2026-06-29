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

            <v-row v-if="fileContentUrl">
              <v-col cols="12">
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
              </v-col>
            </v-row>

            <!-- Open as … — the VIEW_RECIPE picker (PR-1/2/3 ship the recipes
                 themselves; PR-4 hands the appId off to /shapes/render). -->
            <v-row>
              <v-col cols="12">
                <div class="text-subtitle-2 mb-2">Open as…</div>
                <ViewRecipePicker
                  :file-kind="fileReference.fileKind"
                  :focus-shepard-id="fileReferenceAppId"
                  @select="onPickRecipe"
                />
              </v-col>
            </v-row>

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
