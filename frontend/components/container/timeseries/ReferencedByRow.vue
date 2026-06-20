<script setup lang="ts">
import { type DataObject } from "@dlr-shepard/backend-client";
import { readDataObjectAppId } from "~/utils/appId";
import { useCollectionAppIdResolver } from "~/composables/context/useCollectionAppIdResolver";

interface TimeseriesChannel {
  device?: string | null;
  field?: string | null;
  location?: string | null;
  measurement?: string | null;
  symbolicName?: string | null;
}

interface TimeseriesRefV2 {
  appId: string;
  name: string;
  payload: {
    start: number;
    end: number;
    timeseriesContainerId: number;
    timeseries: TimeseriesChannel[];
  };
}

function v2BaseUrl(): string {
  const config = useRuntimeConfig().public;
  const explicit = config.backendV2ApiUrl as string | undefined;
  if (explicit && explicit.length > 0) return explicit.replace(/\/$/, "");
  return (config.backendApiUrl as string)
    .replace(/\/shepard\/api\/?$/, "")
    .replace(/\/$/, "");
}

const props = defineProps<{
  dataObject: DataObject;
  containerId: number;
}>();

// V2-LINKS: routes carry the UUID-v7 appId, never the numeric Neo4j id.
// The DataObject carries its own appId but only a numeric collectionId, so
// we resolve the owning collection's appId via the shared cache.
const { resolve: resolveCollectionAppId, peek: peekCollectionAppId } =
  useCollectionAppIdResolver();
const collectionAppIdRef = ref<string | null>(
  peekCollectionAppId(props.dataObject.collectionId),
);
onMounted(async () => {
  collectionAppIdRef.value = await resolveCollectionAppId(
    props.dataObject.collectionId,
  );
});

const doAppId = computed(() => readDataObjectAppId(props.dataObject));
const collectionHref = computed(() =>
  collectionAppIdRef.value ? `/collections/${collectionAppIdRef.value}` : null,
);
const dataObjectHref = computed(() =>
  collectionAppIdRef.value && doAppId.value
    ? `/collections/${collectionAppIdRef.value}/dataObjects/${doAppId.value}`
    : null,
);

const expanded = ref(false);
const loading = ref(false);
const loaded = ref(false);
const refsForContainer = ref<TimeseriesRefV2[]>([]);

async function fetchReferences() {
  if (loaded.value || loading.value) return;
  const appId = doAppId.value;
  if (!appId) return;
  loading.value = true;
  try {
    const { data: session } = useAuth();
    const accessToken = session.value?.accessToken;
    const url = `${v2BaseUrl()}/v2/references?kind=timeseries&dataObjectAppId=${encodeURIComponent(appId)}`;
    const resp = await fetch(url, {
      headers: {
        ...(accessToken ? { Authorization: `Bearer ${accessToken}` } : {}),
        Accept: "application/json",
      },
    });
    if (!resp.ok) throw new Error(`GET /v2/references?kind=timeseries returned ${resp.status}`);
    const all = (await resp.json()) as TimeseriesRefV2[];
    refsForContainer.value = all.filter(
      r => r.payload?.timeseriesContainerId === props.containerId,
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

function channelLabel(ts: TimeseriesChannel): string {
  return [ts.device, ts.field, ts.location, ts.measurement, ts.symbolicName]
    .filter(Boolean)
    .join(" · ");
}

function nanosToHumanRange(startNs: number | undefined, endNs: number | undefined): string {
  if (startNs == null || endNs == null) return "–";
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
        v-if="doAppId"
        :annotated="new AnnotatedDataObject(doAppId)"
        :can-delete="false"
      />
    </div>
    <template #append>
      <div class="d-flex ga-1 align-center">
        <v-btn
          v-if="collectionHref"
          :to="collectionHref"
          variant="text"
          size="x-small"
          icon="mdi-folder-outline"
          title="Open collection"
          aria-label="Open collection"
        />
        <v-btn
          v-if="dataObjectHref"
          :to="dataObjectHref"
          variant="text"
          size="x-small"
          icon="mdi-arrow-right"
          title="Open data object"
          aria-label="Open data object"
        />
        <v-btn
          variant="text"
          size="x-small"
          :icon="expanded ? 'mdi-chevron-up' : 'mdi-chevron-down'"
          :title="expanded ? 'Hide referenced slices' : 'Show referenced slices'"
          :aria-label="expanded ? 'Hide referenced slices' : 'Show referenced slices'"
          @click="onToggleExpand"
        />
      </div>
    </template>
  </v-list-item>

  <div v-if="expanded" class="referenced-detail">
    <div v-if="loading" role="status" class="d-flex align-center ga-2 py-2 px-4 text-medium-emphasis text-body-2">
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
        :key="ref.appId"
        class="reference-card pa-3 mb-2"
      >
        <div class="d-flex align-baseline mb-1">
          <span class="text-body-2 font-weight-medium">{{ ref.name }}</span>
          <v-spacer />
          <span class="text-caption text-medium-emphasis">
            {{ (ref.payload?.timeseries ?? []).length }}
            channel{{ (ref.payload?.timeseries ?? []).length === 1 ? "" : "s" }}
          </span>
        </div>
        <div class="text-caption text-medium-emphasis mb-2 font-mono">
          {{ nanosToHumanRange(ref.payload?.start, ref.payload?.end) }}
        </div>
        <div class="d-flex flex-wrap ga-1 mb-2">
          <v-chip
            v-for="(ts, idx) in (ref.payload?.timeseries ?? [])"
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
            v-if="ref.appId"
            :annotated="new AnnotatedReference(ref.appId, 'TimeseriesReference')"
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
