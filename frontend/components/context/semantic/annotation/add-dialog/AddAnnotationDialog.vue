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

// IRI fields — store the URI string (may be typed freely or selected from suggestions)
const propertyIri = ref<string>("");
const propertyRepository = ref<SemanticRepository | null>(null);
const valueIri = ref<string>("");
const valueRepository = ref<SemanticRepository | null>(null);
const isValid = ref(false);
const processing = ref(false);

// Autocomplete state — suggestions + loading flags
const propertySuggestions = ref<TermSuggestion[]>([]);
const propertyLoading = ref(false);
const valueSuggestions = ref<TermSuggestion[]>([]);
const valueLoading = ref(false);

watch(
  repositories,
  () => {
    // get repository with lowest id from list
    const repositoryWithLowestId = repositories.value.reduce((prev, current) =>
      prev.id < current.id ? prev : current,
    );
    propertyRepository.value = repositoryWithLowestId;
    valueRepository.value = repositoryWithLowestId;
  },
  { once: true },
);

// enable submit button if all required fields are filled
watch([propertyRepository, propertyIri, valueRepository, valueIri], () => {
  isValid.value =
    propertyRepository.value !== null &&
    propertyIri.value !== "" &&
    valueRepository.value !== null &&
    valueIri.value !== "";
});

const mapToAutocompleteItem = (
  repository: SemanticRepository,
): AutoCompleteItem => ({
  title: `${repository.name} (ID ${repository.id})`,
  value: repository,
});

// ─── Term search debounce helpers ──────────────────────────────────────────

let propertyDebounce: ReturnType<typeof setTimeout> | null = null;
let valueDebounce: ReturnType<typeof setTimeout> | null = null;

function onPropertySearch(query: string) {
  // Keep the IRI field in sync with whatever the user is typing
  // so the submit-button validity watcher sees changes on each keystroke.
  // onPropertyUpdate() will override this when the user selects a suggestion.
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
  // Keep the IRI field in sync on each keystroke (same rationale as above).
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

// When a suggestion is selected from the combobox, update the IRI field
// with the URI. v-combobox sets the model to either the typed string or
// the selected item object — handle both shapes.
function onPropertyUpdate(val: string | TermSuggestion | null) {
  if (!val) {
    propertyIri.value = "";
  } else if (typeof val === "string") {
    propertyIri.value = val;
  } else {
    propertyIri.value = val.uri;
  }
}

function onValueUpdate(val: string | TermSuggestion | null) {
  if (!val) {
    valueIri.value = "";
  } else if (typeof val === "string") {
    valueIri.value = val;
  } else {
    valueIri.value = val.uri;
  }
}

// ─── Submit ────────────────────────────────────────────────────────────────

const onSubmit = async () => {
  processing.value = true;
  try {
    const annotationToAdd = {
      propertyRepositoryId: propertyRepository.value?.id ?? 0,
      propertyIRI: propertyIri.value,
      valueRepositoryId: valueRepository.value?.id ?? 0,
      valueIRI: valueIri.value,
    };
    const annotation = await props.annotated.addAnnotation(annotationToAdd);
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

const showPropertyIRITooltip = ref(true);
const showPropertyRepoNameTooltip = ref(true);
const showValueIRITooltip = ref(true);
const showValueRepoNameTooltip = ref(true);
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
        <v-row>
          <v-col class="pb-0 pt-8">Property</v-col>
        </v-row>
        <v-row>
          <v-col class="pb-0">
            <v-tooltip
              :class="`${showPropertyIRITooltip ? '' : 'hideOnClick'}`"
              :open-on-focus="false"
              content-class="text-body-3"
              location="bottom right"
              max-width="500"
              open-delay="750"
              open-on-hover
            >
              <template #activator="{ props: semanticProps }">
                <v-combobox
                  :items="propertySuggestions"
                  :loading="propertyLoading"
                  autofocus
                  density="compact"
                  item-title="label"
                  item-value="uri"
                  label="IRI"
                  no-data-text="Type at least 2 characters to search ontology terms"
                  no-filter
                  variant="outlined"
                  v-bind="semanticProps"
                  @blur="showPropertyIRITooltip = true"
                  @focus="showPropertyIRITooltip = false"
                  @update:model-value="onPropertyUpdate"
                  @update:search="onPropertySearch"
                >
                  <template #item="{ props: itemProps, item }">
                    <v-list-item v-bind="itemProps">
                      <template #subtitle>{{ item.raw.uri }}</template>
                    </v-list-item>
                  </template>
                </v-combobox>
              </template>
              <div>
                Type to search loaded ontology terms, or paste a term IRI
                directly.
              </div>
            </v-tooltip>
          </v-col>
        </v-row>
        <v-row>
          <v-col class="pb-0">
            <v-tooltip
              :class="`${showPropertyRepoNameTooltip ? '' : 'hideOnClick'}`"
              :open-on-focus="false"
              content-class="text-body-3"
              location="bottom right"
              max-width="500"
              open-delay="750"
              open-on-hover
            >
              <template #activator="{ props: semanticProps }">
                <v-autocomplete
                  v-model="propertyRepository"
                  :items="repositories.map(mapToAutocompleteItem)"
                  color="primary"
                  density="compact"
                  label="Repository Name or ID..."
                  no-data-text="No repositories found"
                  placeholder="select a repository"
                  v-bind="semanticProps"
                  variant="outlined"
                  @blur="showPropertyRepoNameTooltip = true"
                  @focus="showPropertyRepoNameTooltip = false"
                />
              </template>
              <div>
                Before you can add a semantic annotation you need to define a
                semantic repository. You can find your semantic repositories in
                the main menu of shepard.
              </div>
            </v-tooltip>
          </v-col>
        </v-row>
        <v-row>
          <v-col class="pb-0">Value</v-col>
        </v-row>
        <v-row>
          <v-col class="pb-0">
            <v-tooltip
              :class="`${showValueIRITooltip ? '' : 'hideOnClick'}`"
              :open-on-focus="false"
              content-class="text-body-3"
              location="bottom right"
              max-width="500"
              open-delay="750"
              open-on-hover
            >
              <template #activator="{ props: semanticProps }">
                <v-combobox
                  :items="valueSuggestions"
                  :loading="valueLoading"
                  density="compact"
                  item-title="label"
                  item-value="uri"
                  label="IRI"
                  no-data-text="Type at least 2 characters to search ontology terms"
                  no-filter
                  variant="outlined"
                  v-bind="semanticProps"
                  @blur="showValueIRITooltip = true"
                  @focus="showValueIRITooltip = false"
                  @update:model-value="onValueUpdate"
                  @update:search="onValueSearch"
                >
                  <template #item="{ props: itemProps, item }">
                    <v-list-item v-bind="itemProps">
                      <template #subtitle>{{ item.raw.uri }}</template>
                    </v-list-item>
                  </template>
                </v-combobox>
              </template>
              <div>
                Type to search loaded ontology terms, or paste a term IRI
                directly.
              </div>
            </v-tooltip>
          </v-col>
        </v-row>
        <v-row>
          <v-col class="pb-0">
            <v-tooltip
              :class="`${showValueRepoNameTooltip ? '' : 'hideOnClick'}`"
              :open-on-focus="false"
              content-class="text-body-3"
              location="bottom right"
              max-width="500"
              open-delay="750"
              open-on-hover
            >
              <template #activator="{ props: semanticProps }">
                <v-autocomplete
                  v-model="valueRepository"
                  :items="repositories.map(mapToAutocompleteItem)"
                  color="primary"
                  density="compact"
                  label="Repository Name or ID..."
                  no-data-text="No repositories found"
                  placeholder="select a repository"
                  v-bind="semanticProps"
                  variant="outlined"
                  @blur="showValueRepoNameTooltip = true"
                  @focus="showValueRepoNameTooltip = false"
                />
              </template>
              <div>
                Before you can add a semantic annotation you need to define a
                semantic repository. You can find your semantic repositories in
                the main menu of shepard.
              </div>
            </v-tooltip>
          </v-col>
        </v-row>
      </v-form>
    </template>
  </FormDialog>
</template>

<style lang="css" scoped>
.v-tooltip.hideOnClick :deep(.v-overlay__content) {
  visibility: hidden;
}
</style>
