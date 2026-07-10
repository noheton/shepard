<script lang="ts" setup>
import DataObjectFileUpload from "~/components/context/data-object/upload-data/DataObjectFileUpload.vue";
// REF-UNIFIED-TABLE: GitReferencesPane, VideoStreamReferencesPane, HdfReferencesPane removed
// from this page; they now live in frontend/components/context/dataobject/legacy/ for reuse.
// J1c retirement (2026-05-29): DataObjectNotebooksPane is gone too — notebooks
// now appear as rows in the unified data-references table with a notebook icon
// and a per-row "Open in JupyterHub" action (J1e).
import AddRelationshipDialog from "~/components/context/display-components/relationships/add-dialog/AddRelationshipDialog.vue";
import PublishButton from "~/components/context/publish/PublishButton.vue";
import PublicationStatusBadge from "~/components/context/publish/PublicationStatusBadge.vue";
import { collectionsPath, dataObjectsPathFragment } from "~/utils/constants";
import { resolveNumericId } from "~/utils/collectionRouteParams";
import { useFetchTypedPredecessors } from "~/composables/context/useFetchTypedPredecessors";
import { useAdvancedMode } from "~/composables/context/useAdvancedMode";
import AncestorChainPanel from "~/components/context/data-object/AncestorChainPanel.vue";
import EntityToolsMenu from "~/components/context/tools/EntityToolsMenu.vue";
// FORM-UX-ACTIONBUTTON — unified "View as … / Record a …" entry point fed by
// GET /v2/shapes/applicable; absorbs the former Tools-menu "Render view" item.
import ActionMenuButton from "~/components/context/tools/ActionMenuButton.vue";
// UX612-M1 — resolve the attached template's kind so the Tools menu can gate
// "Render view" on VIEW_RECIPE (the only kind /v2/shapes/render accepts).
// TEMPLATE-ICONS-2-FE-RENDER-POINTS-EXPAND — also store the full template so
// the header can show the template's iconKey.
import { TemplatesApi, type ShepardTemplate } from "@dlr-shepard/backend-client";
import { useTemplateIcon } from "~/composables/useTemplateIcon";
import { useV2ShepardApi } from "~/composables/common/api/useV2ShepardApi";
import { useFetchGitReferences } from "~/composables/context/useFetchGitReferences";
import { useFetchVideoStreamReferences } from "~/composables/context/useFetchVideoStreamReferences";
import { useFetchSingletonFileReferences } from "~/composables/context/useFetchSingletonFileReferences";
// MFFD-NDT-QUALITY-1 — thermography NDT pane mounts conditionally when the
// DO carries at least one FileBundleReference whose name suggests thermal
// imagery. Pane is self-contained (fetches its own cached plate-heatmap +
// quality summary); we only need to discover the bundle's appId here.
import DataObjectThermographyPane from "~/components/context/thermography/DataObjectThermographyPane.vue";
// MFFD-IMAGEBUNDLE-PANE-MOUNT-1 — generic image-bundle frame scrubber. Mounts
// when the DO carries at least one FileBundleReference whose first group
// contains image-extension files (.png, .jpg, .jpeg, .tif, .tiff, …).
// Self-detecting: passes candidateBundleAppIds from the v1 dataReferences list;
// the pane calls GET /v2/bundles/{appId} internally for detection.
import DataObjectImageBundlePane from "~/components/context/data-object/DataObjectImageBundlePane.vue";
// OTVIS-VIEWER — decoded Edevis OTvis amplitude/phase frame viewer. Mounts
// when the DO carries a singleton FileReference whose filename ends `.OTvis`.
// In-context-first entry per the tools rule: the appId is already in hand on
// this page, so the viewer pulls bytes from the reference (never a path/URL).
import DataObjectOtvisViewer from "~/components/context/thermography/DataObjectOtvisViewer.vue";
import type { SingletonFileReferenceIO } from "~/composables/context/useFetchSingletonFileReferences";
// MFFD-RENDER-MATERIAL-BATCH-TRACE (slice 3) — in-context lineage pane for
// mffd:material-batch DataObjects. Mounts only when isMaterialBatchDo is true.
import MaterialBatchTracePane from "~/components/context/mffd/MaterialBatchTracePane.vue";
// MFFD-MULTIPLAYER-1 — synchronised multi-payload player. Mounts when the
// DO carries ≥ 2 distinct payload kinds; renders a shared scrubber + a
// per-kind tile grid all bound to one `useSyncedTimeCursor` instance.
import MultiPlayerPane from "~/components/context/multiplayer/MultiPlayerPane.vue";
import { selectMultiPlayerTiles } from "~/utils/multiPlayerTiles";
// MISSING-aas-ui Slice 4: in-context AAS submodel identity pane. Shows the
// DataObject's role as an IDTA AAS Submodel when the AAS plugin is enabled.
import DataObjectAasPane from "~/components/context/aas/DataObjectAasPane.vue";
import {
  useFetchSpatialReferencesV2,
  type SpatialReferenceV2IO,
} from "~/composables/context/useFetchSpatialReferencesV2";
import {
  mapGitReferenceToDataTableElement,
  mapSingletonFileReferenceToDataTableElement,
  mapVideoReferenceToDataTableElement,
  mapSpatialReferenceToDataTableElement,
} from "~/components/context/display-components/data-references/dataTableElementMappingUtil";
import type { DataTableElement } from "~/components/context/display-components/data-references/dataTableElement";
import type { DataReference } from "~/components/context/display-components/data-references/dataReference";
import type { RelatedEntity } from "~/components/context/display-components/relationships/relatedEntity";

definePageMeta({ layout: "collection" });

const { routeParams } = useCollectionRouteParams();

// BUG-COLL-APPID-ROUTE-002 / -007 / -007-PAGE + V2-SWEEP Wave 3:
// useFetchCollection + useFetchDataObject hit the v2 appId-keyed endpoints
// with the route param strings, and the inline edits PATCH the v2 endpoint
// directly (see patchDataObjectV2 below). The remaining numeric-id
// consumers (useDataReferencesByDataObject, useRelatedEntities,
// the AnnotatedDataObject CRUD, and
// the v1 child panels) are documented exceptions — they need the NUMERIC
// ids which only the loaded v2 entities carry (resolved reactively below;
// loaded id wins, numeric-route-param fallback for legacy /collections/123
// deep links — never a UUID coerced into a v1 call). Backlog rows:
// REFS-V2-PANELS / ANNOT-V2 in aidocs/16. The page body only renders once
// both entities load, so within the template `collection.id` /
// `dataObject.id` are the canonical numeric source.
const collectionIdStr = routeParams.value.collectionId ?? "";
const dataObjectIdStr = routeParams.value.dataObjectId ?? "";

const {
  collection,
  isAllowedToEditCollection,
  notFound: isCollectionNotFound,
} = useFetchCollection(collectionIdStr);
const {
  dataObject,
  isLoading: isDataObjectLoading,
  notFound: isDataObjectNotFound,
} = useFetchDataObject(collectionIdStr, dataObjectIdStr);

const collectionNumericId = computed<number | undefined>(() =>
  resolveNumericId(collection.value?.id, routeParams.value.collectionId),
);
const dataObjectNumericId = computed<number | undefined>(() =>
  resolveNumericId(dataObject.value?.id, routeParams.value.dataObjectId),
);

// REFS-V2-PANELS: the reference fetch is now appId-keyed (the v2 unified
// /v2/references endpoint). Prefer the loaded DataObject's appId; fall back to
// the route param (which IS the appId in the v2 routing) so the fetch can fire
// before the entity resolves. Never a numeric id — that was the empty-panel bug.
const dataObjectAppIdForRefs = computed<string | undefined>(
  () => dataObject.value?.appId ?? routeParams.value.dataObjectId ?? undefined,
);
const { dataReferences } = useDataReferencesByDataObject(
  dataObjectAppIdForRefs,
);
// REFS-V2-PANELS-3: collection appId for v2 predecessor/successor fetch.
// Same fallback pattern as dataObjectAppIdForRefs above.
const collectionAppIdForRefs = computed<string | undefined>(
  () => collection.value?.appId ?? (collectionIdStr.includes("-") ? collectionIdStr : undefined),
);
const { relatedEntities } = useRelatedEntities(
  collectionNumericId,
  dataObjectNumericId,
  dataObjectIdStr, // V2-SWEEP-004-2: route param UUID → v2 endpoint returns referencedCollectionAppId
  collectionAppIdForRefs, // REFS-V2-PANELS-3: v2 predecessor/successor endpoint
);

// BUG-DO-DETAIL-HANG (2026-06-13): the page render gate previously required
// `dataReferences` AND `relatedEntities` to be non-undefined, but those v1
// reference composables only flip from `undefined` once the NUMERIC ids
// resolve from the loaded v2 entities. When a reference sub-fetch 403/404s, or
// the numeric id never resolves, those refs stayed `undefined` forever → the
// CenteredLoadingSpinner never cleared (the lone CRITICAL in the 2026-06-12 /
// -13 UX audits). Reference panels are NOT required entities — a DataObject
// with zero git/spatial/file references is a normal empty state, not a fatal.
// The render gate below now requires only `collection && dataObject`; these
// safe-default views let the per-kind tables render an empty state while their
// own fetches resolve (or fail soft) independently.
const dataReferencesSafe = computed<DataReference[]>(
  () => dataReferences.value ?? [],
);
const relatedEntitiesSafe = computed<RelatedEntity[]>(
  () => relatedEntities.value ?? [],
);

// BUG-DO-DETAIL-HANG: the only fatal is the required DataObject failing to
// load with a non-404 status (403 / 500 / network). Previously such a failure
// left `dataObject` undefined + `notFound` false, so the page hung on the
// spinner. We surface a settled-but-failed flag so the template can render the
// EntityNotFound fallback instead of spinning forever. A 404 is already handled
// by `isDataObjectNotFound`.
const dataObjectLoadFailed = computed<boolean>(
  () =>
    !isDataObjectLoading.value &&
    !dataObject.value &&
    !isDataObjectNotFound.value,
);

// REF-UNIFIED-TABLE: extra items for Git/Video/HDF5 reference kinds.
// These composables are set up once dataObject.appId is available, since
// the new-kind endpoints use appId (string) rather than numeric id.
const extraReferenceItems = ref<DataTableElement[]>([]);

// Hold refs to sub-composable refresh functions so the panel can request re-fetch.
const refreshGitRefs = ref<(() => void) | null>(null);
const refreshVideoRefs = ref<(() => void | Promise<void>) | null>(null);
// REF-UNIFIED-TABLE-FR1B / J1c retirement: FR1b singletons (incl. notebooks)
// fetched via the additive /v2/files/by-data-object endpoint.
const refreshFr1bRefs = ref<(() => void | Promise<void>) | null>(null);
// SPATIAL-UNIFY-003: spatial references fetched via /v2/references?kind=spatial.
const refreshSpatialRefs = ref<(() => void | Promise<void>) | null>(null);

// MFFD-MULTIPLAYER-1 — surface the video-reference count outside the
// watcher's closure so the multi-player tile gate (selectMultiPlayerTiles)
// can react to it.
const videoReferenceCount = ref<number>(0);

// REFS-V2-PANELS-1 — hoist the v2 spatial references outside the watcher so
// the multi-player tile gate (hasSpatial) can read them. Populated by the
// watchEffect below once dataObject.appId resolves; no v1 numeric-id needed.
const spatialRefsV2 = ref<SpatialReferenceV2IO[]>([]);

// OTVIS-VIEWER — raw singleton FileReferences, surfaced outside the watcher's
// closure so the OTvis-viewer gate (otvisReferences) can filter on filename.
const singletonFileRefs = ref<SingletonFileReferenceIO[]>([]);

watch(
  () => dataObject.value?.appId,
  (appId) => {
    if (!appId) return;

    const gitComposable = useFetchGitReferences(appId);
    refreshGitRefs.value = gitComposable.refresh;

    const videoComposable = useFetchVideoStreamReferences(appId);
    refreshVideoRefs.value = videoComposable.refresh;

    const fr1bComposable = useFetchSingletonFileReferences(appId);
    refreshFr1bRefs.value = fr1bComposable.refresh;

    const spatialComposable = useFetchSpatialReferencesV2(appId);
    refreshSpatialRefs.value = spatialComposable.refresh;

    // Reactively derive extraReferenceItems from the composable states.
    watchEffect(() => {
      extraReferenceItems.value = [
        ...gitComposable.gitReferences.value.map(mapGitReferenceToDataTableElement),
        ...videoComposable.references.value.map(mapVideoReferenceToDataTableElement),
        ...fr1bComposable.references.value.map(mapSingletonFileReferenceToDataTableElement),
        ...spatialComposable.references.value.map(mapSpatialReferenceToDataTableElement),
      ];
      // MFFD-MULTIPLAYER-1: keep the video count in step with the composable.
      videoReferenceCount.value = videoComposable.references.value.length;
      // OTVIS-VIEWER: surface the raw singletons for the .OTvis filter below.
      singletonFileRefs.value = fr1bComposable.references.value;
      // REFS-V2-PANELS-1: hoist spatial refs so the multiPlayer gate can read them.
      spatialRefsV2.value = spatialComposable.references.value;
    });
  },
  { immediate: true },
);

/**
 * OTVIS-VIEWER — singleton FileReferences whose attached file is an `.OTvis`
 * archive. Each gets its own viewer panel (multiple NDT scans per process
 * step are common). Cheap string-suffix check on already-fetched refs.
 */
const otvisReferences = computed<SingletonFileReferenceIO[]>(() =>
  singletonFileRefs.value.filter(r =>
    (r.file?.filename ?? "").toLowerCase().endsWith(".otvis"),
  ),
);

function refreshExtraReferences() {
  refreshGitRefs.value?.();
  refreshVideoRefs.value?.();
  refreshFr1bRefs.value?.();
  refreshSpatialRefs.value?.();
}

/** Total count for the "Data References" panel badge: legacy + new kinds. */
const totalReferenceCount = computed(
  () => (dataReferences.value?.length ?? 0) + extraReferenceItems.value.length,
);

/**
 * MFFD-NDT-QUALITY-1 — discover a thermography FileBundleReference on this
 * DataObject. Heuristic: name contains "thermo" / "ndt" / "tif" (case-
 * insensitive). The first matching bundle wins (DataObjects usually carry
 * one thermography region per process step). Returns the bundle's appId
 * when found, null otherwise.
 *
 * <p>The detection is local + cheap (string match on already-fetched refs);
 * the pane itself stays unmounted until a match exists, so DOs without
 * thermography pay no cost.
 */
const thermographyBundleAppId = computed<string | null>(() => {
  const refs = dataReferences.value ?? [];
  for (const r of refs) {
    if (!("fileReferenceId" in r || "fileContainerId" in r)) continue;
    const name = ((r as { name?: string }).name ?? "").toLowerCase();
    if (
      name.includes("thermo") ||
      name.includes("ndt") ||
      name.includes(".tif")
    ) {
      // FileBundleReference exposes its appId on the underlying entity.
      const appId = (r as { appId?: string | null }).appId;
      if (appId) return appId;
    }
  }
  return null;
});

/**
 * MFFD-IMAGEBUNDLE-PANE-MOUNT-1 — collect appIds of FileBundleReferences from
 * the v1 dataReferences list. FileBundleReferences carry a `fileContainerId`
 * field (distinguishing them from TimeseriesReferences / StructuredDataRefs).
 * The pane itself calls GET /v2/bundles/{appId} to detect whether the bundle
 * actually contains image-extension files.
 */
const imageBundleCandidateAppIds = computed<string[]>(() => {
  const refs = dataReferences.value ?? [];
  return refs
    .filter(r => "fileContainerId" in r)
    .map(r => (r as { appId?: string | null }).appId)
    .filter((id): id is string => !!id);
});

/**
 * MFFD-MULTIPLAYER-1 — pick the tiles to render in the synchronised
 * multi-payload player. Returns an empty array when fewer than 2 distinct
 * payload kinds are present; the panel hides itself in that case.
 */
const hasTimeseriesReference = computed<boolean>(() => {
  const refs = dataReferences.value ?? [];
  return refs.some(r => "timeseriesContainerId" in r);
});

const multiPlayerTiles = computed(() =>
  selectMultiPlayerTiles({
    hasTimeseries: hasTimeseriesReference.value,
    hasVideo: videoReferenceCount.value > 0,
    thermographyBundleAppId: thermographyBundleAppId.value,
    hasSpatial: spatialRefsV2.value.length > 0,
  }),
);

// PROV1k: fetch typed predecessor summaries from the v2 detail endpoint.
// Best-effort: empty when the DataObject has no typed predecessors or backend
// predates PROV1k. We watch dataObject.appId + collection.appId reactively.
const typedPredecessorsRef = computed(() => {
  if (!collection.value?.appId || !dataObject.value?.appId) return null;
  return { collectionAppId: collection.value.appId, dataObjectAppId: dataObject.value.appId };
});
const typedPredecessors = ref<
  Array<{
    predecessorAppId: string;
    /** @deprecated Join on predecessorAppId instead. */
    predecessorId?: number;
    predecessorName: string;
    predecessorStatus: string | null;
    relationshipType: string;
  }>
>([]);
watch(
  typedPredecessorsRef,
  async val => {
    if (!val) return;
    const { typedPredecessors: tpFetched } = useFetchTypedPredecessors(
      val.collectionAppId,
      val.dataObjectAppId,
    );
    watch(tpFetched, v => { typedPredecessors.value = v; }, { immediate: true });
  },
  { immediate: true },
);

/** PROV1k: Map of predecessor appId (UUID v7) → relationship type, passed to DataObjectRelationshipsTable. */
const predecessorRelationshipTypesMap = computed<Map<string, string>>(() => {
  const map = new Map<string, string>();
  for (const tp of typedPredecessors.value) {
    // Only add non-default types to keep the map sparse; the chip component handles null gracefully.
    if (tp.predecessorAppId && tp.relationshipType && tp.relationshipType !== "prov:wasInformedBy") {
      map.set(tp.predecessorAppId, tp.relationshipType);
    }
  }
  return map;
});
const {
  counter: numberOfLabJournalEntries,
  updateCount: onLabJournalCountChanged,
} = useCounter();
// J1c retirement (2026-05-29): the dedicated notebooks panel + its counter
// are gone. Notebooks now live as rows in the unified data-references table.
const numberOfSemanticAnnotations = ref<number | undefined>(undefined);
// MFFD-RENDER-MATERIAL-BATCH-TRACE (slice 3): detect mffd:material-batch DOs
// from the semantic annotation list. We track the full array (not just the
// count) so the batch-trace pane can mount conditionally without a second fetch.
const loadedAnnotations = ref<Array<{ propertyIRI?: string }>>([]);
function onAnnotationsLoaded(annotations: Array<{ propertyIRI?: string }>) {
  numberOfSemanticAnnotations.value = annotations.length;
  loadedAnnotations.value = annotations;
}
/** True when this DataObject carries the urn:shepard:mffd:batch-id predicate. */
const isMaterialBatchDo = computed<boolean>(() =>
  loadedAnnotations.value.some(
    a => a.propertyIRI === "urn:shepard:mffd:batch-id",
  ),
);
// V2-SWEEP Wave 3: inline edits PATCH the v2 appId-keyed endpoint
// (RFC 7396 merge-patch — only the changed fields go on the wire).
// Mirrors useEditDataObject.ts; the route params are the appIds.
function v2BaseUrl(): string {
  const config = useRuntimeConfig().public;
  const explicit = config.backendV2ApiUrl as string | undefined;
  if (explicit && explicit.length > 0) return explicit.replace(/\/$/, "");
  return (config.backendApiUrl as string)
    .replace(/\/shepard\/api\/?$/, "")
    .replace(/\/$/, "");
}

async function patchDataObjectV2(body: Record<string, unknown>): Promise<void> {
  const { data: session } = useAuth();
  const accessToken = session.value?.accessToken;
  const url =
    `${v2BaseUrl()}/v2/collections/` +
    `${encodeURIComponent(collectionIdStr)}/data-objects/` +
    `${encodeURIComponent(dataObjectIdStr)}`;
  const resp = await fetch(url, {
    method: "PATCH",
    headers: {
      "Content-Type": "application/merge-patch+json",
      Accept: "application/json",
      ...(accessToken ? { Authorization: `Bearer ${accessToken}` } : {}),
    },
    body: JSON.stringify(body),
  });
  if (!resp.ok) throw new Error(`HTTP ${resp.status}`);
}

const showAttributeEditDialog = ref(false);
const showCreateDataReferenceDialog = ref(false);
const showAddRelationshipDialog = ref(false);

// SEMA-V6-017: unified "Annotate" button replaces the legacy "Add annotation" button.
// Both basic and advanced mode show this button (superset rule — never hide basic content).
// Advanced mode additionally shows the legacy "Add raw attribute" button with deprecation note.
const showAnnotationDialog = ref(false);
const { advancedMode } = useAdvancedMode();

// Provenance sub-view: structured log (default) vs force-directed graph.
const provView = ref<"log" | "graph">("log");

// ── Inline description editing ────────────────────────────────────────────
const descEditActive = ref(false);
const descDraft = ref("");
const descStatusDraft = ref<string | null>(null);
const descSaving = ref(false);

function startDescEdit() {
  descDraft.value = dataObject.value?.description ?? "";
  descStatusDraft.value = dataObject.value?.status ?? null;
  descEditActive.value = true;
}

function cancelDescEdit() {
  descEditActive.value = false;
}

async function saveDescEdit() {
  if (!dataObject.value) return;
  descSaving.value = true;
  try {
    // V2-SWEEP Wave 3: appId-keyed merge-patch — replaces the v1 full-body
    // updateDataObject keyed on numeric ids.
    await patchDataObjectV2({
      description: descDraft.value,
      status: descStatusDraft.value ?? null,
    });
    emitSuccess(`Description updated`);
    handleDataObjectUpdate();
    descEditActive.value = false;
  } catch (e) {
    handleError(e, "updating description");
  } finally {
    descSaving.value = false;
  }
}

// UX Pattern F (2026-05-24): reactive title with collection breadcrumb.
// Pattern: "<DataObject.name> · <Collection.name> — shepard" so the
// browser-tab strip stays readable with multiple DOs open.
useHead({
  title: () => {
    const doName = dataObject.value?.name;
    const collName = collection.value?.name;
    if (doName && collName) return `${doName} · ${collName} — shepard`;
    if (doName) return `${doName} — shepard`;
    return "DataObject — shepard";
  },
});

// LIC1 (FAIR-1): defensive computed accessors — the generated client model may
// not yet expose these fields even though the wire payload carries them.
const dataObjectLicense = computed<string | null>(() => {
  if (!dataObject.value) return null;
  const raw = (dataObject.value as unknown as { license?: string | null }).license;
  return raw ?? null;
});
const dataObjectAccessRights = computed<string | null>(() => {
  if (!dataObject.value) return null;
  const raw = (dataObject.value as unknown as { accessRights?: string | null })
    .accessRights;
  return raw ?? null;
});

// FAIR2: createdByOrcid — server-stamped ORCID of the creating researcher.
// Read-only; displayed as a badge linking to orcid.org.
const dataObjectCreatedByOrcid = computed<string | null>(() => {
  if (!dataObject.value) return null;
  const raw = (dataObject.value as unknown as { createdByOrcid?: string | null })
    .createdByOrcid;
  return raw ?? null;
});

// TOOLS-CONTEXT-DO-TEMPLATE-DETECT-1: appId of the :ShepardTemplate this
// DataObject was created from (server-stamped at create time). Drives the
// in-context Tools menu's DO-SHACL / DO-RENDER gate.
const dataObjectAttachedTemplateAppId = computed<string | null>(() => {
  if (!dataObject.value) return null;
  const raw = (dataObject.value as unknown as { attachedTemplateAppId?: string | null })
    .attachedTemplateAppId;
  return raw ?? null;
});

// UX612-M1 — kind of the attached template (VIEW_RECIPE / DATAOBJECT_RECIPE /
// …), resolved via the typed v2 client. `POST /v2/shapes/render` accepts only
// VIEW_RECIPE (422 otherwise), so the Tools menu's "Render view" item gates on
// this kind instead of mere template presence. Best-effort: a failed lookup
// leaves the kind null and the render item hidden (it would 422 anyway when
// the kind can't be confirmed as VIEW_RECIPE).
const templatesApi = useV2ShepardApi(TemplatesApi);
const dataObjectAttachedTemplate = ref<ShepardTemplate | null>(null);
const dataObjectAttachedTemplateKind = computed<string | null>(
  () => dataObjectAttachedTemplate.value?.templateKind ?? null,
);
const dataObjectHeaderIcon = computed<string>(() =>
  useTemplateIcon(dataObjectAttachedTemplate.value, "DataObject"),
);
watch(
  dataObjectAttachedTemplateAppId,
  async appId => {
    dataObjectAttachedTemplate.value = null;
    if (!appId) return;
    try {
      dataObjectAttachedTemplate.value = await templatesApi.value.getTemplate({ appId });
    } catch {
      // fire-and-forget — menu falls back to hiding the render item; header uses default icon
    }
  },
  { immediate: true },
);

// FAIR3: embargoEndDate — user-provided ISO-8601 end date for embargoed datasets.
// Editable when the user has Write permission on the collection.
const dataObjectEmbargoEndDate = computed<string | null>(() => {
  if (!dataObject.value) return null;
  const raw = (dataObject.value as unknown as { embargoEndDate?: string | null })
    .embargoEndDate;
  return raw ?? null;
});

// FAIR3: inline editing state for embargoEndDate.
const embargoEditActive = ref(false);
const embargoDraft = ref("");
const embargoSaving = ref(false);

function startEmbargoEdit() {
  embargoDraft.value = dataObjectEmbargoEndDate.value ?? "";
  embargoEditActive.value = true;
}

function cancelEmbargoEdit() {
  embargoEditActive.value = false;
}

async function saveEmbargoEdit() {
  if (!dataObject.value) return;
  embargoSaving.value = true;
  try {
    // V2-SWEEP Wave 3: appId-keyed merge-patch — only the embargo field
    // travels. Null/empty clears it (FAIR3).
    await patchDataObjectV2({
      embargoEndDate: embargoDraft.value ? embargoDraft.value : null,
    });
    emitSuccess("Embargo end date updated");
    handleDataObjectUpdate();
    embargoEditActive.value = false;
  } catch (e) {
    handleError(e, "updating embargo end date");
  } finally {
    embargoSaving.value = false;
  }
}
</script>

<template>
  <div style="max-width: 1400px">
    <v-container class="pa-0 fill-height" fluid>
      <!-- BUG-DO-DETAIL-HANG: render once the REQUIRED entities (collection +
           dataObject) load. Reference panels (dataReferences / relatedEntities)
           resolve independently and fail soft — they must NOT gate the page. -->
      <v-row
        v-if="!!collection && !!dataObject"
        no-gutters
      >
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
                  collectionIdStr +
                  dataObjectsPathFragment +
                  dataObjectIdStr,
              },
            ]"
          />
        </v-col>
        <v-col cols="12">
          <v-container class="pa-0" fluid>
            <v-row no-gutters>
              <TitleAndMetadataDisplay
                :entity="dataObject"
                id-label="Data Object ID"
                :icon-key="dataObjectHeaderIcon"
              />
            </v-row>
            <!-- LIC1/FAIR2/FAIR3/KIP1k: FAIR metadata strip — license + accessRights
                 + embargoEndDate + createdByOrcid + publication status. Same affordance
                 pattern as the Collection detail page. -->
            <v-row
              v-if="dataObjectLicense || dataObjectAccessRights || dataObjectEmbargoEndDate || dataObjectCreatedByOrcid || dataObject.appId"
              no-gutters
              class="pb-3 ga-2 align-center flex-wrap"
              data-testid="fair-metadata-strip"
            >
              <LicenseChip
                v-if="dataObjectLicense"
                :license="dataObjectLicense"
              />
              <AccessRightsChip
                v-if="dataObjectAccessRights"
                :access-rights="dataObjectAccessRights"
              />
              <!-- KIP1k: publication-status badge — informational only. -->
              <PublicationStatusBadge
                v-if="dataObject.appId"
                entity-kind="data-objects"
                :entity-app-id="dataObject.appId"
              />
              <!-- FAIR3: embargoEndDate — shown when set; editable by Write users. -->
              <span
                v-if="dataObjectEmbargoEndDate && !embargoEditActive"
                class="d-inline-flex align-center ga-1"
                data-testid="embargo-end-date-display"
              >
                <v-chip
                  size="small"
                  color="warning"
                  prepend-icon="mdi-lock-clock"
                  :text="`Embargo ends: ${dataObjectEmbargoEndDate}`"
                />
                <v-btn
                  v-if="isAllowedToEditCollection"
                  icon="mdi-pencil-outline"
                  variant="text"
                  size="x-small"
                  density="comfortable"
                  aria-label="Edit embargo end date"
                  data-testid="embargo-edit-btn"
                  @click="startEmbargoEdit"
                />
              </span>
              <!-- FAIR3 inline editor -->
              <span
                v-if="embargoEditActive"
                class="d-inline-flex align-center ga-2"
                data-testid="embargo-edit-form"
              >
                <v-text-field
                  v-model="embargoDraft"
                  label="Embargo end date (YYYY-MM-DD)"
                  placeholder="2027-12-31"
                  density="compact"
                  hide-details
                  style="max-width: 220px"
                  data-testid="embargo-date-input"
                />
                <v-btn
                  variant="flat"
                  color="primary"
                  size="small"
                  :loading="embargoSaving"
                  data-testid="embargo-save-btn"
                  @click="saveEmbargoEdit"
                >Save</v-btn>
                <v-btn
                  variant="text"
                  size="small"
                  data-testid="embargo-cancel-btn"
                  @click="cancelEmbargoEdit"
                >Cancel</v-btn>
              </span>
              <!-- FAIR3: "Set embargo" affordance when EMBARGOED but no date yet -->
              <v-btn
                v-if="!dataObjectEmbargoEndDate && !embargoEditActive && dataObjectAccessRights === 'EMBARGOED' && isAllowedToEditCollection"
                variant="tonal"
                color="warning"
                size="small"
                prepend-icon="mdi-lock-clock"
                data-testid="embargo-set-btn"
                @click="startEmbargoEdit"
              >Set embargo date</v-btn>
              <!-- FAIR2: createdByOrcid — read-only ORCID badge -->
              <a
                v-if="dataObjectCreatedByOrcid"
                :href="`https://orcid.org/${dataObjectCreatedByOrcid}`"
                target="_blank"
                rel="noopener"
                class="do-orcid-badge"
                :title="`Creator ORCID: ${dataObjectCreatedByOrcid} — click to view on orcid.org`"
                data-testid="created-by-orcid-badge"
              >
                <svg viewBox="0 0 256 256" width="20" height="20" aria-label="ORCID iD" role="img">
                  <circle cx="128" cy="128" r="128" fill="#A6CE39" />
                  <g fill="#FFFFFF">
                    <rect x="83" y="105" width="14" height="78" />
                    <circle cx="90" cy="88" r="9" />
                    <path d="M115 105 h35 c25 0 41 18 41 39 0 22 -18 39 -41 39 h-35 z M129 117 v54 h19 c20 0 28 -14 28 -27 0 -16 -10 -27 -28 -27 z" />
                  </g>
                </svg>
                <span class="do-orcid-id">{{ dataObjectCreatedByOrcid }}</span>
              </a>
            </v-row>
            <v-row
              v-if="dataObject.appId"
              no-gutters
              class="justify-end pb-2 ga-2 align-center"
            >
              <!-- TOOLS-CONTEXT-DO-* — in-context Tools menu grouping the
                   SPARQL query + vocabulary browser + SHACL validate +
                   view-recipe render entry points. Visible to readers
                   too; only the actions themselves require permissions
                   (handled by destination pages). -->
              <!-- FORM-UX-ACTIONBUTTON — "View as …" / "Record a …" from
                   GET /v2/shapes/applicable. Replaces the Tools menu's
                   former "Render view" entry (absorbed; the VIEW gate is
                   server-owned now). Hidden when nothing is applicable. -->
              <ActionMenuButton :focus-app-id="dataObject.appId" />
              <EntityToolsMenu
                :app-id="dataObject.appId"
                scope="data-object"
                :attached-template-app-id="dataObjectAttachedTemplateAppId"
                :attached-template-kind="dataObjectAttachedTemplateKind"
              />
              <PublishButton
                v-if="isAllowedToEditCollection"
                entity-kind="data-objects"
                :entity-app-id="dataObject.appId"
                :entity-name="dataObject.name"
              />
            </v-row>
            <!-- II2 (ui-scrutinizer-2026-05-30): "Cite this DataObject"
                 card — sister to the Collection-level RDM-001 card. Same
                 shape: APA / BibTeX / RIS / CSL JSON. Sits above the
                 description block so a funder / reviewer sees the
                 citation affordance before the prose. -->
            <CiteThisDataObjectCard :data-object="dataObject" />

            <!-- Always-visible: Description with inline edit. UI-017: when
                 edit is engaged, the surrounding section gets an outline + a
                 small "Editing description" label so the user can see at a
                 glance WHICH panel they are editing (the editor pops below the
                 head, so the visual cue closes the affordance gap). -->
            <section
              class="page-section description-section"
              :class="{ 'description-editing': descEditActive }"
              :data-testid="
                descEditActive ? 'description-editing' : 'description-static'
              "
            >
              <div class="page-section-head">
                <div class="text-h5 text-textbody1">Description</div>
                <span
                  v-if="descEditActive"
                  class="text-caption text-primary editing-label"
                  data-testid="description-editing-label"
                >
                  <v-icon size="x-small" class="mr-1">mdi-pencil-outline</v-icon>
                  Editing description
                </span>
                <v-btn
                  v-if="isAllowedToEditCollection && !descEditActive"
                  variant="text"
                  density="comfortable"
                  size="small"
                  prepend-icon="mdi-pencil-outline"
                  aria-label="Edit description"
                  @click="startDescEdit"
                >Edit</v-btn>
              </div>
              <RichTextEditor
                v-if="descEditActive"
                v-model="descDraft"
                :is-editable="true"
              />
              <DescriptionDisplay v-else :entity="dataObject" />
              <div v-if="descEditActive" class="d-flex align-center ga-2 mt-3">
                <!-- UIRULE-DROPDOWN-SEARCH-SORT: 5 options → searchable
                     (v-autocomplete); status lifecycle ladder order is meaningful,
                     so NOT natural-sorted. -->
                <v-autocomplete
                  v-model="descStatusDraft"
                  auto-select-first
                  label="Status"
                  :items="['DRAFT', 'IN_REVIEW', 'READY', 'PUBLISHED', 'ARCHIVED']"
                  density="compact"
                  clearable
                  hide-details
                  style="max-width: 200px"
                />
                <v-spacer />
                <v-btn variant="text" size="small" @click="cancelDescEdit">Cancel</v-btn>
                <v-btn
                  variant="flat"
                  color="primary"
                  size="small"
                  :loading="descSaving"
                  @click="saveDescEdit"
                >Save</v-btn>
              </div>
            </section>

            <!-- Always-visible: Semantic Annotation chips.
                 SEMA-V6-017: single "Annotate" button visible in basic AND
                 advanced mode (superset rule). Advanced mode adds the legacy
                 "Add raw attribute" button with a deprecation tooltip. -->
            <section class="page-section">
              <div class="page-section-head">
                <div class="text-h5 text-textbody1">
                  Annotations
                  <!-- UX Pattern D: low-emphasis count badge -->
                  <span
                    v-if="numberOfSemanticAnnotations !== undefined"
                    class="text-low-emphasis ml-1"
                    data-testid="semantic-annotations-count"
                  >({{ numberOfSemanticAnnotations }})</span>
                </div>
                <!-- SEMA-V6-017: primary "Annotate" button — always visible -->
                <v-btn
                  v-if="isAllowedToEditCollection"
                  color="primary"
                  density="comfortable"
                  prepend-icon="mdi-tag-plus-outline"
                  size="small"
                  variant="tonal"
                  data-testid="annotate-button"
                  @click="showAnnotationDialog = true"
                >
                  Annotate
                </v-btn>
                <!-- SEMA-V6-017: legacy "Add raw attribute" — advanced mode only,
                     with deprecation tooltip pointing at the §11 Phase 2/3 migration. -->
                <v-tooltip
                  v-if="advancedMode && isAllowedToEditCollection"
                  text="Legacy path — prefer Annotate for new annotations. Will be removed in §11 Phase 3 (SEMA-V6-012)."
                  location="top"
                  max-width="320"
                >
                  <template #activator="{ props: tip }">
                    <ExpansionPanelTitleButton
                      v-bind="tip"
                      icon="mdi-plus-circle"
                      text="Add/Edit attributes (legacy)"
                      @click="() => (showAttributeEditDialog = true)"
                    />
                  </template>
                </v-tooltip>
              </div>

              <!-- New AnnotationDialog (SEMA-V6-005).
                   Prefers the SEMA-V6-004 polymorphic path when dataObject.appId
                   is available (DataObjects always have appId). Falls back to the
                   Annotated interface for legacy contexts. -->
              <AnnotationDialog
                v-if="showAnnotationDialog && isAllowedToEditCollection"
                v-model:show-dialog="showAnnotationDialog"
                :subject-app-id="dataObject?.appId ?? undefined"
                subject-kind="DataObject"
                :annotated="new AnnotatedDataObject(dataObject.appId ?? '')"
                @annotation-created="handleAnnotationListUpdate"
              />

              <SemanticAnnotationList
                v-if="dataObject.appId"
                :annotated="new AnnotatedDataObject(dataObject.appId)"
                :can-delete="!!isAllowedToEditCollection"
                @annotations="onAnnotationsLoaded"
              />
            </section>

            <v-row no-gutters>
              <ExpansionPanels :default-open="[1, 2, 3, 4]">
                <ExpansionPanelItem
                  :count="Object.keys(dataObject.attributes ?? {}).length"
                  title="Attributes"
                >
                  <AttributesDisplay :entity="dataObject" />
                  <template v-if="isAllowedToEditCollection" #append>
                    <ExpansionPanelTitleButton
                      icon="mdi-plus-circle"
                      text="Add/Edit"
                      @click="() => (showAttributeEditDialog = true)"
                    />
                    <EditDataObjectAttributesDialog
                      v-if="showAttributeEditDialog"
                      v-model:show-dialog="showAttributeEditDialog"
                      :collection-id="collection.id"
                      :data-object-id="dataObject.id"
                    />
                  </template>
                </ExpansionPanelItem>
                <ExpansionPanelItem
                  :count="numberOfLabJournalEntries"
                  title="Lab Journal"
                >
                  <div class="pt-4">
                    <!-- UI-DO-LABJOURNAL-V2: appId-keyed props so the panel works
                         for appId-only DataObjects (post-reset). Numeric ids passed
                         as optional compat for the v1 create/delete child path. -->
                    <DataObjectLabJournalEntryList
                      :collection-app-id="collectionIdStr"
                      :data-object-app-id="dataObject?.appId ?? dataObjectIdStr"
                      :collection-numeric-id="collection?.id ?? undefined"
                      :data-object-numeric-id="dataObject?.id ?? undefined"
                      @number-of-entries-changed="onLabJournalCountChanged"
                    />
                  </div>
                </ExpansionPanelItem>
                <!-- REF-UNIFIED-TABLE: single unified references table covering
                     all reference kinds (TimeSeries, File Bundle, Structured Data,
                     Git, Video, HDF5). Filter chips at the top let users narrow
                     to a specific kind. New kinds show in the table via extraItems.
                     The old per-kind panes (GitReferencesPane, VideoStreamReferencesPane,
                     HdfReferencesPane) are preserved in components/context/dataobject/legacy/
                     but no longer mounted here. -->
                <ExpansionPanelItem
                  :count="totalReferenceCount"
                  title="Data References"
                >
                  <template v-if="isAllowedToEditCollection" #append>
                    <ExpansionPanelTitleButton
                      icon="mdi-plus-circle"
                      text="Add data reference"
                      @click="() => (showCreateDataReferenceDialog = true)"
                    />
                    <CreateDataReferenceDialog
                      v-if="showCreateDataReferenceDialog"
                      v-model:show-dialog="showCreateDataReferenceDialog"
                      :collection-id="collection.id"
                      :data-object-id="dataObject.id"
                      :data-object-app-id="dataObject.appId ?? undefined"
                    />
                  </template>
                  <DataObjectFileUpload
                    v-if="isAllowedToEditCollection"
                    :collection-id="collection.id"
                    :dataobject-id="dataObject.id"
                  />
                  <DataObjectDataReferencesTable
                    :collection-id="collection.id"
                    :data-object-id="dataObject.id"
                    :data-references="dataReferencesSafe"
                    :is-allowed-to-edit-collection="
                      isAllowedToEditCollection ?? false
                    "
                    :extra-items="extraReferenceItems"
                    :data-object-app-id="dataObject.appId ?? undefined"
                    @refresh="refreshExtraReferences"
                  />
                </ExpansionPanelItem>
                <ExpansionPanelItem
                  :count="relatedEntitiesSafe.length"
                  title="Relationships"
                >
                  <DataObjectRelationshipsTable
                    :collection-id="collection.id"
                    :collection-app-id="collectionIdStr"
                    :data-object-id="dataObject.id"
                    :is-allowed-to-edit-collection="
                      isAllowedToEditCollection ?? false
                    "
                    :related-entities="relatedEntitiesSafe"
                    :predecessor-relationship-types="predecessorRelationshipTypesMap"
                  />
                  <template v-if="isAllowedToEditCollection" #append>
                    <ExpansionPanelTitleButton
                      icon="mdi-plus-circle"
                      text="Add"
                      @click="() => (showAddRelationshipDialog = true)"
                    />
                    <AddRelationshipDialog
                      v-if="showAddRelationshipDialog"
                      v-model:show-dialog="showAddRelationshipDialog"
                      :collection-id="collection.id"
                      :collection-app-id="collection.appId ?? undefined"
                      :data-object-id="dataObject.id"
                      :data-object-app-id="dataObject.appId ?? undefined"
                    />
                  </template>
                </ExpansionPanelItem>
                <!-- MISSING-aas-ui Slice 4: AAS submodel context pane. Mounted
                     when the DataObject has a resolved appId and the parent
                     Collection appId is known (always true on this page). The
                     pane handles 501/disabled + 404 internally; mounting cost
                     is negligible when AAS is off. -->
                <ExpansionPanelItem
                  v-if="dataObject?.appId && collectionIdStr"
                  title="AAS Submodel"
                >
                  <DataObjectAasPane
                    :collection-app-id="collectionIdStr"
                    :data-object-app-id="dataObject.appId"
                  />
                </ExpansionPanelItem>
                <!-- J1c retirement (2026-05-29): the dedicated Jupyter Notebooks
                     panel has been merged into the unified Data References
                     table. Notebooks render as rows with a notebook icon and,
                     when the admin-configurable JupyterConfig (J1e) is open,
                     an "Open in JupyterHub" action button. -->
                <!-- SPATIAL-UNIFY-003: the bolt-on "Spatial data" pane
                     (DataObjectSpatialContainersPane + the "run the spatial-
                     importer pass" empty-state) is RETIRED. Spatial is now a
                     "Spatial (N)" tab in the unified Data References table
                     above, created in-context via the per-File "Promote to
                     spatial" action (POST /v2/spatial/promote). See
                     aidocs/integrations/124. -->
                <!-- MFFD-MULTIPLAYER-1: synchronised multi-payload player.
                     Mounts only when the DO carries ≥ 2 distinct payload
                     kinds (TS + video / TS + thermo / etc.). One shared
                     time cursor drives every tile; scrubbing the toolbar
                     moves the TS marker AND the video playhead AND any
                     other tile's representation of t. Lazy-mounted under
                     v-if so the heavy ECharts / VideoPlayer setup runs
                     only when a user opens this panel. -->
                <ExpansionPanelItem
                  v-if="multiPlayerTiles.length >= 2 && dataObject.appId"
                  title="Synchronised player"
                >
                  <MultiPlayerPane
                    :data-object-app-id="dataObject.appId"
                    :tiles="multiPlayerTiles"
                    :thermography-bundle-app-id="thermographyBundleAppId"
                  />
                </ExpansionPanelItem>
                <!-- Provenance: two views — a structured time-based log
                     (default, easier to read) and the force-directed
                     graph (eye-candy, second tab). Both render the same
                     /v2/provenance/activities data; the user picks
                     whichever shape they want. -->
                <ExpansionPanelItem title="Provenance">
                  <v-tabs v-model="provView" density="compact" color="primary" class="pb-2">
                    <v-tab value="log">
                      <v-icon size="small" class="me-1">mdi-format-list-bulleted</v-icon>
                      Log
                    </v-tab>
                    <v-tab value="graph">
                      <v-icon size="small" class="me-1">mdi-graph-outline</v-icon>
                      Graph
                    </v-tab>
                  </v-tabs>
                  <div class="pt-2 pb-2">
                    <DataObjectProvLog
                      v-if="provView === 'log' && dataObject.appId"
                      :target-app-id="dataObject.appId"
                    />
                    <DataObjectProvGraph
                      v-else-if="provView === 'graph'"
                      :data-object="dataObject"
                      :collection-app-id="collectionIdStr"
                    />
                  </div>
                </ExpansionPanelItem>
                <!-- MFFD-NDT-QUALITY-1: Thermography NDT pane — mounts only
                     when a thermography-shaped FileBundleReference is
                     attached to the DataObject. Self-contained: fetches
                     its own cached plate-heatmap + quality summary via
                     /v2/thermography/*. Caller passes canEdit so the
                     Re-analyze button gates Write properly. -->
                <ExpansionPanelItem
                  v-if="thermographyBundleAppId && dataObject.appId"
                  title="Thermography NDT"
                >
                  <DataObjectThermographyPane
                    :data-object-app-id="dataObject.appId"
                    :image-bundle-app-id="thermographyBundleAppId"
                  />
                </ExpansionPanelItem>
                <!-- OTVIS-VIEWER: decoded Edevis OTvis amplitude/phase frame
                     viewer. One panel per .OTvis singleton FileReference on
                     this DO. In-context-first entry — the appId is already in
                     hand; the viewer pulls bytes from the reference and shows
                     server-rendered heatmap PNGs with a frame scrubber. -->
                <ExpansionPanelItem
                  v-for="otvisRef in otvisReferences"
                  :key="`otvis-${otvisRef.appId}`"
                  title="Thermography Frames (OTvis)"
                >
                  <DataObjectOtvisViewer
                    :file-reference-app-id="otvisRef.appId"
                    :reference-name="otvisRef.name"
                  />
                </ExpansionPanelItem>
                <!-- MFFD-RENDER-MATERIAL-BATCH-TRACE (slice 3): in-context
                     pane showing every process step that consumed this
                     material batch. Mounts only when the DO carries the
                     urn:shepard:mffd:batch-id predicate (detected from
                     the loaded SemanticAnnotation list). -->
                <ExpansionPanelItem
                  v-if="isMaterialBatchDo && dataObject.appId && collection.appId"
                  title="Material Batch Consumers"
                >
                  <MaterialBatchTracePane
                    :data-object-app-id="dataObject.appId"
                    :collection-app-id="collection.appId"
                  />
                </ExpansionPanelItem>
                <!-- MFFD-IMAGEBUNDLE-PANE-MOUNT-1: generic image frame scrubber.
                     Shown when at least one FileBundleReference is attached and
                     its first group carries at least one image-extension file.
                     Distinct from the Thermography NDT pane — that pane uses a
                     name heuristic and renders a plate-heatmap; this one uses
                     content-type detection and renders the ImageBundleViewer
                     scrubber. Non-overlapping: thermography bundles are excluded
                     by the name heuristic so they don't appear here too. -->
                <ExpansionPanelItem
                  v-if="imageBundleCandidateAppIds.length > 0 && dataObject.appId"
                  title="Image Frames"
                >
                  <DataObjectImageBundlePane
                    :data-object-app-id="dataObject.appId"
                    :candidate-bundle-app-ids="imageBundleCandidateAppIds"
                  />
                </ExpansionPanelItem>
                <!-- UX-PROV1: Ancestor chain — advanced mode only.
                     Shows the upstream predecessor chain as a vertical
                     timeline using the ANC-1 predecessor-chain endpoint.
                     Strict superset rule: this panel is additive; it
                     never hides anything shown in basic mode. -->
                <ExpansionPanelItem
                  v-if="advancedMode && dataObject.appId && collection.appId"
                  title="Ancestor Chain"
                >
                  <AncestorChainPanel
                    :collection-id="collection.id"
                    :collection-app-id="collection.appId"
                    :data-object-app-id="dataObject.appId"
                  />
                </ExpansionPanelItem>
              </ExpansionPanels>
            </v-row>
          </v-container>
        </v-col>
      </v-row>
      <EntityNotFound
        v-else-if="isDataObjectNotFound"
        entity-kind="DataObject"
        :requested-id="dataObjectIdStr"
        :parent-route="`/collections/${collectionIdStr}`"
      />
      <EntityNotFound
        v-else-if="isCollectionNotFound"
        entity-kind="Collection"
        :requested-id="collectionIdStr"
        parent-route="/collections"
      />
      <!-- BUG-DO-DETAIL-HANG: a non-404 DataObject load failure (403 / 500 /
           network) used to leave the page spinning forever. Surface the
           not-found fallback once the fetch settles unsuccessfully so the user
           sees an actionable empty state instead of an indeterminate spinner. -->
      <EntityNotFound
        v-else-if="dataObjectLoadFailed"
        entity-kind="DataObject"
        :requested-id="dataObjectIdStr"
        :parent-route="`/collections/${collectionIdStr}`"
      />
      <CenteredLoadingSpinner v-else />
    </v-container>
  </div>
</template>

<style lang="scss" scoped>
.page-section {
  margin-bottom: 24px;
}
.page-section-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
  padding-left: 32px;
}
// UI-017: visual cue for inline description edit. A subtle 2px primary-tinted
// outline + soft tint frames the section being edited so the user can see
// what's editable without hunting for the editor.
.description-section {
  border-radius: 6px;
  transition: outline-color 120ms ease, background-color 120ms ease;
  outline: 2px solid transparent;
}
.description-editing {
  outline: 2px solid rgba(var(--v-theme-primary), 0.4);
  background-color: rgba(var(--v-theme-primary), 0.03);
  padding: 8px 0;
}
.editing-label {
  display: inline-flex;
  align-items: center;
  font-weight: 500;
  letter-spacing: 0.02em;
}

/* FAIR2: creator ORCID badge on the DataObject detail page.
   Same visual treatment as .orcid-badge in ProfilePane.vue. */
.do-orcid-badge {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  text-decoration: none;
  border-radius: 4px;
  padding: 2px 6px 2px 2px;
  background: rgba(166, 206, 57, 0.12);
  border: 1px solid rgba(166, 206, 57, 0.4);
  cursor: pointer;
  color: inherit;
  font-size: 0.75rem;
}
.do-orcid-badge:hover {
  background: rgba(166, 206, 57, 0.2);
  border-color: #A6CE39;
}
.do-orcid-id {
  font-family: monospace;
  letter-spacing: 0.02em;
  color: #4a7a1a;
}
</style>
