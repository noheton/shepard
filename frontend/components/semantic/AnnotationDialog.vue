<script lang="ts" setup>
/**
 * SEMA-V6-005 — 3-click annotation dialog.
 *
 * Three-click flow (§5.1 of aidocs/semantics/100):
 *   1. Click "Annotate" → dialog opens
 *   2. Type in predicate field → autocomplete from term search
 *   3. Type in value field → autocomplete / free text
 *   4. Click Apply (or Ctrl+Enter)
 *
 * API strategy (two modes, selected by prop shape):
 *   - New mode (preferred): subjectAppId + subjectKind → calls POST /v2/annotations
 *     (SEMA-V6-004 polymorphic surface). Supports the full v6 wire shape
 *     including sourceMode, confidence, validFrom/Until.
 *   - Legacy mode: annotated (Annotated interface) → uses per-entity endpoint.
 *     Used when the caller doesn't yet have subjectAppId (e.g. legacy non-appId entities).
 *
 * Predicate and value autocomplete use GET /v2/semantic/terms/search (n10s-backed).
 * When SEMA-V6-008 ships the pg_trgm index the autocomplete will be upgraded
 * to vocabulary-controlled data — this component's public interface stays unchanged.
 *
 * Optional collapsible sections (present in both basic AND advanced mode per
 * feedback_basic_advanced_superset.md):
 * - Confidence slider (0–1, defaults to 1.0 for human writes)
 * - Valid from / until date pickers
 * - Source mode selector (🧑 Human / 🤖 AI / 🤝 Collaborative)
 *
 * Emits:
 *   annotation-created — after a successful write.
 */
import type { ResponseError } from "@dlr-shepard/backend-client";
import type { Annotated, AnnotationToAdd } from "~/composables/annotated";
import {
  useTermSearch,
  type TermSuggestion,
} from "~/composables/context/useTermSearch";

// ── Props & emits ─────────────────────────────────────────────────────────────

const props = defineProps<{
  /**
   * New-mode subject: appId of the entity being annotated. When provided
   * together with subjectKind, the dialog calls POST /v2/annotations directly.
   */
  subjectAppId?: string;
  /**
   * New-mode subject kind: e.g. "DataObject", "Collection". Required together
   * with subjectAppId for the v6 polymorphic path.
   */
  subjectKind?: string;
  /**
   * Legacy-mode: Annotated interface for per-entity endpoint routing.
   * Used when subjectAppId / subjectKind are not available.
   */
  annotated?: Annotated;
  /** Optional initial property IRI when opening in edit mode. */
  initialPropertyIri?: string;
  /** Optional initial value IRI / literal when opening in edit mode. */
  initialValueIri?: string;
}>();

const showDialog = defineModel<boolean>("showDialog", {
  required: true,
  default: false,
});

const emit = defineEmits<{
  "annotation-created": [];
}>();

// ── State ─────────────────────────────────────────────────────────────────────

const { search } = useTermSearch();

const propertyIri = ref<string>(props.initialPropertyIri ?? "");
const valueIri = ref<string>(props.initialValueIri ?? "");
const propertyPreview = ref<TermSuggestion | null>(null);
const valuePreview = ref<TermSuggestion | null>(null);

const propertySuggestions = ref<TermSuggestion[]>([]);
const propertyLoading = ref(false);
const valueSuggestions = ref<TermSuggestion[]>([]);
const valueLoading = ref(false);

// Optional advanced fields (collapsed by default)
const showOptional = ref(false);
const confidence = ref<number>(1.0);
const validFrom = ref<string>("");
const validUntil = ref<string>("");
const sourceMode = ref<"human" | "ai" | "collaborative">("human");

const processing = ref(false);

const sourceModeOptions = [
  { title: "🧑 Human", value: "human" },
  { title: "🤖 AI", value: "ai" },
  { title: "🤝 Collaborative", value: "collaborative" },
];

const isValid = computed(
  () => propertyIri.value.trim() !== "" && valueIri.value.trim() !== "",
);

// ── v2 base URL helper (matches annotated.ts pattern) ─────────────────────────

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

// ── Autocomplete debounce ─────────────────────────────────────────────────────

let propertyDebounce: ReturnType<typeof setTimeout> | null = null;
let valueDebounce: ReturnType<typeof setTimeout> | null = null;

function onPropertySearch(query: string) {
  propertyIri.value = query ?? "";
  if (propertyDebounce) clearTimeout(propertyDebounce);
  if (!query || query.trim().length < 2) {
    propertySuggestions.value = [];
    return;
  }
  propertyLoading.value = true;
  propertyDebounce = setTimeout(async () => {
    propertySuggestions.value = await search(query);
    propertyLoading.value = false;
  }, 300);
}

function onValueSearch(query: string) {
  valueIri.value = query ?? "";
  if (valueDebounce) clearTimeout(valueDebounce);
  if (!query || query.trim().length < 2) {
    valueSuggestions.value = [];
    return;
  }
  valueLoading.value = true;
  valueDebounce = setTimeout(async () => {
    valueSuggestions.value = await search(query);
    valueLoading.value = false;
  }, 300);
}

function onPropertyUpdate(val: string | TermSuggestion | null) {
  if (!val) {
    propertyIri.value = "";
    propertyPreview.value = null;
  } else if (typeof val === "string") {
    propertyIri.value = val;
    propertyPreview.value = null;
  } else {
    propertyIri.value = val.uri;
    propertyPreview.value = val;
  }
}

function onValueUpdate(val: string | TermSuggestion | null) {
  if (!val) {
    valueIri.value = "";
    valuePreview.value = null;
  } else if (typeof val === "string") {
    valueIri.value = val;
    valuePreview.value = null;
  } else {
    valueIri.value = val.uri;
    valuePreview.value = val;
  }
}

// ── Keyboard shortcut: Ctrl+Enter submits ─────────────────────────────────────

function onKeydown(e: KeyboardEvent) {
  if ((e.ctrlKey || e.metaKey) && e.key === "Enter" && isValid.value) {
    onSubmit();
  }
}

// ── Submit ────────────────────────────────────────────────────────────────────

async function submitViaV2() {
  // SEMA-V6-004 polymorphic path: POST /v2/annotations
  const isIri = valuePreview.value !== null;
  const body: Record<string, unknown> = {
    subjectAppId: props.subjectAppId,
    subjectKind: props.subjectKind,
    predicateIri: propertyIri.value,
    predicateLabel: propertyPreview.value?.label ?? null,
    sourceMode: sourceMode.value,
    confidence: confidence.value,
  };
  if (isIri) {
    body.objectIri = valueIri.value;
  } else {
    body.objectLiteral = valueIri.value;
  }
  if (validFrom.value) {
    body.validFromMillis = new Date(validFrom.value).getTime();
  }
  if (validUntil.value) {
    body.validUntilMillis = new Date(validUntil.value).getTime();
  }

  const url = `${v2BaseUrl()}/v2/annotations`;
  const resp = await fetch(url, {
    method: "POST",
    headers: await authHeaders(),
    body: JSON.stringify(body),
  });
  if (!resp.ok) {
    const text = await resp.text().catch(() => "");
    throw new Error(`HTTP ${resp.status}: ${text}`);
  }
}

async function submitViaLegacy() {
  // Legacy path: Annotated interface (per-entity endpoint)
  const payload: AnnotationToAdd = {
    propertyIRI: propertyIri.value,
    valueIRI: valueIri.value,
  };
  await props.annotated!.addAnnotation(payload);
}

async function onSubmit() {
  if (!isValid.value) return;
  processing.value = true;
  try {
    if (props.subjectAppId && props.subjectKind) {
      await submitViaV2();
    } else if (props.annotated) {
      await submitViaLegacy();
    } else {
      throw new Error("AnnotationDialog: neither subjectAppId+subjectKind nor annotated provided");
    }
    showDialog.value = false;
    emitSuccess(
      `Annotation "${propertyPreview.value?.label ?? propertyIri.value}: ${valuePreview.value?.label ?? valueIri.value}" added.`,
    );
    handleAnnotationListUpdate();
    emit("annotation-created");
  } catch (e) {
    handleError(e as ResponseError, "creating annotation");
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
    :submit-disabled="!isValid"
    save-button-text="Apply"
    title="Annotate"
    @submit="onSubmit"
  >
    <template #form>
      <v-form @keydown="onKeydown">
        <!-- ── Step 1: Predicate ──────────────────────────────────────────── -->
        <v-row class="pt-4">
          <v-col class="pb-1">
            <div class="text-subtitle-2 text-medium-emphasis d-flex align-center ga-1">
              Predicate
              <v-tooltip
                text="The semantic property (predicate) from a controlled vocabulary. Search by label or paste an IRI directly."
                max-width="320"
                location="top"
              >
                <template #activator="{ props: tip }">
                  <v-icon
                    v-bind="tip"
                    size="14"
                    color="medium-emphasis"
                    icon="mdi-help-circle-outline"
                  />
                </template>
              </v-tooltip>
            </div>
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
              data-testid="annotation-dialog-predicate"
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

        <!-- Predicate preview card -->
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

        <!-- ── Step 2: Value ──────────────────────────────────────────────── -->
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
              data-testid="annotation-dialog-value"
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

        <!-- Value preview card -->
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

        <!-- ── Optional advanced fields (collapsed by default) ───────────── -->
        <!-- Per feedback_basic_advanced_superset.md: present in BOTH basic
             and advanced mode (never hidden). -->
        <v-row class="mt-3">
          <v-col>
            <v-btn
              variant="text"
              size="small"
              density="compact"
              color="medium-emphasis"
              :prepend-icon="showOptional ? 'mdi-chevron-up' : 'mdi-chevron-down'"
              data-testid="annotation-dialog-optional-toggle"
              @click="showOptional = !showOptional"
            >
              Optional fields
            </v-btn>
          </v-col>
        </v-row>

        <template v-if="showOptional">
          <!-- Source mode selector -->
          <v-row class="mt-1">
            <v-col>
              <div class="text-caption text-medium-emphasis mb-1">Source mode</div>
              <v-btn-toggle
                v-model="sourceMode"
                density="compact"
                color="primary"
                variant="outlined"
                mandatory
                data-testid="annotation-dialog-source-mode"
              >
                <v-btn
                  v-for="opt in sourceModeOptions"
                  :key="opt.value"
                  :value="opt.value"
                  size="small"
                >
                  {{ opt.title }}
                </v-btn>
              </v-btn-toggle>
            </v-col>
          </v-row>

          <!-- Confidence slider -->
          <v-row class="mt-2">
            <v-col>
              <div class="text-caption text-medium-emphasis mb-1">
                Confidence: {{ confidence.toFixed(2) }}
              </div>
              <v-slider
                v-model="confidence"
                :min="0"
                :max="1"
                :step="0.01"
                color="primary"
                density="compact"
                hide-details
                data-testid="annotation-dialog-confidence"
              />
            </v-col>
          </v-row>

          <!-- Valid from / until -->
          <v-row class="mt-2">
            <v-col cols="6">
              <v-text-field
                v-model="validFrom"
                density="compact"
                hide-details
                label="Valid from (ISO date)"
                placeholder="2026-01-01"
                variant="outlined"
                data-testid="annotation-dialog-valid-from"
              />
            </v-col>
            <v-col cols="6">
              <v-text-field
                v-model="validUntil"
                density="compact"
                hide-details
                label="Valid until (ISO date)"
                placeholder="2027-01-01"
                variant="outlined"
                data-testid="annotation-dialog-valid-until"
              />
            </v-col>
          </v-row>
        </template>

        <!-- Keyboard hint -->
        <v-row class="mt-2">
          <v-col>
            <span class="text-caption text-disabled">Ctrl+Enter to apply</span>
          </v-col>
        </v-row>
      </v-form>
    </template>
  </FormDialog>
</template>
