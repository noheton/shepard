<script setup lang="ts">
import {
  TimeseriesReferenceApi,
  type DataObject,
  type Timeseries,
  type TimeseriesReference,
} from "@dlr-shepard/backend-client";
import { useShepardApi } from "~/composables/common/api/useShepardApi";

const props = defineProps<{
  dataObject: DataObject;
  containerId: number;
}>();

const expanded = ref(false);
const loading = ref(false);
const loaded = ref(false);
const refsForContainer = ref<TimeseriesReference[]>([]);

async function fetchReferences() {
  if (loaded.value || loading.value) return;
  loading.value = true;
  try {
    const all = await useShepardApi(TimeseriesReferenceApi)
      .value.getAllTimeseriesReferences({
        collectionId: props.dataObject.collectionId,
        dataObjectId: props.dataObject.id,
      });
    refsForContainer.value = all.filter(
      r => r.timeseriesContainerId === props.containerId,
    );
    loaded.value = true;
  } catch (e) {
    handleError(e as Error, "fetching timeseries references for linked data object");
  } finally {
    loading.value = false;
  }
}

function onToggleExpand() {
  expanded.value = !expanded.value;
  if (expanded.value) fetchReferences();
}

function channelLabel(ts: Timeseries): string {
  return [ts.device, ts.field, ts.location, ts.measurement, ts.symbolicName]
    .filter(Boolean)
    .join(" · ");
}

function nanosToHumanRange(startNs: number, endNs: number): string {
  const startMs = startNs / 1_000_000;
  const endMs = endNs / 1_000_000;
  const fmt = (ms: number) =>
    new Date(ms).toISOString().replace("T", " ").slice(0, 19) + " UTC";
  return `${fmt(startMs)}  →  ${fmt(endMs)}`;
}
</script>

<template>
  <v-list-item
    prepend-icon="mdi-database-outline"
    :title="dataObject.name"
    :subtitle="dataObject.status ? `Status: ${dataObject.status}` : undefined"
    class="rounded mb-1"
  >
    <!-- Data object's own semantic annotations, shown inline so users
         scanning the linked-by list can spot tagged datasets at a glance. -->
    <div class="dataobject-annotations">
      <SemanticAnnotationList
        :annotated="new AnnotatedDataObject(dataObject.collectionId, dataObject.id)"
        :can-delete="false"
      />
    </div>
    <template #append>
      <div class="d-flex ga-1 align-center">
        <v-btn
          :to="`/collections/${dataObject.collectionId}`"
          variant="text"
          size="x-small"
          icon="mdi-folder-outline"
          title="Open collection"
        />
        <v-btn
          :to="`/collections/${dataObject.collectionId}/dataObjects/${dataObject.id}`"
          variant="text"
          size="x-small"
          icon="mdi-arrow-right"
          title="Open data object"
        />
        <v-btn
          variant="text"
          size="x-small"
          :icon="expanded ? 'mdi-chevron-up' : 'mdi-chevron-down'"
          :title="expanded ? 'Hide referenced slices' : 'Show referenced slices'"
          @click="onToggleExpand"
        />
      </div>
    </template>
  </v-list-item>

  <div v-if="expanded" class="referenced-detail">
    <div v-if="loading" class="d-flex align-center ga-2 py-2 px-4 text-medium-emphasis text-body-2">
      <v-progress-circular indeterminate size="14" width="2" />
      Loading referenced slices…
    </div>
    <div
      v-else-if="loaded && refsForContainer.length === 0"
      class="px-4 py-2 text-medium-emphasis text-body-2"
    >
      No timeseries references from this data object point at this container.
    </div>
    <div v-else>
      <div
        v-for="ref in refsForContainer"
        :key="ref.id"
        class="reference-card pa-3 mb-2"
      >
        <div class="d-flex align-baseline mb-1">
          <span class="text-body-2 font-weight-medium">{{ ref.name }}</span>
          <v-spacer />
          <span class="text-caption text-medium-emphasis">
            {{ ref.timeseries.length }}
            channel{{ ref.timeseries.length === 1 ? "" : "s" }}
          </span>
        </div>
        <div class="text-caption text-medium-emphasis mb-2 font-mono">
          {{ nanosToHumanRange(ref.start, ref.end) }}
        </div>
        <div class="d-flex flex-wrap ga-1 mb-2">
          <v-chip
            v-for="(ts, idx) in ref.timeseries"
            :key="idx"
            size="x-small"
            variant="tonal"
            :title="channelLabel(ts)"
          >
            {{ ts.field || ts.symbolicName || ts.device || "channel" }}
          </v-chip>
        </div>
        <!-- Semantic annotations attached to this TimeseriesReference -->
        <div class="annotation-row">
          <SemanticAnnotationList
            :annotated="new AnnotatedReference(dataObject.collectionId, dataObject.id, ref.id)"
            :can-delete="false"
          />
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.referenced-detail {
  margin-left: 48px;
  margin-right: 8px;
  margin-bottom: 4px;
}
/* Compact annotation chips inside the list-item row body */
.dataobject-annotations :deep(ul) {
  gap: 4px 8px;
  margin-top: 2px;
  margin-bottom: 0;
}
.dataobject-annotations :deep(.v-chip) {
  font-size: 0.7rem;
}
.reference-card {
  background: rgba(var(--v-border-color), 0.05);
  border-left: 3px solid rgb(var(--v-theme-primary));
  border-radius: 4px;
}
.font-mono {
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
}
</style>
