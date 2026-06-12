<script setup lang="ts">
import type { ShepardFile } from "@dlr-shepard/backend-client";
import { FileContainerAccessor } from "~/composables/container/FileContainerAccessor";
import { useFileContainerLinkedDataObjects } from "~/composables/containers/useFileContainerLinkedDataObjects";

const { routeParams } = useContainerRouteParams();
const containerId = routeParams.value.containerId;
const urlSegment = containerTypeUrlPathSegmentMappings.FILE;

// V2-SWEEP-003-2: route param is now an appId (UUID v7) when navigated from
// CollectionContainersPanel / ContainerList. HeaderBar search still routes numeric
// id (V1-EXCEPTION, SEARCH-V2 will retire it) — fetchData() handles both paths.
const containerAccessor = new FileContainerAccessor(containerId);
const containerAppId = computed<string | null>(
  () => containerAccessor.fileContainer.value?.appId ?? (/^\d+$/.test(containerId) ? null : containerId),
);
// Numeric Neo4j id for child components still using v1 API (V1-EXCEPTION).
const containerNumericId = computed<number>(
  () => /^\d+$/.test(containerId) ? Number(containerId) : (containerAccessor.fileContainer.value?.id ?? 0),
);
const { dataObjects: linkedDataObjects, isLoading: linkedDataObjectsLoading } =
  useFileContainerLinkedDataObjects(containerAppId);

const totalFileSizeBytes = computed(() =>
  (containerAccessor.files.value ?? []).reduce((sum, f) => sum + (f.fileSize ?? 0), 0)
);

function fmtBytes(b: number): string {
  if (b === 0) return "0 B";
  if (b < 1_048_576) return `${(b / 1_024).toFixed(1)} KB`;
  if (b < 1_073_741_824) return `${(b / 1_048_576).toFixed(1)} MB`;
  return `${(b / 1_073_741_824).toFixed(2)} GB`;
}

const deleteWarning = computed<string | undefined>(() => {
  const n = linkedDataObjects.value?.length ?? 0;
  if (n === 0) return undefined;
  return (
    `${n} data object${n === 1 ? "" : "s"} reference this container. ` +
    "Deleting it now will leave those references orphaned (the files they used to point at will no longer be retrievable)."
  );
});

const fetchData = async () => {
  await containerAccessor.fetchData();
  containerAccessor.fetchFiles();
  containerAccessor.fetchRoles();
};

onContainerUpdated(() => {
  fetchData();
});

const uploadFile = async (
  file: File,
  options?: import("~/composables/container/xhrUpload").XhrUploadOptions,
): Promise<ShepardFile> => {
  return containerAccessor.uploadFile(file, options);
};
fetchData();

// UX Pattern F (2026-05-24): reactive title — call useHead once with a getter.
useHead({
  title: () =>
    containerAccessor.fileContainer.value?.name
      ? `${containerAccessor.fileContainer.value.name} (Files) — shepard`
      : "File Container — shepard",
});
</script>
<template>
  <PageShell>
  <v-container fluid>
    <v-row v-if="!!containerAccessor.fileContainer.value" no-gutters>
      <v-col cols="12">
        <Breadcrumbs
          :items="[
            {
              title: 'Containers',
              to: containersPath,
            },
            {
              title: containerAccessor.fileContainer.value.name,
              to: containersPath + urlSegment + containerId,
            },
          ]"
        />
      </v-col>
      <v-col cols="12">
        <v-container class="pa-0" fluid>
          <v-row no-gutters>
            <ContainerTitleAndMetadataDisplay
              :app-id="containerAppId ?? containerId"
              :n-items="containerAccessor.files.value?.length"
              :name="containerAccessor.fileContainer.value.name"
              :type-label="'File Container'"
            >
              <template #buttons>
                <UploadFilesButton
                  v-if="containerAccessor.isAllowedToEditData.value"
                  :upload-file="
                    async (file, options) => {
                      await uploadFile(file, options);
                    }
                  "
                />
                <EditPermissionsButton
                  v-if="containerAccessor.isAllowedToEditPermissions.value"
                  :shepard-object-accessor="containerAccessor"
                />
                <DeleteContainerButton
                  v-if="containerAccessor.isAllowedToDelete.value"
                  :entity-name="containerAccessor.fileContainer.value.name"
                  :warning="deleteWarning"
                  @delete="containerAccessor.delete()"
                />
              </template>
            </ContainerTitleAndMetadataDisplay>
          </v-row>
        </v-container>
      </v-col>
    </v-row>
    <CenteredLoadingSpinner v-else />
    <!-- Storage stats chips — computed from loaded files -->
    <div
      v-if="containerAccessor.files.value?.length"
      class="d-flex flex-wrap align-center ga-2 mb-3"
    >
      <v-chip size="small" variant="tonal" prepend-icon="mdi-database-outline">
        {{ fmtBytes(totalFileSizeBytes) }} total
      </v-chip>
      <v-chip size="small" variant="tonal" prepend-icon="mdi-file-multiple-outline">
        {{ containerAccessor.files.value.length }} file{{ containerAccessor.files.value.length === 1 ? "" : "s" }}
      </v-chip>
    </div>
    <FilesTable
      :files="containerAccessor.files.value"
      :is-allowed-to-edit="containerAccessor.isAllowedToEditData.value"
      :loading="containerAccessor.loading.value"
      :container-app-id="containerAccessor.fileContainer.value?.appId ?? undefined"
      :container-id="containerNumericId"
      @delete-file="(file: ShepardFile) => containerAccessor.deleteFile(file)"
      @download-file="
        (file: ShepardFile) => containerAccessor.downloadFile(file)
      "
    />
    <!-- SA-CONT: container-level semantic annotations -->
    <ExpansionPanels class="mt-4" :default-open="[0]">
      <ExpansionPanelItem title="Semantic Annotations">
        <template
          v-if="containerAccessor.isAllowedToEditData.value"
          #append
        >
          <AddAnnotationButton
            :annotated="new AnnotatedFileContainer(containerAppId ?? '')"
          />
        </template>
        <SemanticAnnotationList
          :annotated="new AnnotatedFileContainer(containerAppId ?? '')"
          :can-delete="!!containerAccessor.isAllowedToEditData.value"
        />
      </ExpansionPanelItem>
    </ExpansionPanels>
    <!-- CC1b: Referenced by — wired to GET /v2/file-containers/{id}/linked-data-objects -->
    <ExpansionPanels class="mt-4" :default-open="[0]">
      <ExpansionPanelItem
        title="Referenced by"
        :count="linkedDataObjects?.length"
      >
        <div v-if="linkedDataObjectsLoading" role="status" class="pa-4">
          <v-progress-circular indeterminate size="20" aria-label="Loading linked datasets" />
        </div>
        <div
          v-else-if="!linkedDataObjects || linkedDataObjects.length === 0"
          class="pa-4 text-medium-emphasis text-body-2"
        >
          No linked datasets found.
        </div>
        <div v-else class="pa-2">
          <v-list density="compact">
            <LinkedDataObjectRow
              v-for="obj in linkedDataObjects"
              :key="obj.id"
              :data-object="obj"
            />
          </v-list>
        </div>
      </ExpansionPanelItem>
    </ExpansionPanels>
  </v-container>
  </PageShell>
</template>
