<script lang="ts" setup>
import type {
  ResponseError,
  SemanticAnnotation,
  SemanticRepository,
} from "@dlr-shepard/backend-client";
import type { AutoCompleteItem } from "~/components/common/AutocompleteInput.vue";
import NameInput from "~/components/context/input-components/NameInput.vue";
import { useFetchSemanticRepositories } from "~/composables/context/useFetchSemanticRepositories";

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
                <NameInput
                  v-model:name="propertyIri"
                  autofocus
                  label="IRI"
                  v-bind="semanticProps"
                  @blur="showPropertyIRITooltip = true"
                  @focus="showPropertyIRITooltip = false"
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
                <NameInput
                  v-model:name="valueIri"
                  label="IRI"
                  v-bind="semanticProps"
                  @blur="showValueIRITooltip = true"
                  @focus="showValueIRITooltip = false"
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
