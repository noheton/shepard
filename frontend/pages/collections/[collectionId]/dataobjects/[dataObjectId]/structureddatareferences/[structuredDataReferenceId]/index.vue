<script lang="ts" setup>
/**
 * REFS-V2-PANELS-2 — migrate SDR detail page to the unified v2 envelope
 * (same shape as FileReference + TimeseriesReference pages). Fixes the
 * eternal-spinner regression for v2-navigated users caused by resolveNumericId
 * returning undefined for UUID route params.
 *
 * Structured data CONTENT items (the JSON payloads) still require v1-only
 * endpoints (GET /shepard/api/…/structuredDataPayload + getAllStructuredDatas).
 * Those calls need the numeric structuredDataContainerId that the v2 wire
 * shape intentionally suppresses. Content rendering is tracked as SDR-CONTENT-V2
 * in aidocs/16-dispatcher-backlog.md.
 */
import {
  ContainersApi,
  ReferencesApi,
} from "@dlr-shepard/backend-client";
import ActionButton from "~/components/common/data-table/ActionButton.vue";
import type { StructuredDataDataTableItem } from "~/components/context/display-components/structured-data-references/structuredDataDataTableItem";
import { useV2ShepardApi } from "~/composables/common/api/useV2ShepardApi";
import { useFetchReferenceV2 } from "~/composables/context/useFetchReferenceV2";

definePageMeta({ layout: "collection" });

const { routeParams } = useCollectionRouteParams();
const collectionIdStr = routeParams.value.collectionId ?? "";
const dataObjectIdStr = routeParams.value.dataObjectId ?? "";

const showDeleteDialog = ref<boolean>(false);
const showAddAnnotationDialog = ref<boolean>(false);
const showEditDialog = ref<boolean>(false);
const showStructuredDataContentViewerDialog = ref<boolean>(false);
const structuredDataDataTableItems = ref<StructuredDataDataTableItem[]>([]);
const selectedPayload = ref<string>("");
const selectedItemName = ref<string>("");
const isEditMode = ref<boolean>(false);

const { collection, isAllowedToEditCollection } =
  useFetchCollection(collectionIdStr);
const { dataObject } = useFetchDataObject(collectionIdStr, dataObjectIdStr);

// UX612-C1: load the reference via the unified v2 envelope — same shape the
// FileReference and TimeseriesReference detail pages use. Route param IS the
// appId (frontend-v2-only rule); no numeric-id resolution needed or wanted.
const {
  referenceV2,
  notFound: structuredDataReferenceNotFound,
  refresh: refreshReferenceV2,
} = useFetchReferenceV2(() => routeParams.value.structuredDataReferenceId);

// Container metadata: numeric containerId is suppressed in BasicContainerV2IO
// (@JsonIgnoreProperties({"id"})). Fetch the human-readable container name via
// GET /v2/containers/{appId} using the containerAppId from the v2 payload.
const containersApi = useV2ShepardApi(ContainersApi);

const containerMeta = ref<{
  name: string | undefined;
  availability: "available" | "deleted" | "forbidden" | "error";
} | undefined>(undefined);

const containerAppId = computed<string | undefined>(() => {
  const p = referenceV2.value?.payload;
  if (!p) return undefined;
  return (p as { structuredDataContainerAppId?: string }).structuredDataContainerAppId ?? undefined;
});

watch(
  containerAppId,
  async appId => {
    if (!appId) {
      containerMeta.value = undefined;
      return;
    }
    try {
      const c = await containersApi.value.getContainer({ appId });
      containerMeta.value = { name: c.name, availability: "available" };
    } catch (e) {
      const status = (e as { response?: { status?: number } })?.response?.status;
      containerMeta.value = {
        name: undefined,
        availability:
          status === 403 ? "forbidden" : status === 404 ? "deleted" : "error",
      };
    }
  },
  { immediate: true },
);

interface SdrView {
  appId: string;
  /** Numeric Neo4j id — @JsonIgnore-d on v2 wire; 0 is a display-only placeholder. */
  id: number;
  name: string;
  createdAt: Date;
  createdBy: string;
  updatedAt: Date | null;
  updatedBy: string | null;
  /** UUID of the backing StructuredDataContainer; null when absent in payload. */
  structuredDataContainerAppId: string | null;
  /**
   * Numeric container id — unavailable from v2. Kept as 0; StructuredDataViewerDialog
   * guards on !props.structuredDataContainerId (0 → save is a no-op), so the
   * edit affordance is safely disabled pending SDR-CONTENT-V2.
   */
  structuredDataContainerId: number;
  referencedContainerName: string | undefined;
  referencedContainerAvailability:
    | "available"
    | "deleted"
    | "forbidden"
    | "error";
}

const structuredDataReference = computed<SdrView | undefined>(() => {
  const r = referenceV2.value;
  if (!r) return undefined;
  const p = (r.payload ?? {}) as {
    structuredDataContainerAppId?: string | null;
  };
  return {
    appId: r.appId ?? routeParams.value.structuredDataReferenceId ?? "",
    id: typeof r.id === "number" ? r.id : 0,
    name: r.name ?? "",
    createdAt: r.createdAt,
    createdBy: r.createdBy,
    updatedAt: r.updatedAt,
    updatedBy: r.updatedBy,
    structuredDataContainerAppId: p.structuredDataContainerAppId ?? null,
    structuredDataContainerId: 0,
    referencedContainerName: containerMeta.value?.name,
    referencedContainerAvailability:
      containerMeta.value?.availability ?? "available",
  };
});

const headers = ref([
  { title: "Name", key: "name", sortable: true },
  { title: "Oid", key: "oid", sortable: true },
  { title: "Created at", key: "createdAt", sortable: true },
  { title: "", key: "actions" },
]);

function onAnnotate() {
  showAddAnnotationDialog.value = true;
}

function onEdit() {
  showEditDialog.value = true;
}

function onDelete() {
  showDeleteDialog.value = true;
}

function onShowStructuredDataContentDialog(structuredDataPayload: string) {
  selectedPayload.value = structuredDataPayload;
  selectedItemName.value = "";
  isEditMode.value = false;
  showStructuredDataContentViewerDialog.value = true;
}

function onEditStructuredDataContent(payload: string, name: string) {
  selectedPayload.value = payload;
  selectedItemName.value = name;
  isEditMode.value = true;
  showStructuredDataContentViewerDialog.value = true;
}

async function onStructuredDataSaved() {
  refreshReferenceV2();
}

/**
 * UI-SDREF-NO-CONTENT-001 — download the structured-data payload as a JSON
 * file. Items are currently loaded from the v1 fallback list; this handler
 * stays for when SDR-CONTENT-V2 wires up the v2 content fetch.
 */
function onDownloadStructuredData(params: {
  filename: string;
  payload: string;
}) {
  try {
    let body = params.payload;
    try {
      body = JSON.stringify(JSON.parse(params.payload), null, 2);
    } catch {
      /* not JSON — keep raw */
    }
    const blob = new Blob([body], { type: "application/json" });
    downloadFile(blob, sanitizeFilename(params.filename));
  } catch (e) {
    handleError(e as Error, "downloading structured data");
  }
}

async function deleteStructuredDataReference() {
  const appId = structuredDataReference.value?.appId;
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
    handleError(error, "deleteStructuredDataReference");
    showDeleteDialog.value = false;
  }
}

const itemsPerPage = 10;

watch(structuredDataReference, () => {
  useHead({
    title: structuredDataReference.value?.name + " | shepard",
  });
});
</script>

<template>
  <div style="max-width: 1000px">
    <v-container class="pa-0 fill-height" fluid max-width="1000px">
      <v-row v-if="!!collection && !!dataObject && !!structuredDataReference">
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
                title: `${structuredDataReference?.name}`,
                to:
                  collectionsPath +
                  routeParams.collectionId +
                  dataObjectsPathFragment +
                  routeParams.dataObjectId +
                  structuredDataReferencesPathFragment +
                  routeParams.structuredDataReferenceId,
              },
            ]"
          />
        </v-col>
        <v-col cols="12">
          <v-container class="pa-0" fluid>
            <v-row no-gutters>
              <TitleAndMetadataDisplay
                :entity="{
                  ...structuredDataReference,
                  name: `Structured Data Reference '${structuredDataReference.name}'`,
                  type: 'Structured Data',
                  container: structuredDataReference.structuredDataContainerAppId
                    ? {
                        title:
                          structuredDataReference.referencedContainerName ??
                          'unknown name',
                        id: 0,
                        type: 'STRUCTUREDDATA',
                        availability:
                          structuredDataReference.referencedContainerAvailability,
                        appId: structuredDataReference.structuredDataContainerAppId,
                      }
                    : undefined,
                }"
                :on-annotate="onAnnotate"
                :on-edit="structuredDataReference.appId && isAllowedToEditCollection ? onEdit : undefined"
                :on-delete="onDelete"
                id-label="ID"
              />
            </v-row>
            <v-row align="center" justify="space-between">
              <v-col>
                <SemanticAnnotationList
                  v-if="structuredDataReference?.appId"
                  :can-delete="!!isAllowedToEditCollection"
                  :annotated="
                    new AnnotatedReference(
                      structuredDataReference.appId,
                      'StructuredDataReference',
                    )
                  "
                />
              </v-col>
            </v-row>
            <!-- SDR-CONTENT-V2: structured data payload items require a v2
                 GET /v2/references/{appId}/content endpoint not yet implemented
                 for kind=structured-data. Table renders empty until that ships. -->
            <v-row>
              <DataTable
                :items-per-page="itemsPerPage"
                :cell-props="{
                  class: 'text-textbody1',
                }"
                :header-props="{
                  class: 'text-subtitle-2 text-textbody1',
                }"
                :headers="headers"
                :items-for-pagination="structuredDataDataTableItems"
              >
                <template
                  #[`item.name`]="{
                    value,
                    item,
                  }: {
                    value: StructuredDataDataTableItem['name'];
                    item: StructuredDataDataTableItem;
                  }"
                >
                  <!-- UI-SDREF-NO-CONTENT-001 — name is a link to the View
                       dialog (was plain text → user thought it was a
                       dead end). Mirrors the FileReference page. -->
                  <a
                    v-if="item.actions.showPayload.enabled"
                    href="#"
                    class="sd-name-link"
                    @click.prevent="
                      () =>
                        onShowStructuredDataContentDialog(
                          item.actions.showPayload.payload,
                        )
                    "
                  >{{ value.structuredDataName }}</a>
                  <span v-else>{{ value.structuredDataName }}</span>
                  <span
                    v-if="value.availability !== 'available'"
                    class="text-error"
                  >
                    ({{ value.availability }})
                  </span>
                </template>
                <template #[`item.createdAt`]="{ value }: { value: Date }">
                  {{ toShortDateString(value) }}
                </template>
                <template
                  #[`item.actions`]="{
                    value,
                    item,
                  }: {
                    value: StructuredDataDataTableItem['actions'];
                    item: StructuredDataDataTableItem;
                  }"
                >
                  <ActionContainer>
                    <ActionButton
                      v-if="value.download.enabled"
                      icon="mdi-tray-arrow-down"
                      aria-label="Download structured data as JSON"
                      @click="() => onDownloadStructuredData(value.download)"
                    />
                    <ActionButton
                      v-if="value.showPayload.enabled"
                      icon="mdi-eye-outline"
                      aria-label="View structured data"
                      @click="
                        () =>
                          onShowStructuredDataContentDialog(
                            value.showPayload.payload,
                          )
                      "
                    />
                    <ActionButton
                      v-if="value.showPayload.enabled && isAllowedToEditCollection"
                      icon="mdi-pencil-outline"
                      aria-label="Edit structured data"
                      @click="
                        () =>
                          onEditStructuredDataContent(
                            value.showPayload.payload,
                            item.name?.structuredDataName ?? '',
                          )
                      "
                    />
                  </ActionContainer>
                </template>
              </DataTable>
            </v-row>
          </v-container>
        </v-col>
      </v-row>
      <!-- UI-404-NICE-EMPTY-STATE-REF-PAGES: 404 on the structured-data reference
           fetch → honest empty state instead of an eternal spinner. -->
      <EntityNotFound
        v-else-if="structuredDataReferenceNotFound"
        entity-kind="StructuredDataReference"
        :requested-id="routeParams.structuredDataReferenceId ?? ''"
        :parent-route="
          collectionsPath +
          routeParams.collectionId +
          dataObjectsPathFragment +
          routeParams.dataObjectId
        "
      />
      <CenteredLoadingSpinner v-else />
    </v-container>
    <EditStructuredDataReferenceDialog
      v-if="showEditDialog && structuredDataReference?.appId"
      v-model:show-dialog="showEditDialog"
      :structured-data-reference-app-id="structuredDataReference.appId"
      :current-name="structuredDataReference.name"
      @saved="(newName) => { refreshReferenceV2(); void newName; }"
    />
    <ConfirmDeleteDialog
      v-if="showDeleteDialog"
      v-model:show-dialog="showDeleteDialog"
      @confirmed="deleteStructuredDataReference"
    />
    <AddAnnotationDialog
      v-if="showAddAnnotationDialog && structuredDataReference?.appId"
      v-model:show-dialog="showAddAnnotationDialog"
      :annotated="
        new AnnotatedReference(
          structuredDataReference.appId,
          'StructuredDataReference',
        )
      "
    />
    <StructuredDataViewerDialog
      v-if="showStructuredDataContentViewerDialog"
      v-model:show-dialog="showStructuredDataContentViewerDialog"
      :structured-data-payload="selectedPayload"
      :is-editable="isEditMode"
      :structured-data-container-id="structuredDataReference?.structuredDataContainerId"
      :structured-data-name="selectedItemName"
      @saved="onStructuredDataSaved"
    />
  </div>
</template>

<style lang="scss" scoped>
.v-table {
  :deep(.word-wrap-anywhere) {
    word-wrap: anywhere;
  }

  :deep(tbody) > tr > td {
    padding: 20px 24px !important;
  }
}

.sd-name-link {
  color: rgb(var(--v-theme-primary));
  text-decoration: none;
  cursor: pointer;

  &:hover {
    text-decoration: underline;
  }
}
</style>
