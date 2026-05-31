<script setup lang="ts">
/**
 * TemplateAutocomplete — UI-SHAPES-RENDER-PICKERS-001 + Q.
 *
 * Pick a ShepardTemplate by name. Backed by GET /v2/templates?kind=<kind>.
 * Returns the selected template's appId via v-model.
 *
 * Default kind is "VIEW_RECIPE" (the only consumer today is the shapes
 * render playground). Pass `kind="*"` to list all templates.
 *
 * The advanced-mode "raw appId" fallback stays on the parent page — power
 * users may have an appId in hand from MCP / scripts, and this picker is
 * not a hard block on that flow.
 */

import {
  formatOption,
  type TemplateListItem,
  templatesUrl,
} from "~/utils/templateAutocomplete";

const props = withDefaults(
  defineProps<{ kind?: string; label?: string }>(),
  { kind: "VIEW_RECIPE", label: "Template" },
);

const selectedAppId = defineModel<string>("appId", { default: "" });

const items = ref<TemplateListItem[]>([]);
const isLoading = ref(false);
const fetchError = ref<string | null>(null);

function getV2Base(): string {
  const config = useRuntimeConfig().public;
  const explicit = config.backendV2ApiUrl as string | undefined;
  return explicit && explicit.length > 0
    ? explicit
    : (config.backendApiUrl as string).replace(/\/shepard\/api\/?$/, "");
}

function getAuthHeaders(): Record<string, string> {
  const { data: auth } = useAuth();
  const h: Record<string, string> = { Accept: "application/json" };
  if (auth.value?.accessToken) {
    h["Authorization"] = `Bearer ${auth.value.accessToken}`;
  }
  return h;
}

/** GET /v2/templates?kind=<kind>. Public surface; returns 200 + an array. */
async function fetchTemplates() {
  isLoading.value = true;
  fetchError.value = null;
  try {
    const url = templatesUrl(getV2Base(), props.kind);
    const res = await fetch(url, { headers: getAuthHeaders() });
    if (!res.ok) {
      fetchError.value = `${res.status} ${res.statusText}`;
      return;
    }
    items.value = (await res.json()) as TemplateListItem[];
  } catch (e) {
    fetchError.value = e instanceof Error ? e.message : String(e);
  } finally {
    isLoading.value = false;
  }
}

onMounted(() => void fetchTemplates());

// Format each option as "Name (8-char appId snippet)". Pure helper kept
// in `utils/templateAutocomplete.ts` so a Vitest unit covers it without
// mounting Vue.
const options = computed(() => items.value.map(t => formatOption(t, props.kind)));
</script>

<template>
  <div>
    <v-autocomplete
      v-model="selectedAppId"
      :items="options"
      :label="label"
      :loading="isLoading"
      density="compact"
      variant="outlined"
      clearable
      hide-details="auto"
      :error-messages="fetchError ? [`Failed to load templates: ${fetchError}`] : []"
      :hint="`Pick a ${kind === '*' ? 'template' : kind + ' template'} — picker reads /v2/templates`"
      persistent-hint
    />
  </div>
</template>
