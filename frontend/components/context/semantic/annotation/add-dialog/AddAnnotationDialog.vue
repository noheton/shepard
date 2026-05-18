<script lang="ts" setup>
import type {
  ResponseError,
  SemanticAnnotation,
  SemanticRepository,
} from "@dlr-shepard/backend-client";
import type { AutoCompleteItem } from "~/components/common/AutocompleteInput.vue";
import { useFetchSemanticRepositories } from "~/composables/context/useFetchSemanticRepositories";
import {
  useTermSearch,
  type TermSuggestion,
} from "~/composables/context/useTermSearch";

interface AddAnnotationDialogProps {
  annotated: Annotated;
}

const props = defineProps<AddAnnotationDialogProps>();

const showDialog = defineModel<boolean>("showDialog", {
  required: true,
  default: false,
});

const emit = defineEmits<{
  annotationAdded: [id: SemanticAnnotation];
}>();

const { repositories } = useFetchSemanticRepositories();
const { search } = useTermSearch();

const propertyIri = ref<string>("");
const propertyRepository = ref<SemanticRepository | null>(null);
const valueIri = ref<string>("");
const valueRepository = ref<SemanticRepository | null>(null);
const isValid = ref(false);
const processing = ref(false);
const showAdvanced = ref(false);

// previews shown when user selects a suggestion
const propertyPreview = ref<TermSuggestion | null>(null);
const valuePreview = ref<TermSuggestion | null>(null);

const propertySuggestions = ref<TermSuggestion[]>([]);
const propertyLoading = ref(false);
const valueSuggestions = ref<TermSuggestion[]>([]);
const valueLoading = ref(false);

watch(
  repositories,
  () => {
    const first = repositories.value.reduce((prev, cur) =>
      prev.id < cur.id ? prev : cur,
    );
    propertyRepository.value = first;
    valueRepository.value = first;
  },
  { once: true },
);

watch([propertyRepository, propertyIri, valueRepository, valueIri], () => {
  isValid.value =
    propertyRepository.value !== null &&
    propertyIri.value !== "" &&
    valueRepository.value !== null &&
    valueIri.value !== "";
});

const mapToAutocompleteItem = (r: SemanticRepository): AutoCompleteItem => ({
  title: `${r.name} (ID ${r.id})`,
  value: r,
});

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

const onSubmit = async () => {
  processing.value = true;
  try {
    const annotation = await props.annotated.addAnnotation({
      propertyRepositoryId: propertyRepository.value?.id ?? 0,
      propertyIRI: propertyIri.value,
      valueRepositoryId: valueRepository.value?.id ?? 0,
      valueIRI: valueIri.value,
    });
    showDialog.value = false;
    emitSuccess(
      `Successfully added semantic annotation "${formatSemanticAnnotation(annotation.propertyName, annotation.valueName)}".`,
    );
    emit("annotationAdded", annotation);
    handleAnnotationListUpdate();
  } catch (error) {
    handleError(error as ResponseError, "creating semantic annotation");
  }
  processing.value = false;
};
</script>

<template>
  <FormDialog
    v-if="showDialog"
    v-model:show-dialog="showDialog"
    :loading="processing"
    :max-width="600"
    :submit-disabled="!isValid"
    save-button-text="Add"
    title="Create New Semantic Annotation"
    @submit="onSubmit"
  >
    <template #form>
      <v-form>
        <!-- Property ─────────────────────────────────────────────────────── -->
        <v-row class="pt-6">
          <v-col class="pb-1">
            <div class="text-subtitle-2 text-medium-emphasis">Property</div>
          </v-col>
        </v-row>
        <v-row>
          <v-col class="pb-1">
            <v-combobox
              :items="propertySuggestions"
              :loading="propertyLoading"
              autofocus
              density="compact"
              item-title="label"
              item-value="uri"
              label="Search term or paste IRI"
              no-data-text="Type at least 2 characters to search ontology terms"
              no-filter
              variant="outlined"
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

        <!-- property preview -->
        <v-row v-if="propertyPreview" class="mt-0">
          <v-col class="pt-0">
            <v-sheet
              color="surface-variant"
              rounded="lg"
              class="pa-3 text-body-2"
            >
              <div class="font-weight-medium mb-1">{{ propertyPreview.label }}</div>
              <div
                v-if="propertyPreview.description"
                class="text-medium-emphasis"
              >
                {{ propertyPreview.description }}
              </div>
              <div class="text-caption text-disabled mt-1">{{ propertyPreview.uri }}</div>
            </v-sheet>
          </v-col>
        </v-row>

        <!-- Value ────────────────────────────────────────────────────────── -->
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
              density="compact"
              item-title="label"
              item-value="uri"
              label="Search term or paste IRI"
              no-data-text="Type at least 2 characters to search ontology terms"
              no-filter
              variant="outlined"
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

        <!-- value preview -->
        <v-row v-if="valuePreview" class="mt-0">
          <v-col class="pt-0">
            <v-sheet
              color="surface-variant"
              rounded="lg"
              class="pa-3 text-body-2"
            >
              <div class="font-weight-medium mb-1">{{ valuePreview.label }}</div>
              <div
                v-if="valuePreview.description"
                class="text-medium-emphasis"
              >
                {{ valuePreview.description }}
              </div>
              <div class="text-caption text-disabled mt-1">{{ valuePreview.uri }}</div>
            </v-sheet>
          </v-col>
        </v-row>

        <!-- Advanced (repository overrides) ─────────────────────────────── -->
        <v-row class="mt-2">
          <v-col>
            <v-btn
              variant="text"
              size="small"
              density="compact"
              color="medium-emphasis"
              :prepend-icon="showAdvanced ? 'mdi-chevron-up' : 'mdi-chevron-down'"
              @click="showAdvanced = !showAdvanced"
            >
              Advanced
            </v-btn>
          </v-col>
        </v-row>

        <template v-if="showAdvanced">
          <v-row class="mt-0">
            <v-col class="pb-1">
              <div class="text-caption text-medium-emphasis mb-1">Property repository</div>
              <v-autocomplete
                v-model="propertyRepository"
                :items="repositories.map(mapToAutocompleteItem)"
                color="primary"
                density="compact"
                label="Repository"
                no-data-text="No repositories found"
                variant="outlined"
              />
            </v-col>
          </v-row>
          <v-row>
            <v-col class="pb-1">
              <div class="text-caption text-medium-emphasis mb-1">Value repository</div>
              <v-autocomplete
                v-model="valueRepository"
                :items="repositories.map(mapToAutocompleteItem)"
                color="primary"
                density="compact"
                label="Repository"
                no-data-text="No repositories found"
                variant="outlined"
              />
            </v-col>
          </v-row>
        </template>
      </v-form>
    </template>
  </FormDialog>
</template>
