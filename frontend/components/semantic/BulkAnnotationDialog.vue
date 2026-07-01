<script lang="ts" setup>
/**
 * SEMANTIC-ANNOTATE-BULK-UI-1 — multi-subject bulk annotation dialog.
 *
 * Annotates N entities in one call via POST /v2/annotations/bulk (up to 100
 * items; ships the same CreateAnnotationIO shape repeated per subjectAppId).
 * Best-effort: failed rows are reported inline without aborting the batch.
 *
 * Usage:
 *   <BulkAnnotationDialog
 *     v-model:show-dialog="showBulk"
 *     :subject-app-ids="selectedAppIds"
 *     subject-kind="DataObject"
 *     @bulk-annotated="onBulkDone"
 *   />
 */
import {
  useTermSearch,
  type TermSuggestion,
} from "~/composables/context/useTermSearch";

const props = defineProps<{
  subjectAppIds: string[];
  subjectKind: string;
}>();

const showDialog = defineModel<boolean>("showDialog", {
  required: true,
  default: false,
});

const emit = defineEmits<{
  "bulk-annotated": [succeeded: number, failed: number];
}>();

const { search } = useTermSearch();

const propertyIri = ref<string>("");
const valueIri = ref<string>("");
const propertyPreview = ref<TermSuggestion | null>(null);
const valuePreview = ref<TermSuggestion | null>(null);

const propertySuggestions = ref<TermSuggestion[]>([]);
const propertyLoading = ref(false);
const valueSuggestions = ref<TermSuggestion[]>([]);
const valueLoading = ref(false);

const processing = ref(false);

interface BulkResult { succeeded: number; failed: number }
const lastResult = ref<BulkResult | null>(null);

const isValid = computed(
  () => propertyIri.value.trim() !== "" && valueIri.value.trim() !== "",
);

function reset() {
  propertyIri.value = "";
  valueIri.value = "";
  propertyPreview.value = null;
  valuePreview.value = null;
  propertySuggestions.value = [];
  valueSuggestions.value = [];
  lastResult.value = null;
}

watch(showDialog, (v) => { if (v) reset(); });

// ─── Base URL + auth ──────────────────────────────────────────────────────────

function v2BaseUrl(): string {
  const config = useRuntimeConfig().public;
  const explicit = (config as { backendV2ApiUrl?: string }).backendV2ApiUrl;
  if (explicit && explicit.length > 0) return explicit.replace(/\/$/, "");
  return (config.backendApiUrl as string)
    .replace(/\/shepard\/api\/?$/, "")
    .replace(/\/$/, "");
}

async function authHeaders(): Promise<Record<string, string>> {
  const { data: session } = useAuth();
  const accessToken = session.value?.accessToken;
  if (!accessToken) throw new Error("Not authenticated");
  return {
    Authorization: `Bearer ${accessToken}`,
    Accept: "application/json",
    "Content-Type": "application/json",
  };
}

// ─── Predicate autocomplete ───────────────────────────────────────────────────

let propertyDebounce: ReturnType<typeof setTimeout> | null = null;

function onPropertySearch(q: string) {
  if (propertyDebounce) clearTimeout(propertyDebounce);
  if (!q || q.length < 2) { propertySuggestions.value = []; return; }
  propertyDebounce = setTimeout(async () => {
    propertyLoading.value = true;
    propertySuggestions.value = await search(q);
    propertyLoading.value = false;
  }, 250);
}

function onPropertyUpdate(v: string | TermSuggestion | null) {
  if (!v) { propertyIri.value = ""; propertyPreview.value = null; return; }
  if (typeof v === "string") {
    propertyIri.value = v;
    propertyPreview.value = propertySuggestions.value.find(s => s.uri === v || s.label === v) ?? null;
  } else {
    propertyIri.value = v.uri;
    propertyPreview.value = v;
  }
}

// ─── Value autocomplete ───────────────────────────────────────────────────────

let valueDebounce: ReturnType<typeof setTimeout> | null = null;

function onValueSearch(q: string) {
  if (valueDebounce) clearTimeout(valueDebounce);
  if (!q || q.length < 2) { valueSuggestions.value = []; return; }
  valueDebounce = setTimeout(async () => {
    valueLoading.value = true;
    valueSuggestions.value = await search(q);
    valueLoading.value = false;
  }, 250);
}

function onValueUpdate(v: string | TermSuggestion | null) {
  if (!v) { valueIri.value = ""; valuePreview.value = null; return; }
  if (typeof v === "string") {
    valueIri.value = v;
    valuePreview.value = valueSuggestions.value.find(s => s.uri === v || s.label === v) ?? null;
  } else {
    valueIri.value = v.uri;
    valuePreview.value = v;
  }
}

// ─── Submit ───────────────────────────────────────────────────────────────────

async function onSubmit() {
  if (!isValid.value || props.subjectAppIds.length === 0) return;
  processing.value = true;
  lastResult.value = null;
  try {
    const isIri = valuePreview.value !== null;
    const items = props.subjectAppIds.map(appId => ({
      subjectAppId: appId,
      subjectKind: props.subjectKind,
      predicateIri: propertyIri.value,
      predicateLabel: propertyPreview.value?.label ?? null,
      ...(isIri ? { objectIri: valueIri.value } : { objectLiteral: valueIri.value }),
    }));
    const resp = await fetch(`${v2BaseUrl()}/v2/annotations/bulk`, {
      method: "POST",
      headers: await authHeaders(),
      body: JSON.stringify(items),
    });
    if (!resp.ok) {
      const text = await resp.text().catch(() => "");
      throw new Error(`HTTP ${resp.status}: ${text}`);
    }
    const body = await resp.json() as { succeeded: number; failed: number };
    lastResult.value = { succeeded: body.succeeded, failed: body.failed };
    emitSuccess(
      `Bulk annotated: ${body.succeeded}/${items.length} succeeded.`,
    );
    emit("bulk-annotated", body.succeeded, body.failed);
    if (body.failed === 0) showDialog.value = false;
  } catch (e) {
    handleError(e as Error, "bulk annotation");
  } finally {
    processing.value = false;
  }
}
</script>

<template>
  <FormDialog
    v-if="showDialog"
    v-model:show-dialog="showDialog"
    :loading="processing"
    :max-width="600"
    :submit-disabled="!isValid || subjectAppIds.length === 0"
    save-button-text="Annotate all"
    :title="`Bulk annotate ${subjectAppIds.length} ${subjectKind}${subjectAppIds.length !== 1 ? 's' : ''}`"
    @submit="onSubmit"
  >
    <template #form>
      <v-form>
        <!-- Result summary (shown when failed > 0 after submit) -->
        <v-alert
          v-if="lastResult && lastResult.failed > 0"
          type="warning"
          variant="tonal"
          density="compact"
          class="mb-3"
        >
          {{ lastResult.succeeded }}/{{ subjectAppIds.length }} succeeded —
          {{ lastResult.failed }} failed (check permissions or schema).
        </v-alert>

        <!-- Predicate -->
        <v-row class="pt-4">
          <v-col class="pb-1">
            <div class="text-subtitle-2 text-medium-emphasis">Predicate</div>
          </v-col>
        </v-row>
        <v-row>
          <v-col class="pb-1">
            <v-combobox
              :items="propertySuggestions"
              :loading="propertyLoading"
              :model-value="propertyIri"
              autofocus
              density="compact"
              item-title="label"
              item-value="uri"
              label="Search term or paste IRI"
              no-data-text="Type at least 2 characters to search"
              no-filter
              variant="outlined"
              data-testid="bulk-annotation-dialog-predicate"
              @update:model-value="onPropertyUpdate"
              @update:search="onPropertySearch"
            >
              <template #item="{ props: itemProps, item }">
                <v-list-item v-bind="itemProps">
                  <template #subtitle>
                    <span class="text-caption text-medium-emphasis">{{ item.raw.uri }}</span>
                  </template>
                </v-list-item>
              </template>
            </v-combobox>
          </v-col>
        </v-row>

        <v-row v-if="propertyPreview" class="mt-0">
          <v-col class="pt-0">
            <v-sheet color="surface-variant" rounded="lg" class="pa-3 text-body-2">
              <div class="font-weight-medium mb-1">{{ propertyPreview.label }}</div>
              <div v-if="propertyPreview.description" class="text-medium-emphasis">
                {{ propertyPreview.description }}
              </div>
              <div class="text-caption text-disabled mt-1">{{ propertyPreview.uri }}</div>
            </v-sheet>
          </v-col>
        </v-row>

        <!-- Value -->
        <v-row class="pt-2">
          <v-col class="pb-1">
            <div class="text-subtitle-2 text-medium-emphasis">Value</div>
          </v-col>
        </v-row>
        <v-row>
          <v-col class="pb-1">
            <v-combobox
              :items="valueSuggestions"
              :loading="valueLoading"
              :model-value="valueIri"
              density="compact"
              item-title="label"
              item-value="uri"
              label="Search term, paste IRI or type literal value"
              no-data-text="Type at least 2 characters to search, or enter a free-text value"
              no-filter
              variant="outlined"
              data-testid="bulk-annotation-dialog-value"
              @update:model-value="onValueUpdate"
              @update:search="onValueSearch"
            >
              <template #item="{ props: itemProps, item }">
                <v-list-item v-bind="itemProps">
                  <template #subtitle>
                    <span class="text-caption text-medium-emphasis">{{ item.raw.uri }}</span>
                  </template>
                </v-list-item>
              </template>
            </v-combobox>
          </v-col>
        </v-row>

        <v-row v-if="valuePreview" class="mt-0">
          <v-col class="pt-0">
            <v-sheet color="surface-variant" rounded="lg" class="pa-3 text-body-2">
              <div class="font-weight-medium mb-1">{{ valuePreview.label }}</div>
              <div v-if="valuePreview.description" class="text-medium-emphasis">
                {{ valuePreview.description }}
              </div>
              <div class="text-caption text-disabled mt-1">{{ valuePreview.uri }}</div>
            </v-sheet>
          </v-col>
        </v-row>
      </v-form>
    </template>
  </FormDialog>
</template>
