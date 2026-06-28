<script setup lang="ts">
// UI-GAP-3 — admin config registry overview. Calls GET /v2/admin/config to list
// all registered ConfigDescriptor features, then lazily fetches each feature's
// current JSON shape on panel expansion. Features with a bespoke admin pane link
// there directly; plugin-registered features without a bespoke pane show an
// inline JSON viewer (PATCH editor is UI-GAP-3-2, deferred).
import { AdminFragments } from "./adminMenuItems";

interface ConfigFeature {
  feature: string;
  description: string;
}

// Map feature keys that have a dedicated admin pane to their AdminFragment hash.
const BESPOKE_PANE: Record<string, string> = {
  semantic: AdminFragments.SEMANTIC_CONFIG,
  jupyter: AdminFragments.JUPYTER,
  unhide: AdminFragments.UNHIDE,
  "legacy-v1": AdminFragments.LEGACY_V1,
  "sql-timeseries": AdminFragments.SQL_TIMESERIES,
  aas: AdminFragments.AAS_CONFIG,
};

function v2BaseUrl(): string {
  const config = useRuntimeConfig().public;
  const explicit = config.backendV2ApiUrl as string | undefined;
  return explicit && explicit.length > 0
    ? explicit.replace(/\/$/, "")
    : (config.backendApiUrl as string).replace(/\/shepard\/api\/?$/, "");
}

async function authFetch(path: string): Promise<Response> {
  const { data: session } = useAuth();
  const token = session.value?.accessToken;
  const headers: Record<string, string> = { Accept: "application/json" };
  if (token) headers.Authorization = `Bearer ${token}`;
  return fetch(`${v2BaseUrl()}${path}`, { headers });
}

const features = ref<ConfigFeature[]>([]);
const isLoading = ref(true);
const configDetails = ref<Record<string, unknown>>({});
const loadingFeatures = ref<string[]>([]);

async function loadFeatures() {
  isLoading.value = true;
  try {
    const resp = await authFetch("/v2/admin/config");
    if (!resp.ok) throw new Error(`HTTP ${resp.status}`);
    features.value = (await resp.json()) as ConfigFeature[];
  } catch (e) {
    handleError(e, "fetching admin config features");
    features.value = [];
  } finally {
    isLoading.value = false;
  }
}

async function loadDetail(feature: string) {
  if (feature in configDetails.value || loadingFeatures.value.includes(feature)) return;
  loadingFeatures.value = [...loadingFeatures.value, feature];
  try {
    const resp = await authFetch(`/v2/admin/config/${encodeURIComponent(feature)}`);
    if (!resp.ok) throw new Error(`HTTP ${resp.status}`);
    configDetails.value = { ...configDetails.value, [feature]: await resp.json() };
  } catch (e) {
    handleError(e, `fetching config for "${feature}"`);
    configDetails.value = { ...configDetails.value, [feature]: null };
  } finally {
    loadingFeatures.value = loadingFeatures.value.filter(f => f !== feature);
  }
}

function onPanelUpdate(openIndices: unknown) {
  const indices = Array.isArray(openIndices) ? (openIndices as number[]) : [];
  for (const i of indices) {
    const f = features.value[i];
    if (f) loadDetail(f.feature);
  }
}

loadFeatures();
</script>

<template>
  <div :id="AdminFragments.CONFIG_OVERVIEW" class="d-flex flex-column ga-4">
    <h4 class="text-h4">Runtime Config Registry</h4>
    <p class="text-body-2 text-medium-emphasis">
      All registered runtime-configurable features
      (<code>GET /v2/admin/config</code>). Expand a row to inspect the current
      JSON shape. Features with a dedicated pane show an
      <strong>Open pane</strong> button.
    </p>

    <centered-loading-spinner v-if="isLoading" />

    <EmptyListIcon
      v-else-if="features.length === 0"
      label="No config features registered"
    />

    <v-expansion-panels
      v-else
      variant="accordion"
      multiple
      @update:model-value="onPanelUpdate"
    >
      <v-expansion-panel v-for="f in features" :key="f.feature">
        <v-expansion-panel-title>
          <div class="d-flex align-center w-100 ga-2">
            <code class="text-body-2 font-weight-medium flex-shrink-0">{{ f.feature }}</code>
            <span class="text-body-2 text-medium-emphasis flex-grow-1 text-truncate ml-1">
              — {{ f.description }}
            </span>
            <v-btn
              v-if="BESPOKE_PANE[f.feature]"
              :href="`#${BESPOKE_PANE[f.feature]}`"
              size="x-small"
              variant="tonal"
              color="primary"
              class="flex-shrink-0"
              @click.stop
            >
              Open pane
            </v-btn>
          </div>
        </v-expansion-panel-title>
        <v-expansion-panel-text>
          <centered-loading-spinner
            v-if="loadingFeatures.includes(f.feature)"
            size="24"
          />
          <pre
            v-else-if="f.feature in configDetails"
            class="text-body-2 pa-3 rounded bg-surface-variant overflow-auto"
            style="max-height: 320px; white-space: pre-wrap; word-break: break-all"
          >{{ JSON.stringify(configDetails[f.feature], null, 2) }}</pre>
        </v-expansion-panel-text>
      </v-expansion-panel>
    </v-expansion-panels>
  </div>
</template>

<style scoped lang="scss"></style>
