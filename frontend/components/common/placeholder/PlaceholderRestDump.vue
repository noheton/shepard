<script setup lang="ts">
// PlaceholderRestDump — raw REST surface dump for power users until the
// real UI lands. Visible in **advanced mode only** per
// memory/feedback_basic_advanced_superset.md: basic mode users see only
// description + impl-status; advanced mode adds the raw JSON.
//
// Endpoint may be null (designed-not-shipped placeholders); the component
// renders a "no endpoint yet" hint instead of fetching.

import { useAdvancedMode } from "~/composables/context/useAdvancedMode";

const props = defineProps<{
  endpoint: string | null;
  hint?: string;
}>();

const { advancedMode } = useAdvancedMode();

const data = ref<unknown>(null);
const error = ref<string | null>(null);
const isLoading = ref(false);

async function fetchRaw() {
  if (!props.endpoint || !advancedMode.value) return;
  isLoading.value = true;
  error.value = null;
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
    const res = await fetch(v2Base + props.endpoint, { headers });
    if (!res.ok) {
      error.value = `${res.status} ${res.statusText}`;
      return;
    }
    data.value = await res.json();
  } catch (e: unknown) {
    error.value = e instanceof Error ? e.message : String(e);
  } finally {
    isLoading.value = false;
  }
}

watch(
  () => [advancedMode.value, props.endpoint],
  () => {
    if (advancedMode.value && props.endpoint) fetchRaw();
  },
  { immediate: true },
);
</script>

<template>
  <v-card v-if="advancedMode" variant="outlined" class="mt-4">
    <v-card-title class="text-subtitle-1">
      <v-icon size="small" class="mr-1">mdi-code-json</v-icon>
      Raw REST dump
      <v-chip size="x-small" class="ml-2" variant="tonal" color="warning">
        advanced mode
      </v-chip>
    </v-card-title>
    <v-card-subtitle v-if="hint" class="text-caption">{{ hint }}</v-card-subtitle>
    <v-card-text>
      <div v-if="!endpoint" class="text-caption text-medium-emphasis">
        Backend endpoint not shipped yet — nothing to fetch.
      </div>
      <div v-else-if="isLoading" class="text-caption">Loading…</div>
      <div v-else-if="error" class="text-caption text-error">
        Failed to fetch <code>{{ endpoint }}</code>: {{ error }}
      </div>
      <pre v-else class="text-caption rest-dump">{{ JSON.stringify(data, null, 2) }}</pre>
    </v-card-text>
  </v-card>
</template>

<style scoped>
.rest-dump {
  max-height: 400px;
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-word;
}
</style>
