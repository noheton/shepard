<script setup lang="ts">
import type {
  ResponseError,
  SemanticAnnotation,
  SemanticRepository,
} from "@dlr-shepard/backend-client";
import type { AutoCompleteItem } from "~/components/common/AutocompleteInput.vue";
import NameInput from "~/components/context/input-components/NameInput.vue";
import { useFetchSemanticRepositories } from "~/composables/context/useFetchSemanticRepositories";
import {
  addSemanticAnnotation,
  type AddSemanticAnnotationArgs,
} from "../addSemanticAnnotation";

interface AddAnnotationDialogProps {
  collectionId: number;
  dataObjectId?: number;
  referenceId?: number;
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
const propertyIri = ref<string>("");
const propertyRepository = ref<SemanticRepository | null>(null);
const valueIri = ref<string>("");
const valueRepository = ref<SemanticRepository | null>(null);
const isValid = ref(false);
const processing = ref(false);

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

const onSubmit = async () => {
  processing.value = true;
  try {
    const args: AddSemanticAnnotationArgs = {
      ...props,
      annotation: {
        propertyRepositoryId: propertyRepository.value?.id ?? 0,
        propertyIRI: propertyIri.value,
        valueRepositoryId: valueRepository.value?.id ?? 0,
        valueIRI: valueIri.value,
      },
    };
    const annotation = await addSemanticAnnotation(args);
    showDialog.value = false;
    emitSuccess(
      `Successfully added semantic annotation "${annotation?.name}".`,
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
  <Dialog
    v-if="showDialog"
    v-model:show-dialog="showDialog"
    title="Create New Semantic Annotation"
    save-button-text="Add"
    :max-width="600"
    :submit-disabled="!isValid"
    :loading="processing"
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
              location="bottom right"
              open-delay="750"
              open-on-hover
              :open-on-focus="false"
              max-width="500"
              content-class="text-body-3"
              :class="`${showPropertyIRITooltip ? '' : 'hideOnClick'}`"
            >
              <template #activator="{ props }">
                <NameInput
                  v-model:name="propertyIri"
                  label="IRI"
                  autofocus
                  v-bind="props"
                  @focus="showPropertyIRITooltip = false"
                  @blur="showPropertyIRITooltip = true"
                />
              </template>
              <div>
                Please paste term IRI. Refer to your ontology server to get the
                link.
              </div>
            </v-tooltip>
          </v-col>
        </v-row>
        <v-row>
          <v-col class="pb-0">
            <v-tooltip
              location="bottom right"
              open-delay="750"
              open-on-hover
              :open-on-focus="false"
              max-width="500"
              content-class="text-body-3"
              :class="`${showPropertyRepoNameTooltip ? '' : 'hideOnClick'}`"
            >
              <template #activator="{ props }">
                <v-autocomplete
                  v-bind="props"
                  v-model="propertyRepository"
                  :items="repositories.map(mapToAutocompleteItem)"
                  label="Repository Name or ID..."
                  placeholder="select a repository"
                  no-data-text="No repositories found"
                  color="primary"
                  variant="outlined"
                  density="compact"
                  @focus="showPropertyRepoNameTooltip = false"
                  @blur="showPropertyRepoNameTooltip = true"
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
              location="bottom right"
              open-delay="750"
              open-on-hover
              :open-on-focus="false"
              max-width="500"
              content-class="text-body-3"
              :class="`${showValueIRITooltip ? '' : 'hideOnClick'}`"
            >
              <template #activator="{ props }">
                <NameInput
                  v-model:name="valueIri"
                  label="IRI"
                  v-bind="props"
                  @focus="showValueIRITooltip = false"
                  @blur="showValueIRITooltip = true"
                />
              </template>
              <div>
                Please paste term IRI. Refer to your ontology server to get the
                link.
              </div>
            </v-tooltip>
          </v-col>
        </v-row>
        <v-row>
          <v-col class="pb-0">
            <v-tooltip
              location="bottom right"
              open-delay="750"
              open-on-hover
              :open-on-focus="false"
              max-width="500"
              content-class="text-body-3"
              :class="`${showValueRepoNameTooltip ? '' : 'hideOnClick'}`"
            >
              <template #activator="{ props }">
                <v-autocomplete
                  v-bind="props"
                  v-model="valueRepository"
                  :items="repositories.map(mapToAutocompleteItem)"
                  label="Repository Name or ID..."
                  density="compact"
                  variant="outlined"
                  no-data-text="No repositories found"
                  color="primary"
                  placeholder="select a repository"
                  @focus="showValueRepoNameTooltip = false"
                  @blur="showValueRepoNameTooltip = true"
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
  </Dialog>
</template>

<style lang="css" scoped>
.v-tooltip.hideOnClick :deep(.v-overlay__content) {
  visibility: hidden;
}
</style>
