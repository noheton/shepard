<script setup lang="ts">
import type {
  ResponseError,
  SemanticAnnotation,
  SemanticRepository,
} from "@/generated/openapi";
import SemanticRepositoryService from "@/services/semanticRepositoriesService";
import { logError } from "@/utils/error-handling";
import { reactive, ref, type PropType } from "vue";

const props = defineProps({
  modalId: {
    type: String,
    default: "edit-semantic-modal",
  },
  modalName: {
    type: String,
    default: "Edit Semantic Annotation",
  },
  annotationList: {
    type: Array as PropType<SemanticAnnotation[]>,
    default() {
      return [];
    },
  },
});
const emit = defineEmits(["create", "delete"]);

const initialFormData = () => ({
  propertyRepositoryId: "",
  valueRepositoryId: "",
  propertyIRI: "",
  valueIRI: "",
});

const formData = reactive(initialFormData());

const repository = {
  property: ref<{ valid?: boolean; repository?: SemanticRepository }>({}),
  value: ref<{ valid?: boolean; repository?: SemanticRepository }>({}),
};

function handlePrepare() {
  Object.assign(formData, initialFormData());
  repository.property.value = {};
  repository.value.value = {};
}

function emitSemanticAnnotation() {
  const semanticAnnotation = {
    propertyIRI: formData.propertyIRI,
    propertyRepositoryId: +formData.propertyRepositoryId,
    valueIRI: formData.valueIRI,
    valueRepositoryId: +formData.valueRepositoryId,
  };
  emit("create", semanticAnnotation);
}

function fetchRepository(type: "property" | "value") {
  const id =
    type == "value"
      ? +formData.valueRepositoryId
      : +formData.propertyRepositoryId;
  SemanticRepositoryService.getSemanticRepository({
    semanticRepositoryId: id,
  })
    .then(response => {
      repository[type].value = { repository: response, valid: true };
    })
    .catch(e => {
      logError(e as ResponseError, "fetching " + type + " repository");
      repository[type].value = { repository: undefined, valid: false };
    });
}
</script>

<template>
  <b-modal
    :id="props.modalId"
    ref="modal"
    size="lg"
    :title="props.modalName"
    lazy
    ok-only
    ok-variant="secondary"
    ok-title="Close"
    @show="handlePrepare()"
  >
    <b-container>
      <b-row class="mb-2">
        <b-col cols="3"> Property IRI </b-col>
        <b-col cols="9">
          <b-form-input
            v-model="formData.propertyIRI"
            placeholder="property: IRI"
            required
          >
          </b-form-input>
        </b-col>
      </b-row>
      <b-row class="mb-4">
        <b-col cols="3"> Property Repository ID </b-col>
        <b-col cols="9">
          <b-form-input
            v-model="formData.propertyRepositoryId"
            :state="repository.property.value.valid"
            placeholder="property: Semantic Repository ID"
            type="number"
            required
            @blur="fetchRepository('property')"
          ></b-form-input>
          <small v-if="repository.property.value.repository">
            <em> {{ repository.property.value.repository.name }} </em>
          </small>
          <small v-else>Please enter a valid repository id</small>
        </b-col>
      </b-row>

      <b-row class="mb-3">
        <b-col cols="3"> Value IRI </b-col>
        <b-col cols="9">
          <b-form-input
            v-model="formData.valueIRI"
            placeholder="value: IRI"
            required
          ></b-form-input>
        </b-col>
      </b-row>
      <b-row class="mb-2">
        <b-col cols="3"> Value Repository ID</b-col>
        <b-col cols="9">
          <b-form-input
            v-model="formData.valueRepositoryId"
            :state="repository.value.value.valid"
            placeholder="value: Semantic Repository ID"
            type="number"
            required
            @blur="fetchRepository('value')"
          ></b-form-input>

          <small v-if="repository.value.value.repository">
            <em> {{ repository.value.value.repository.name }} </em>
          </small>
          <small v-else>Please enter a valid repository id</small>
        </b-col>
      </b-row>
      <b-row>
        <b-col>
          <b-button
            class="float-right"
            variant="primary"
            @click="emitSemanticAnnotation()"
          >
            Create Semantic Annotation
          </b-button>
        </b-col>
      </b-row>
      <hr />
      <div v-for="(annotation, index) in props.annotationList" :key="index">
        <b-badge class="p-0 mb-3 mr-1 float-left">
          <span class="bg-info py-1 px-2 rounded-left text-white">
            {{ annotation.name?.split("::")[0] }}
          </span>
          <span class="bg-primary py-1 px-2 rounded-right text-white">
            {{ annotation.name?.split("::")[1] }}
            <b-link class="pl-2">
              <XIcon variant="white" @click="emit('delete', annotation.id)" />
            </b-link>
          </span>
        </b-badge>
      </div>
    </b-container>
  </b-modal>
</template>
