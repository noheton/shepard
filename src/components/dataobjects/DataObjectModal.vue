<script setup lang="ts">
import TextEditor from "@/components/generic/TextEditor.vue";
import DataObjectService from "@/services/dataObjectService";
import { handleError } from "@/utils/error-handling";
import type { DataObject, ResponseError } from "@dlr-shepard/shepard-client";
import { ref, type PropType } from "vue";
import { useRouter } from "vue2-helpers/vue-router";

const props = defineProps({
  modalId: {
    type: String,
    default: "dataObjectModal",
  },
  modalName: {
    type: String,
    default: "dataObjectModal",
  },
  currentCollectionId: {
    type: Number,
    required: true,
  },
  currentDataObject: {
    type: Object as PropType<DataObject | undefined>,
    default: undefined,
  },
});

const emit = defineEmits(["data-object-changed"]);

const router = useRouter();
const name = ref<string>("");
const description = ref<string>("");
const validParent = ref<boolean | undefined>();
const possibleParent = ref<DataObject>({ name: "" });
const validPredecessors = ref<Map<number, boolean | undefined>>(new Map());
const possiblePredecessors = ref<Array<DataObject>>([]);
const possibleAttributes = ref<{ key: string; value: string }[]>([]);

function prepare() {
  name.value = props.currentDataObject?.name || "";
  description.value = props.currentDataObject?.description || "";
  validPredecessors.value = new Map();
  possiblePredecessors.value = [];
  possibleAttributes.value = [];

  if (props.currentDataObject?.parentId) {
    possibleParent.value = { id: props.currentDataObject.parentId, name: "" };
    validateParent();
  } else {
    possibleParent.value = {
      id: undefined,
      name: "",
    };
  }

  if (props.currentDataObject?.predecessorIds?.length) {
    props.currentDataObject.predecessorIds.forEach((pId, i) => {
      possiblePredecessors.value.push({ id: pId, name: "" });
      validatePredecessor(i);
    });
  } else {
    possiblePredecessors.value.push({ id: undefined, name: "" });
  }

  if (
    props.currentDataObject?.attributes &&
    Object.keys(props.currentDataObject.attributes).length > 0
  ) {
    Object.entries(props.currentDataObject.attributes).forEach(
      ([key, value]) => {
        possibleAttributes.value.push({ key: key, value: value });
      },
    );
  } else {
    possibleAttributes.value.push({
      key: "",
      value: "",
    });
  }
}

function handleOk() {
  const newDataObject: DataObject = {
    id: props.currentDataObject?.id,
    name: name.value,
    description: description.value,
  };

  if (possibleParent.value.id != undefined) {
    newDataObject.parentId = possibleParent.value.id;
  }

  newDataObject.predecessorIds = possiblePredecessors.value
    .filter(pre => pre.id != undefined)
    .map(pre => pre.id) as number[];

  const attributes: { [key: string]: string } = {};
  possibleAttributes.value
    .filter(attr => attr.key != "")
    .forEach(attr => {
      attributes[attr.key] = attr.value;
    });
  newDataObject.attributes = attributes;

  if (props.currentDataObject?.id) {
    updateDataObject(newDataObject);
  } else {
    createDataObject(newDataObject);
  }
}

function addAttribute() {
  possibleAttributes.value.push({
    key: "",
    value: "",
  });
}

function removeAttribute(i: number) {
  possibleAttributes.value.splice(i, 1);
}

function addPredecessor() {
  possiblePredecessors.value.push({ id: undefined, name: "" });
}

function removePredecessor(i: number) {
  possiblePredecessors.value.splice(i, 1);
}

function validateParent() {
  if (possibleParent.value.id == undefined) {
    possibleParent.value.name = "";
    validParent.value = undefined;
  } else {
    DataObjectService.getDataObject({
      collectionId: props.currentCollectionId,
      dataObjectId: possibleParent.value.id,
    })
      .then(response => {
        possibleParent.value.name = response.name ? response.name : "";
        if (possibleParent.value.name == "") {
          validParent.value = undefined;
        } else {
          validParent.value = true;
        }
      })
      .catch(() => {
        possibleParent.value.name = "";
        validParent.value = false;
      });
  }
}

function validatePredecessor(i: number) {
  const id = possiblePredecessors.value[i]?.id;
  if (id == undefined) {
    possiblePredecessors.value[i] = { id: undefined, name: "" };
  } else {
    DataObjectService.getDataObject({
      collectionId: props.currentCollectionId,
      dataObjectId: id,
    })
      .then(response => {
        possiblePredecessors.value[i].name = response.name ? response.name : "";
        validPredecessors.value.set(
          id,
          possiblePredecessors.value[i].name != undefined,
        );
        possiblePredecessors.value = [...possiblePredecessors.value];
      })
      .catch(() => {
        possiblePredecessors.value[i].name = "";
        validPredecessors.value.set(id, false);
        possiblePredecessors.value = [...possiblePredecessors.value];
      });
  }
}

function createDataObject(dataObject: DataObject) {
  DataObjectService.createDataObject({
    collectionId: props.currentCollectionId,
    dataObject: dataObject,
  })
    .then(response => {
      router.push({
        name: "DataObject",
        params: {
          collectionId: String(props.currentCollectionId),
          dataObjectId: String(response.id),
        },
      });
    })
    .catch(e => {
      handleError(e as ResponseError, "creating data object");
    });
}

function updateDataObject(dataObject: DataObject) {
  if (!dataObject.id) {
    return;
  }
  DataObjectService.updateDataObject({
    collectionId: props.currentCollectionId,
    dataObjectId: dataObject.id,
    dataObject: dataObject,
  })
    .then(() => {
      emit("data-object-changed");
    })
    .catch(e => {
      handleError(e as ResponseError, "updating data object");
    });
}
</script>

<template>
  <b-modal
    :id="modalId"
    ref="modal"
    size="lg"
    :title="modalName"
    lazy
    @show="prepare()"
    @ok="handleOk()"
  >
    <b-form-group>
      <b-container>
        <b-row class="mb-3">
          <b-col cols="2"> Name </b-col>
          <b-col cols="8">
            <b-form-input v-model="name" required placeholder="Name">
            </b-form-input>
          </b-col>
        </b-row>

        <b-row class="mb-3">
          <b-col cols="2"> Description </b-col>
          <b-col cols="8">
            <TextEditor v-model="description"></TextEditor>
          </b-col>
        </b-row>

        <b-row class="mb-3">
          <b-col cols="2"> Parent </b-col>
          <b-col cols="3">
            <b-form-input
              v-model="possibleParent.id"
              type="number"
              placeholder="ID"
              @blur="validateParent()"
            >
            </b-form-input>
          </b-col>
          <b-col cols="5">
            <b-form-input
              v-model="possibleParent.name"
              :state="validParent"
              readonly
              placeholder="Name"
            >
            </b-form-input>
          </b-col>
        </b-row>

        <p>Predecessors</p>
        <b-form-group
          v-for="(predecessor, i) in possiblePredecessors"
          :key="i"
          class="m-0"
        >
          <b-row class="mb-1">
            <b-col cols="2">{{ i }}</b-col>
            <b-col cols="3">
              <b-form-input
                v-model="predecessor.id"
                type="number"
                placeholder="ID"
                @blur="validatePredecessor(i)"
              ></b-form-input>
            </b-col>
            <b-col cols="5">
              <b-form-input
                v-model="predecessor.name"
                :state="
                  predecessor.id
                    ? validPredecessors.get(predecessor.id)
                    : undefined
                "
                readonly
                placeholder="Name"
              ></b-form-input>
            </b-col>
            <b-col cols="1">
              <b-button
                v-show="i == possiblePredecessors.length - 1"
                class="small-button"
                variant="success"
                @click="addPredecessor()"
              >
                <CreateIcon />
              </b-button>
            </b-col>
            <b-col cols="1">
              <b-button
                v-show="i || (!i && possiblePredecessors.length > 1)"
                class="small-button"
                variant="danger"
                @click="removePredecessor(i)"
              >
                <RemoveIcon />
              </b-button>
            </b-col>
          </b-row>
        </b-form-group>

        <p>Attributes</p>
        <div class="mt-3">
          <b-form-group
            v-for="(attribute, i) in possibleAttributes"
            :key="i"
            class="m-0"
          >
            <b-row class="mb-1">
              <b-col cols="2">{{ i }}</b-col>
              <b-col cols="3">
                <b-form-input
                  v-model="attribute.key"
                  placeholder="key"
                ></b-form-input>
              </b-col>

              <b-col cols="5">
                <b-form-input
                  v-model="attribute.value"
                  placeholder="value"
                ></b-form-input>
              </b-col>

              <b-col cols="1">
                <b-button
                  v-show="i == possibleAttributes.length - 1"
                  class="small-button"
                  variant="success"
                  @click="addAttribute()"
                >
                  <CreateIcon />
                </b-button>
              </b-col>
              <b-col cols="1">
                <b-button
                  v-show="i || (!i && possibleAttributes.length > 1)"
                  class="small-button"
                  variant="danger"
                  @click="removeAttribute(i)"
                >
                  <RemoveIcon />
                </b-button>
              </b-col>
            </b-row>
          </b-form-group>
        </div>
      </b-container>
    </b-form-group>
  </b-modal>
</template>
