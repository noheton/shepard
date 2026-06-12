<script lang="ts" setup>
import DeleteContainerButton from "~/components/container/DeleteContainerButton.vue";
import { TimeseriesContainerAccessor } from "~/composables/container/TimeseriesContainerAccessor";
import { containerTypeUrlPathSegmentMappings } from "~/utils/containerPathMappings";
import { useTimeseriesContainerChartView } from "~/composables/containers/useTimeseriesContainerChartView";
import { useTimeseriesContainerLinkedDataObjects } from "~/composables/containers/useTimeseriesContainerLinkedDataObjects";
import { useFetchTimeseriesContainerStats } from "~/composables/containers/useFetchTimeseriesContainerStats";

const { routeParams } = useContainerRouteParams();
const containerId = routeParams.value.containerId;
const urlSegment = containerTypeUrlPathSegmentMappings.TIMESERIES;

const containerAccessor = new TimeseriesContainerAccessor(containerId);

// V2-SWEEP-003-2: route param is now an appId (UUID v7); HeaderBar search still
// routes numeric id (V1-EXCEPTION, SEARCH-V2 will retire it). Resolve appId from
// the loaded container (v1 fallback path) or from the route param directly (v2 path).
const containerAppId = computed<string | null>(
  () => containerAccessor.container.value?.appId ?? (/^\d+$/.test(containerId) ? null : containerId),
);
// Numeric Neo4j id for child components still using v1 API (V1-EXCEPTION).
const containerNumericId = computed<number>(
  () => /^\d+$/.test(containerId) ? Number(containerId) : (containerAccessor.container.value?.id ?? 0),
);

const { dataObjects: linkedDataObjects, isLoading: linkedDataObjectsLoading } =
  useTimeseriesContainerLinkedDataObjects(containerAppId);

const { stats: containerStats } = useFetchTimeseriesContainerStats(containerAppId);

function fmtBytes(b: number): string {
  if (b === 0) return "0 B";
  if (b < 1_048_576) return `${(b / 1_024).toFixed(1)} KB`;
  if (b < 1_073_741_824) return `${(b / 1_048_576).toFixed(1)} MB`;
  return `${(b / 1_073_741_824).toFixed(2)} GB`;
}

// TS_CHART_VIEW1 — curated channel selection persisted per-container.
// Writers can change it; readers see it; everyone can override per-session
// with the "Show all channels" toggle below the chart.
const {
  selectedChannelKeys: persistedChannelKeys,
  updatedBy: chartViewUpdatedBy,
  saving: chartViewSaving,
  save: saveChartView,
} = useTimeseriesContainerChartView(containerAppId);

// Per-session "Show all channels" override — ignores the persisted curated
// view but doesn't change it. Pure browser state.
const showAllChannels = ref(false);
const effectiveChannelKeys = computed<string[] | undefined>(() => {
  if (showAllChannels.value) return undefined; // ⇒ chart's legacy first-N path
  return persistedChannelKeys.value;
});

// Edit mode for the channel selector. When active, render checkboxes
// alongside the chart; when not, just the chart.
const editingChartView = ref(false);
const draftChannelKeys = ref<string[]>([]);
function startEditChartView() {
  draftChannelKeys.value = [...persistedChannelKeys.value];
  editingChartView.value = true;
}
function cancelEditChartView() {
  editingChartView.value = false;
}
async function commitEditChartView() {
  const ok = await saveChartView(draftChannelKeys.value);
  if (ok) editingChartView.value = false;
}
function toggleChannelDraft(key: string, on: boolean) {
  const set = new Set(draftChannelKeys.value);
  if (on) set.add(key);
  else set.delete(key);
  draftChannelKeys.value = Array.from(set);
}

function channelKey(ch: { measurement?: string | null; device?: string | null; location?: string | null; symbolicName?: string | null; field?: string | null }): string {
  return [ch.measurement ?? "", ch.device ?? "", ch.location ?? "", ch.symbolicName ?? "", ch.field ?? ""].join("|");
}
function channelLabel(ch: { measurement?: string | null; device?: string | null; location?: string | null; symbolicName?: string | null; field?: string | null }): string {
  const parts = [ch.device, ch.field, ch.location, ch.measurement, ch.symbolicName].filter(Boolean);
  return parts.length ? parts.join(" · ") : "(unnamed channel)";
}

const deleteWarning = computed<string | undefined>(() => {
  const n = linkedDataObjects.value?.length ?? 0;
  if (n === 0) return undefined;
  return (
    `${n} data object${n === 1 ? "" : "s"} reference this container. ` +
    "Deleting it now will leave those references orphaned (the data they used to point at will no longer be retrievable)."
  );
});

const fetchData = async () => {
  await containerAccessor.fetchData();
  containerAccessor.fetchMeasurements();
  containerAccessor.fetchRoles();
};

onContainerUpdated(() => {
  fetchData();
});

const filterFiles = (files: File[]) => {
  return files.filter(file => {
    const fileName = file.name.toLowerCase();
    return fileName.endsWith(".csv");
  });
};

const uploadFile = async (file: File): Promise<void> => {
  return containerAccessor.uploadMeasurements(file);
};

fetchData();

// UX Pattern F (2026-05-24): reactive title — call useHead once with a getter.
useHead({
  title: () =>
    containerAccessor.container.value?.name
      ? `${containerAccessor.container.value.name} (Timeseries) — shepard`
      : "Timeseries Container — shepard",
});
</script>

<template>
  <PageShell>
    <v-container fluid>
      <v-row v-if="!!containerAccessor.container.value" no-gutters>
        <v-col cols="12">
          <Breadcrumbs
            :items="[
              {
                title: 'Containers',
                to: containersPath,
              },
              {
                title: containerAccessor.container.value.name,
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
                :n-items="containerAccessor.measurements.value.length"
                :name="containerAccessor.container.value.name"
                :type-label="'Timeseries Container'"
              >
                <template #buttons>
                  <UploadFilesButton
                    v-if="containerAccessor.isAllowedToEditData.value"
                    accept=".csv"
                    button-text="Upload CSV"
                    dialog-title="Upload CSV"
                    :filter="filterFiles"
                    :upload-file="uploadFile"
                    @upload-finished="() => fetchData()"
                  >
                    <template #info>
                      <TimeseriesFileUploadInfoText />
                    </template>
                  </UploadFilesButton>
                  <EditPermissionsButton
                    v-if="containerAccessor.isAllowedToEditPermissions.value"
                    :shepard-object-accessor="containerAccessor"
                  />
                  <DeleteContainerButton
                    v-if="containerAccessor.isAllowedToDelete.value"
                    :entity-name="containerAccessor.container.value.name"
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
      <!-- TS_STATS1 — size on disk + channel/point counts -->
      <div
        v-if="containerStats"
        class="d-flex flex-wrap align-center ga-2 mb-3"
      >
        <v-chip size="small" variant="tonal" prepend-icon="mdi-database-outline">
          {{ fmtBytes(containerStats.estimatedSizeBytes) }} uncompressed
        </v-chip>
        <v-chip size="small" variant="tonal" prepend-icon="mdi-chart-line">
          {{ containerStats.pointCount.toLocaleString() }} points
        </v-chip>
        <v-chip size="small" variant="tonal" prepend-icon="mdi-wave">
          {{ containerStats.channelCount }} channel{{ containerStats.channelCount === 1 ? "" : "s" }}
        </v-chip>
      </div>
      <ExpansionPanels class="mb-4" :default-open="[0, 1]">
        <ExpansionPanelItem title="Channel Overview">
          <template
            v-if="containerAccessor.isAllowedToEditData.value && !editingChartView"
            #append
          >
            <ExpansionPanelTitleButton
              icon="mdi-tune-variant"
              text="Edit channels"
              @click="startEditChartView"
            />
          </template>
          <TimeseriesAllChannelsChart
            :container-id="containerNumericId"
            :container-app-id="containerAppId"
            :measurements="containerAccessor.measurements.value"
            :selected-channel-keys="effectiveChannelKeys"
          />
          <!-- Persisted-view summary + "show all" override (session-only) -->
          <div
            v-if="persistedChannelKeys.length > 0 || showAllChannels"
            class="d-flex flex-wrap align-center ga-3 mt-2 px-2 text-caption text-medium-emphasis"
          >
            <span v-if="!showAllChannels && persistedChannelKeys.length > 0">
              Curated view — {{ persistedChannelKeys.length }} channel{{ persistedChannelKeys.length === 1 ? "" : "s" }}
              <span v-if="chartViewUpdatedBy">
                · set by {{ chartViewUpdatedBy }}
              </span>
            </span>
            <span v-if="showAllChannels">
              Showing all channels (overrides the curated view for this session)
            </span>
            <v-spacer />
            <v-btn
              v-if="persistedChannelKeys.length > 0"
              variant="text"
              size="x-small"
              @click="showAllChannels = !showAllChannels"
            >
              {{ showAllChannels ? "Use curated view" : "Show all channels" }}
            </v-btn>
          </div>
          <!-- Edit mode: checkboxes for each available channel -->
          <div
            v-if="editingChartView"
            class="mt-4 chart-view-editor pa-3"
          >
            <div class="d-flex align-center mb-2">
              <div class="text-body-2 font-weight-medium">
                Pick channels for the Channel Overview chart
              </div>
              <v-spacer />
              <v-btn variant="text" size="small" @click="cancelEditChartView">Cancel</v-btn>
              <v-btn
                variant="flat"
                color="primary"
                size="small"
                :loading="chartViewSaving"
                @click="commitEditChartView"
              >Save</v-btn>
            </div>
            <div class="text-caption text-medium-emphasis mb-2">
              The selection is shared — everyone viewing this container sees the
              same default chart. Per-session "show all" override remains.
            </div>
            <div class="d-flex flex-wrap ga-x-4 ga-y-1">
              <v-checkbox
                v-for="ch in containerAccessor.measurements.value"
                :key="channelKey(ch)"
                :model-value="draftChannelKeys.includes(channelKey(ch))"
                :label="channelLabel(ch)"
                density="compact"
                hide-details
                @update:model-value="(v) => toggleChannelDraft(channelKey(ch), !!v)"
              />
            </div>
          </div>
        </ExpansionPanelItem>
        <!-- SA-CONT: container-level semantic annotations -->
        <ExpansionPanelItem title="Semantic Annotations">
          <template
            v-if="containerAccessor.isAllowedToEditData.value"
            #append
          >
            <AddAnnotationButton
              :annotated="new AnnotatedTimeseriesContainer(containerAppId ?? '')"
            />
          </template>
          <SemanticAnnotationList
            :annotated="new AnnotatedTimeseriesContainer(containerAppId ?? '')"
            :can-delete="!!containerAccessor.isAllowedToEditData.value"
          />
        </ExpansionPanelItem>
      </ExpansionPanels>
      <!-- UX-PIN1: containerPath is stored with each pin so the PersonalDigest
           tile can navigate back to this container. -->
      <TimeseriesMeasurementsTable
        :container-id="containerNumericId"
        :container-app-id="containerAppId ?? ''"
        :is-allowed-to-edit-data="containerAccessor.isAllowedToEditData.value"
        :measurements="containerAccessor.measurements.value"
        :container-path="`${containersPath}${urlSegment}${containerId}`"
      />
      <!-- TS-SEMANTIC-REST: channel-level annotation surface (shepardId-keyed v2 endpoint) -->
      <ExpansionPanels class="mt-4" :default-open="[]">
        <ExpansionPanelItem
          title="Channel Annotations"
          :count="containerAccessor.measurements.value.length"
        >
          <ChannelAnnotationsPane
            :container-app-id="containerAppId ?? ''"
            :measurements="containerAccessor.measurements.value"
            :is-allowed-to-edit-data="!!containerAccessor.isAllowedToEditData.value"
          />
        </ExpansionPanelItem>
      </ExpansionPanels>
      <!-- CC1b: Referenced by — wired to GET /v2/timeseries-containers/{id}/linked-data-objects -->
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
              <ReferencedByRow
                v-for="obj in linkedDataObjects"
                :key="obj.id"
                :data-object="obj"
                :container-id="containerNumericId"
              />
            </v-list>
          </div>
        </ExpansionPanelItem>
      </ExpansionPanels>
    </v-container>
  </PageShell>
</template>

<style lang="scss" scoped>
.chart-view-editor {
  background: rgba(var(--v-border-color), 0.05);
  border-left: 3px solid rgb(var(--v-theme-primary));
  border-radius: 4px;
}
</style>
