<script setup lang="ts">
// /snapshots/diff — global snapshot picker + diff viewer.
//
// SNAPSHOT-LIST-1-FE (2026-05-31): replaces the previous helper banner +
// raw-appId inputs with two `v-autocomplete` pickers backed by the global
// list endpoint shipped 2026-05-31 (backend 1935128eb). Each option
// renders `name · collectionName · createdAt · appId-prefix`. An
// "Advanced: raw appIds" toggle keeps the previous text-field shape
// available for power users with appIds in hand from MCP / scripts.
//
// Backed by GET /v2/snapshots[?collectionAppId=…] for the picker and
// GET /v2/snapshots/{a}/diff/{b} for the comparison.

import PlaceholderImplStatus from "~/components/common/placeholder/PlaceholderImplStatus.vue";
import {
  useSnapshotList,
  type SnapshotListItem,
} from "~/composables/useSnapshotList";

useHead({ title: "Snapshot diff | shepard" });

const route = useRoute();
const aAppId = ref<string>((route.query.a as string) ?? "");
const bAppId = ref<string>((route.query.b as string) ?? "");
const result = ref<unknown>(null);
const error = ref<string | null>(null);
const isLoading = ref(false);
const showAdvanced = ref<boolean>(false);

const {
  items: snapshots,
  isLoading: loadingSnapshots,
  error: snapshotsError,
  fetchPage,
} = useSnapshotList();

onMounted(() => {
  void fetchPage({ size: 200 });
});

const snapshotOptions = computed(() =>
  snapshots.value.map((s: SnapshotListItem) => ({
    value: s.appId,
    title: optionTitle(s),
    raw: s,
  })),
);

function optionTitle(s: SnapshotListItem): string {
  const when = formatCreated(s.createdAt);
  const coll = s.collectionName ?? "—";
  const head = `${s.appId.slice(0, 8)}…`;
  return `${s.name} · ${coll} · ${when} · ${head}`;
}

function formatCreated(iso: string): string {
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return iso;
  return d.toISOString().slice(0, 16).replace("T", " ");
}

async function runDiff() {
  if (!aAppId.value || !bAppId.value) {
    error.value = "Both snapshot appIds are required.";
    return;
  }
  isLoading.value = true;
  error.value = null;
  result.value = null;
  try {
    const { data: auth } = useAuth();
    const config = useRuntimeConfig().public;
    const explicit = config.backendV2ApiUrl as string | undefined;
    const v2Base =
      explicit && explicit.length > 0
        ? explicit
        : (config.backendApiUrl as string).replace(/\/shepard\/api\/?$/, "");
    const headers: Record<string, string> = { Accept: "application/json" };
    if (auth.value?.accessToken) {
      headers["Authorization"] = `Bearer ${auth.value.accessToken}`;
    }
    const url =
      v2Base +
      `/v2/snapshots/${encodeURIComponent(aAppId.value)}/diff/${encodeURIComponent(bAppId.value)}`;
    const res = await fetch(url, { headers });
    const body = await res.text();
    if (!res.ok) {
      error.value = `${res.status} ${res.statusText}\n${body}`;
      return;
    }
    try {
      result.value = JSON.parse(body);
    } catch {
      result.value = body;
    }
  } catch (e: unknown) {
    error.value = e instanceof Error ? e.message : String(e);
  } finally {
    isLoading.value = false;
  }
}
</script>

<template>
  <v-container fluid style="max-width: 2400px; margin: 0 auto">
    <div class="d-flex flex-column ga-2 mb-4">
      <h4 class="text-h4">Snapshot diff</h4>
      <p class="text-body-1 text-medium-emphasis">
        Compare two Collection snapshots — what DataObjects were added,
        removed, or modified between them. Backed by
        <code>GET /v2/snapshots/{a}/diff/{b}</code>.
      </p>
    </div>

    <v-alert
      v-if="snapshotsError"
      type="error"
      variant="tonal"
      density="compact"
      class="mb-3"
    >
      Snapshot list error: {{ snapshotsError }}
    </v-alert>

    <v-row>
      <v-col cols="12" md="6">
        <v-autocomplete
          v-model="aAppId"
          :items="snapshotOptions"
          item-title="title"
          item-value="value"
          label="Snapshot A (older)"
          variant="outlined"
          density="comfortable"
          :loading="loadingSnapshots"
          clearable
          data-testid="picker-a"
          hint="Searchable: name, collection, appId prefix"
          persistent-hint
        />
      </v-col>
      <v-col cols="12" md="6">
        <v-autocomplete
          v-model="bAppId"
          :items="snapshotOptions"
          item-title="title"
          item-value="value"
          label="Snapshot B (newer)"
          variant="outlined"
          density="comfortable"
          :loading="loadingSnapshots"
          clearable
          data-testid="picker-b"
          hint="Searchable: name, collection, appId prefix"
          persistent-hint
        />
      </v-col>
    </v-row>

    <div class="d-flex align-center ga-2 mt-2 mb-2">
      <v-btn color="primary" :loading="isLoading" @click="runDiff">
        <v-icon start>mdi-vector-difference</v-icon> Compare
      </v-btn>
      <v-switch
        v-model="showAdvanced"
        label="Advanced: raw appIds"
        color="primary"
        hide-details
        density="compact"
        data-testid="show-advanced"
      />
    </div>

    <v-expand-transition>
      <v-row v-if="showAdvanced" dense class="mb-2">
        <v-col cols="12" md="6">
          <v-text-field
            v-model="aAppId"
            label="Snapshot A appId (raw)"
            density="compact"
            variant="outlined"
            hint="Power-user override — bypasses the picker"
            persistent-hint
            data-testid="raw-a"
          />
        </v-col>
        <v-col cols="12" md="6">
          <v-text-field
            v-model="bAppId"
            label="Snapshot B appId (raw)"
            density="compact"
            variant="outlined"
            hint="Power-user override — bypasses the picker"
            persistent-hint
            data-testid="raw-b"
          />
        </v-col>
      </v-row>
    </v-expand-transition>

    <v-alert v-if="error" type="error" class="mt-3" variant="tonal">
      <pre class="text-caption">{{ error }}</pre>
    </v-alert>
    <v-card v-if="result" variant="outlined" class="mt-3">
      <v-card-title class="text-subtitle-1">Diff result</v-card-title>
      <v-card-text>
        <pre class="text-caption diff-result">{{ JSON.stringify(result, null, 2) }}</pre>
      </v-card-text>
    </v-card>
    <PlaceholderImplStatus
      backend="shipped"
      backlog-row="SNAP-DIFF"
      design-doc="aidocs/platform/25-neo4j-id-migration-design.md"
      endpoint="/v2/snapshots/{a}/diff/{b}"
      notes="Picker backed by GET /v2/snapshots (SNAPSHOT-LIST-1-FE shipped 2026-05-31). Structured visualisation queued under SNAP-DIFF-UI-FOLLOWUP."
    />
  </v-container>
</template>

<style scoped>
.diff-result {
  max-height: 500px;
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-word;
}
</style>
