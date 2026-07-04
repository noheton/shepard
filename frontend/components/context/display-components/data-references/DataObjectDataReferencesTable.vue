<script lang="ts" setup>
import DataObjectDataMetaCell from "./DataObjectDataMetaCell.vue";
import type { DataReference } from "./dataReference";
import type { DataTableElement } from "./dataTableElement";
import { mapDataReferenceToDataTableElement } from "./dataTableElementMappingUtil";
import { useManageGitReferences } from "~/composables/context/useManageGitReferences";
import { useJupyterConfig } from "~/composables/context/admin/useJupyterConfig";
import {
  usePromoteToSpatial,
  isSpatialEligibleName,
} from "~/composables/context/usePromoteToSpatial";

interface DataObjectDataReferencesTableProps {
  collectionId: number;
  dataObjectId: number;
  dataReferences: Array<DataReference>;
  isAllowedToEditCollection: boolean;
  /** REF-UNIFIED-TABLE: pre-mapped extra items for new-kind references (Git/Video/HDF5). */
  extraItems?: Array<DataTableElement>;
  /** AppId of the DataObject (required for delete actions on new kinds). */
  dataObjectAppId?: string;
}

const props = defineProps<DataObjectDataReferencesTableProps>();
const router = useRouter();

// ── Legacy-kind annotation dialog (now v2 appId path) ────────────────────────
// The "legacy" TimeSeries / File Bundle / Structured Data rows are annotated
// via the same v2 /v2/annotations surface as the new kinds — keyed by the
// reference's appId + concrete kind, not the dropped numeric id.
const selectedReferenceAppId = ref<string>("");
const selectedReferenceKind = ref<string>("DataObjectReference");
const showAddAnnotationDialog = ref(false);

function openAddAnnotationDialog(appId: string, kind: string) {
  selectedReferenceAppId.value = appId;
  selectedReferenceKind.value = kind;
  showAddAnnotationDialog.value = true;
}

function legacyKindFor(type: RefKind): string {
  if (type === "TimeSeries") return "TimeseriesReference";
  if (type === "File Bundle") return "FileReference";
  if (type === "Structured Data") return "StructuredDataReference";
  return "DataObjectReference";
}

// ── SEMA-V6 annotation dialog (appId path — new kinds) ───────────────────────
const showSemaAnnotationDialog = ref(false);
const semaAnnotationAppId = ref<string | undefined>(undefined);
const semaAnnotationKind = ref<string>("DataObjectReference");

function openSemaAnnotationDialog(appId: string, kind: string) {
  semaAnnotationAppId.value = appId;
  semaAnnotationKind.value = kind;
  showSemaAnnotationDialog.value = true;
}

// ── Navigation ────────────────────────────────────────────────────────────────
function showDetails(pathFragment: string, id: number | string) {
  const route =
    collectionsPath +
    props.collectionId +
    dataObjectsPathFragment +
    props.dataObjectId +
    pathFragment +
    id;
  router.push(route);
}

/**
 * REF-VIDEO-DETAIL-PAGE — pick the right identifier for the route segment.
 * Legacy kinds (TimeSeries / File Bundle / Structured Data) navigate by
 * numeric `elementId`; new kinds (Video, …) navigate by `elementAppId`
 * (UUID v7). The detail page on the other side decides which it expects.
 */
function showDetailsId(item: DataTableElement): number | string | undefined {
  if (item.actions.elementId != null) return item.actions.elementId;
  return item.actions.elementAppId;
}

// ── Delete actions for new kinds ──────────────────────────────────────────────
const { remove: removeGitRef } = useManageGitReferences();

const showDeleteDialog = ref(false);
const deleteTarget = ref<DataTableElement | null>(null);

function openDeleteDialog(item: DataTableElement) {
  deleteTarget.value = item;
  showDeleteDialog.value = true;
}

// ── REF-EDIT-4: rename dialog for FileBundleReference rows ───────────────────
const showEditBundleDialog = ref(false);
const editBundleTarget = ref<DataTableElement | null>(null);

function openEditBundleDialog(item: DataTableElement) {
  editBundleTarget.value = item;
  showEditBundleDialog.value = true;
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

const emit = defineEmits<{ (e: "refresh"): void }>();

// ── SPATIAL-UNIFY-004: in-context "Promote to spatial" on eligible File rows ──
const { promote: promoteToSpatial, isPromoting } = usePromoteToSpatial();

/** True when a File row's filename is an eligible pointcloud/trajectory. */
function isPromotableFile(item: DataTableElement): boolean {
  return item.type === "File" && isSpatialEligibleName(item.meta.filename ?? item.name);
}

async function onPromoteToSpatial(item: DataTableElement) {
  const appId = item.meta.appId;
  if (!appId) return;
  const ok = await promoteToSpatial(appId);
  if (ok) emit("refresh");
}

const router2 = useRouter();
/**
 * SPATIAL-UNIFY-005 — "View as pointcloud" opens the 3D viewer for a spatial
 * reference's backing container. The container appId is resolved from the
 * reference (the user never picks a container). Falls back to no-op when the
 * container hasn't materialised yet (promotion pending).
 */
function viewAsPointcloud(item: DataTableElement) {
  const containerAppId = item.meta.spatialContainerAppId;
  if (!containerAppId) return;
  router2.push(`/containers/spatialdata/${encodeURIComponent(containerAppId)}`);
}

async function confirmDelete() {
  const item = deleteTarget.value;
  if (!item || !props.dataObjectAppId) return;
  const appId = item.meta.appId;
  if (!appId) return;

  if (item.type === "Git") {
    const ok = await removeGitRef(props.dataObjectAppId, appId);
    if (ok) {
      showDeleteDialog.value = false;
      emit("refresh");
    }
    return;
  }

  // FR1b singletons + Spatial: V2CONV-A2 / SPATIAL-UNIFY DELETE
  // /v2/references/{appId} (unified surface). The kind is resolved from the
  // entity server-side, so spatial deletes through the same path.
  if (item.type === "File" || item.type === "Notebook" || item.type === "Spatial") {
    const accessToken = session.value?.accessToken;
    if (!accessToken) return;
    const url = `${v2BaseUrl()}/v2/references/${encodeURIComponent(appId)}`;
    try {
      const response = await fetch(url, {
        method: "DELETE",
        headers: { Authorization: `Bearer ${accessToken}` },
      });
      if (response.ok || response.status === 204) {
        showDeleteDialog.value = false;
        emit("refresh");
      } else {
        handleError(`HTTP ${response.status}`, `delete ${item.type} reference`);
      }
    } catch (err) {
      handleError(err, `delete ${item.type} reference`);
    }
    return;
  }

  // Video: raw fetch DELETE
  const accessToken = session.value?.accessToken;
  if (!accessToken) return;

  const kindPath = "video-stream-references";
  const url = `${v2BaseUrl()}/v2/data-objects/${encodeURIComponent(props.dataObjectAppId)}/${kindPath}/${encodeURIComponent(appId)}`;

  try {
    const response = await fetch(url, {
      method: "DELETE",
      headers: { Authorization: `Bearer ${accessToken}` },
    });
    if (response.ok || response.status === 204) {
      showDeleteDialog.value = false;
      emit("refresh");
    } else {
      handleError(`HTTP ${response.status}`, `delete ${item.type} reference`);
    }
  } catch (err) {
    handleError(err, `delete ${item.type} reference`);
  }
}

// ── Table data ────────────────────────────────────────────────────────────────
const legacyItems = computed(() =>
  props.dataReferences.map(mapDataReferenceToDataTableElement),
);

const allTableItems = computed<DataTableElement[]>(() => [
  ...legacyItems.value,
  ...(props.extraItems ?? []),
]);

// Per-kind filter chips above the table. "All" is the default; clicking a
// kind narrows the rows. Counts come from the un-filtered list so empty
// kinds still show (greyed) and users learn the data shape at a glance.
type RefKind = DataTableElement["type"];
const allKind = "All" as const;
type SelectedKind = typeof allKind | RefKind;
const selectedKind = ref<SelectedKind>(allKind);

const kindCounts = computed<Record<RefKind, number>>(() => {
  const counts: Record<RefKind, number> = {
    TimeSeries: 0,
    "Structured Data": 0,
    "File Bundle": 0,
    File: 0,
    Notebook: 0,
    Git: 0,
    Video: 0,
    Spatial: 0,
  };
  for (const item of allTableItems.value) counts[item.type]++;
  return counts;
});

const tableItems = computed(() =>
  selectedKind.value === allKind
    ? allTableItems.value
    : allTableItems.value.filter(item => item.type === selectedKind.value),
);

const kindIcons: Record<RefKind, string> = {
  TimeSeries: "mdi-chart-line",
  "Structured Data": "mdi-code-json",
  "File Bundle": "mdi-file-multiple-outline",
  // REF-UNIFIED-TABLE-FR1B / J1c retirement
  File: "mdi-file-outline",
  Notebook: "mdi-notebook-outline",
  Git: "mdi-git",
  Video: "mdi-video-outline",
  Spatial: "mdi-cube-scan",
};
const KIND_ORDER: RefKind[] = [
  "TimeSeries",
  "Structured Data",
  "File Bundle",
  "File",
  "Notebook",
  "Git",
  "Video",
  "Spatial",
];

const headers = [
  {
    title: "Type",
    value: "type",
    sort: (a: string, b: string) => a.localeCompare(b),
  },
  {
    title: "Name",
    value: "name",
    sort: (a: string, b: string) => a.localeCompare(b),
  },
  {
    title: "Meta",
    value: "meta",
    sort: (
      a: DataTableElement["meta"],
      b: DataTableElement["meta"],
    ) => (a.id ?? 0) - (b.id ?? 0),
  },
  {
    title: "Created",
    value: "created",
    sort: (a: DataTableElement["created"], b: DataTableElement["created"]) =>
      a.createdAt.valueOf() - b.createdAt.valueOf(),
  },
  {
    title: "",
    value: "actions",
  },
];

const itemsPerPage = 10;

/** True for new-kind rows that have appId but no detail page yet. */
const NEW_KINDS: ReadonlySet<RefKind> = new Set(["File", "Notebook", "Git", "Video", "Spatial"]);

/** True for kinds whose download URL is the FR1b singleton content endpoint. */
const SINGLETON_FILE_KINDS: ReadonlySet<RefKind> = new Set(["File", "Notebook"]);

function semaKindFor(type: RefKind): string {
  if (type === "Git") return "GitReference";
  if (type === "Video") return "VideoStreamReference";
  if (type === "Spatial") return "SpatialDataReference";
  if (type === "File" || type === "Notebook") return "FileReference";
  return "DataObjectReference";
}

// ── JupyterHub link-out (J1e) ────────────────────────────────────────────
//
// The "Open in JupyterHub" action on Notebook rows is visible only when both
// `enabled === true` AND `hubUrl != null && hubUrl !== ""`. Reads the public
// /v2/jupyter/config endpoint so non-admin users can see (or not see) the
// affordance without a 403.
const { config: jupyterConfig } = useJupyterConfig();
const jupyterAffordanceVisible = computed(
  () =>
    !!jupyterConfig.value &&
    jupyterConfig.value.enabled === true &&
    !!jupyterConfig.value.hubUrl &&
    jupyterConfig.value.hubUrl.length > 0,
);

/**
 * Build the JupyterHub launch URL for a singleton FileReference appId.
 * Follows the `{hubUrl}/hub/spawn?file={downloadUrl}` shape — see
 * docs/admin/runbooks/jupyterhub-config.md for the JupyterHub-side
 * convention map (nbgitpuller / `?fromURL=` / `/user-redirect/`).
 *
 * Returns null when the affordance gate is closed.
 */
function jupyterLaunchUrl(appId: string): string | null {
  const cfg = jupyterConfig.value;
  if (!cfg || !cfg.enabled || !cfg.hubUrl) return null;
  const downloadUrl = `${v2BaseUrl()}/v2/references/${encodeURIComponent(appId)}/content`;
  const hubBase = cfg.hubUrl.replace(/\/$/, "");
  return `${hubBase}/hub/spawn?file=${encodeURIComponent(downloadUrl)}`;
}

/** Build the direct download URL for a FR1b singleton appId. */
function singletonDownloadUrl(appId: string): string {
  return `${v2BaseUrl()}/v2/references/${encodeURIComponent(appId)}/content`;
}

/** Format a byte count as B / KB / MB / GB. */
function formatBytes(bytes: number | null | undefined): string {
  if (bytes == null) return "—";
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  if (bytes < 1024 * 1024 * 1024) return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
  return `${(bytes / (1024 * 1024 * 1024)).toFixed(2)} GB`;
}

/** Format duration in seconds as MM:SS or H:MM:SS. */
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
</script>

<template>
  <!-- Kind summary + filter chips. Always rendered (even when empty) so the
       data shape is communicated at a glance. -->
  <div class="d-flex flex-wrap align-center ga-2 pb-3">
    <v-chip
      :variant="selectedKind === 'All' ? 'flat' : 'tonal'"
      :color="selectedKind === 'All' ? 'primary' : undefined"
      size="small"
      @click="selectedKind = 'All'"
    >
      All ({{ allTableItems.length }})
    </v-chip>
    <v-chip
      v-for="kind in KIND_ORDER"
      :key="kind"
      :variant="selectedKind === kind ? 'flat' : 'tonal'"
      :color="selectedKind === kind ? 'primary' : undefined"
      :disabled="kindCounts[kind] === 0"
      size="small"
      :prepend-icon="kindIcons[kind]"
      @click="selectedKind = kind"
    >
      {{ kind }} ({{ kindCounts[kind] }})
    </v-chip>
  </div>

  <EmptyListIcon v-if="tableItems.length === 0" label="No data yet" />
  <div v-else style="overflow-x: auto">
    <DataTable
      :headers="headers"
      :items-for-pagination="tableItems"
      :items-per-page="itemsPerPage"
    >
      <!--
        User feedback 2026-05-18: the eye-icon on hover was the only path
        to drill in. Title + Type are now clickable links — the eye-icon
        lives on as a tertiary action that mirrors the navigation, useful
        for keyboard / accessible users who don't trigger row-hover.
      -->
      <template #[`item.type`]="{ item }: { item: DataTableElement }">
        <a
          v-if="item.actions.showDetails.enabled && showDetailsId(item) != null"
          href="#"
          class="reference-link d-inline-flex align-center"
          @click.prevent="showDetails(item.actions.showDetails.pathFragment, showDetailsId(item)!)"
        >
          <v-icon :icon="kindIcons[item.type]" size="small" class="me-1" />
          {{ item.type }}
        </a>
        <span v-else class="d-inline-flex align-center">
          <v-icon :icon="kindIcons[item.type]" size="small" class="me-1" />
          {{ item.type }}
        </span>
      </template>

      <template #[`item.name`]="{ item }: { item: DataTableElement }">
        <a
          v-if="item.actions.showDetails.enabled && showDetailsId(item) != null"
          href="#"
          class="reference-link"
          @click.prevent="showDetails(item.actions.showDetails.pathFragment, showDetailsId(item)!)"
        >
          {{ item.name }}
        </a>
        <template v-else-if="item.type === 'Git' && item.meta.repoUrl">
          <a
            :href="item.meta.repoUrl"
            target="_blank"
            rel="noopener"
            class="reference-link"
          >{{ item.name }}</a>
          <div v-if="item.meta.gitRef" class="text-caption text-medium-emphasis mt-1">
            <v-icon size="x-small">mdi-source-branch</v-icon>
            {{ item.meta.gitRef }}
            <span v-if="item.meta.gitPath"> · {{ item.meta.gitPath }}</span>
          </div>
        </template>
        <span v-else>{{ item.name }}</span>
      </template>

      <template #[`item.meta`]="{ item, value }: { item: DataTableElement; value: DataTableElement['meta'] }">
        <!-- Legacy kinds: use the existing meta cell + annotation list -->
        <template v-if="!NEW_KINDS.has(item.type)">
          <DataObjectDataMetaCell :meta="value" />
          <SemanticAnnotationList
            v-if="value.appId"
            :key="value.appId"
            :can-delete="isAllowedToEditCollection"
            :limit="4"
            :annotated="
              new AnnotatedReference(value.appId, legacyKindFor(item.type))
            "
          />
        </template>
        <!-- New kinds: show kind-specific meta chips -->
        <template v-else>
          <div class="d-flex flex-wrap ga-1">
            <!-- Video meta -->
            <v-chip
              v-if="item.type === 'Video' && value.durationSeconds != null"
              size="x-small"
              variant="tonal"
              prepend-icon="mdi-timer-outline"
            >
              {{ formatDuration(value.durationSeconds) }}
            </v-chip>
            <v-chip
              v-if="item.type === 'Video' && value.resolution"
              size="x-small"
              variant="tonal"
              prepend-icon="mdi-monitor-screenshot"
            >
              {{ value.resolution }}
            </v-chip>
            <!-- REF-UNIFIED-TABLE-FR1B: filename + size on File / Notebook rows -->
            <v-chip
              v-if="(item.type === 'File' || item.type === 'Notebook') && value.filename"
              size="x-small"
              variant="tonal"
              prepend-icon="mdi-file-document-outline"
              :data-testid="`fr1b-filename-${item.meta.appId}`"
            >
              {{ value.filename }}
            </v-chip>
            <v-chip
              v-if="(item.type === 'File' || item.type === 'Notebook') && value.fileSize != null"
              size="x-small"
              variant="tonal"
              prepend-icon="mdi-database-outline"
            >
              {{ formatBytes(value.fileSize) }}
            </v-chip>
          </div>
        </template>
      </template>

      <template
        #[`item.created`]="{ value }: { value: DataTableElement['created'] }"
      >
        <!-- Show "—" for new kinds where createdAt is the fallback epoch date -->
        <span v-if="value.createdAt.valueOf() === 0" class="text-medium-emphasis">—</span>
        <CreatedTableCell
          v-else
          :created-at="value.createdAt"
          :created-by="value.createdBy"
        />
      </template>

      <template
        #[`item.actions`]="{ item }: { item: DataTableElement }"
      >
        <ActionContainer>
          <!-- Legacy kinds: navigate to detail page -->
          <ActionButton
            v-if="item.actions.showDetails.enabled && showDetailsId(item) != null"
            icon="mdi-eye-outline"
            @click="() => showDetails(item.actions.showDetails.pathFragment, showDetailsId(item)!)"
          />
          <!-- Legacy kinds: annotation dialog (v2 appId path) -->
          <ActionButton
            v-if="isAllowedToEditCollection && !NEW_KINDS.has(item.type) && item.meta.appId"
            icon="mdi-tag-outline"
            @click="() => openAddAnnotationDialog(item.meta.appId!, legacyKindFor(item.type))"
          />
          <!-- REF-EDIT-4: rename action for FileBundleReference rows -->
          <ActionButton
            v-if="isAllowedToEditCollection && item.type === 'File Bundle' && item.meta.appId"
            icon="mdi-pencil-outline"
            aria-label="Rename file bundle reference"
            @click="() => openEditBundleDialog(item)"
          />
          <!-- New kinds: SEMA-V6 annotation dialog -->
          <ActionButton
            v-if="isAllowedToEditCollection && NEW_KINDS.has(item.type) && item.meta.appId"
            icon="mdi-tag-outline"
            @click="() => openSemaAnnotationDialog(item.meta.appId!, semaKindFor(item.type))"
          />
          <!-- REF-UNIFIED-TABLE-FR1B: download for singleton File / Notebook -->
          <v-btn
            v-if="SINGLETON_FILE_KINDS.has(item.type) && item.meta.appId"
            :href="singletonDownloadUrl(item.meta.appId)"
            target="_blank"
            rel="noopener noreferrer"
            variant="text"
            density="comfortable"
            size="small"
            icon="mdi-download-outline"
            aria-label="Download"
            :data-testid="`fr1b-download-${item.meta.appId}`"
          />
          <!--
            J1e: Open in JupyterHub action — visible only on Notebook
            rows when the admin-configured affordance gate is open
            (enabled === true AND hubUrl != null). Hidden otherwise so
            the row degrades gracefully to plain download + delete.
          -->
          <v-btn
            v-if="item.type === 'Notebook' && item.meta.appId && jupyterAffordanceVisible"
            :href="jupyterLaunchUrl(item.meta.appId) ?? '#'"
            target="_blank"
            rel="noopener noreferrer"
            variant="flat"
            color="warning"
            density="comfortable"
            size="small"
            prepend-icon="mdi-jupyter"
            :data-testid="`jupyter-launch-${item.meta.appId}`"
          >
            Open in JupyterHub
          </v-btn>
          <!-- SPATIAL-UNIFY-004: in-context "Promote to spatial" on eligible File rows -->
          <v-btn
            v-if="isAllowedToEditCollection && isPromotableFile(item) && item.meta.appId"
            variant="text"
            density="comfortable"
            size="small"
            color="primary"
            prepend-icon="mdi-cube-scan"
            :loading="isPromoting"
            :data-testid="`promote-spatial-${item.meta.appId}`"
            @click="() => onPromoteToSpatial(item)"
          >
            Promote to spatial
          </v-btn>
          <!-- SPATIAL-UNIFY-005: "View as pointcloud" on Spatial rows (in-context viewer entry) -->
          <v-btn
            v-if="item.type === 'Spatial' && item.meta.spatialContainerAppId"
            variant="text"
            density="comfortable"
            size="small"
            prepend-icon="mdi-rotate-3d"
            :data-testid="`view-pointcloud-${item.meta.appId}`"
            @click="() => viewAsPointcloud(item)"
          >
            View as pointcloud
          </v-btn>
          <!-- SPATIAL-UNIFY: "promoting…" chip while the importer streams points -->
          <v-chip
            v-if="item.type === 'Spatial' && item.meta.promotionState === 'pending'"
            size="x-small"
            variant="tonal"
            color="info"
            prepend-icon="mdi-progress-clock"
          >
            promoting…
          </v-chip>
          <!-- New kinds: delete action -->
          <ActionButton
            v-if="isAllowedToEditCollection && NEW_KINDS.has(item.type)"
            icon="mdi-delete-outline"
            color="error"
            @click="() => openDeleteDialog(item)"
          />
        </ActionContainer>
      </template>
    </DataTable>
  </div>

  <!-- Legacy-kind annotation dialog (v2 appId path) -->
  <AddAnnotationDialog
    v-if="showAddAnnotationDialog && selectedReferenceAppId"
    v-model:show-dialog="showAddAnnotationDialog"
    :annotated="
      new AnnotatedReference(selectedReferenceAppId, selectedReferenceKind)
    "
  />

  <!-- SEMA-V6 annotation dialog (appId path, new kinds) -->
  <AnnotationDialog
    v-if="showSemaAnnotationDialog && semaAnnotationAppId"
    v-model:show-dialog="showSemaAnnotationDialog"
    :subject-app-id="semaAnnotationAppId"
    :subject-kind="semaAnnotationKind"
  />

  <!-- Delete confirmation for new-kind rows -->
  <ConfirmDeleteDialog
    v-if="showDeleteDialog && deleteTarget"
    v-model:show-dialog="showDeleteDialog"
    :prompt-text="`Delete ${deleteTarget.type} reference to ${deleteTarget.name}?`"
    @confirmed="confirmDelete"
  />

  <!-- REF-EDIT-4: rename dialog for FileBundleReference -->
  <EditFileBundleReferenceDialog
    v-if="showEditBundleDialog && editBundleTarget?.meta.appId"
    v-model:show-dialog="showEditBundleDialog"
    :file-bundle-reference-app-id="editBundleTarget.meta.appId!"
    :current-name="editBundleTarget.name"
    @saved="() => emit('refresh')"
  />
</template>

<style scoped>
.reference-link {
  color: rgb(var(--v-theme-primary));
  text-decoration: none;
  cursor: pointer;
}
.reference-link:hover {
  text-decoration: underline;
}
</style>
