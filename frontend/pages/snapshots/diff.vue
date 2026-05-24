<script setup lang="ts">
// /snapshots/diff — minimal snapshot-diff viewer.
// Backed by GET /v2/snapshots/{aAppId}/diff/{bAppId}.
// Two appId inputs + a Compare button + JSON dump of the diff.

import PlaceholderImplStatus from "~/components/common/placeholder/PlaceholderImplStatus.vue";

useHead({ title: "Snapshot diff | shepard" });

const route = useRoute();
const aAppId = ref<string>((route.query.a as string) ?? "");
const bAppId = ref<string>((route.query.b as string) ?? "");
const result = ref<unknown>(null);
const error = ref<string | null>(null);
const isLoading = ref(false);

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
  <v-container>
    <div class="d-flex flex-column ga-2 mb-4">
      <h4 class="text-h4">Snapshot diff</h4>
      <p class="text-body-1 text-medium-emphasis">
        Compare two Collection snapshots — what DataObjects were added,
        removed, or modified between them. Backed by
        <code>GET /v2/snapshots/{a}/diff/{b}</code>.
      </p>
    </div>
    <v-row>
      <v-col cols="12" md="6">
        <v-text-field
          v-model="aAppId"
          label="Snapshot A (older) appId"
          density="compact"
          variant="outlined"
        />
      </v-col>
      <v-col cols="12" md="6">
        <v-text-field
          v-model="bAppId"
          label="Snapshot B (newer) appId"
          density="compact"
          variant="outlined"
        />
      </v-col>
    </v-row>
    <v-btn color="primary" :loading="isLoading" @click="runDiff">
      <v-icon start>mdi-vector-difference</v-icon> Compare
    </v-btn>
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
      notes="Raw diff JSON shown. Structured visualisation queued under SNAP-DIFF-UI-FOLLOWUP."
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
