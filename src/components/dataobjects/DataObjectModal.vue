<script setup lang="ts">
import DataObjectService from "@/services/dataObjectService";
import { emitter } from "@/utils/event-bus";
import { useRouter } from "@/utils/helpers";
import { DataObject } from "@dlr-shepard/shepard-client";
import { PropType, ref, Ref } from "vue";

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
    type: Object as PropType<DataObject>,
    default: undefined,
  },
});

const emit = defineEmits(["data-object-changed"]);

const router = useRouter();
const newDataObject: Ref<DataObject> = ref({ name: "" });
const validParent: Ref<boolean | undefined> = ref(undefined);
const validPredecessors: Ref<Array<boolean | undefined>> = ref([]);
const possibleParent: Ref<DataObject> = ref({ name: "" });
const possiblePredecessors: Ref<Array<DataObject>> = ref([]);
const possibleAttributes: Ref<{ key: string; value: string }[]> = ref([]);

function prepare() {
  newDataObject.value = props.currentDataObject
    ? { ...props.currentDataObject }
    : { name: "" };
  possiblePredecessors.value = [];
  possibleAttributes.value = [];
  validPredecessors.value = [];

  if (props.currentDataObject?.parentId) {
    validateParent();
    possibleParent.value = { id: props.currentDataObject.parentId, name: "" };
  } else {
    possibleParent.value = {
      id: undefined,
      name: "",
    };
  }

  if (
    props.currentDataObject?.predecessorIds &&
    props.currentDataObject?.predecessorIds.length > 0
  ) {
    for (let i = 0; i < props.currentDataObject?.predecessorIds.length; i++) {
      possiblePredecessors.value.push({
        id: props.currentDataObject?.predecessorIds[i],
        name: "",
      });
      validatePredecessor(i);
    }
  } else {
    possiblePredecessors.value.push({
      id: undefined,
      name: "",
    });
  }

  if (props.currentDataObject?.attributes) {
    Object.entries(props.currentDataObject?.attributes).forEach(
      ([key, value]) => {
        possibleAttributes.value.push({ key: key, value: value });
      },
    );
  }
  if (possibleAttributes.value.length == 0) {
    possibleAttributes.value.push({
      key: "",
      value: "",
    });
  }
}

function handleOk() {
  if (possibleParent.value.id != undefined) {
    newDataObject.value.parentId = possibleParent.value.id;
  }

  const preIds: number[] = [];
  possiblePredecessors.value.forEach(pre => {
    if (pre.id) {
      preIds.push(pre.id);
    }
  });
  newDataObject.value.predecessorIds = preIds;

  const attributes: { [key: string]: string } = {};
  possibleAttributes.value.forEach(attr => {
    if (attr.key != "") {
      attributes[attr.key] = attr.value;
    }
  });
  newDataObject.value.attributes = attributes;

  if (newDataObject.value.id) {
    update();
  } else {
    create();
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
  possiblePredecessors.value.push({
    id: undefined,
    name: "",
  });
  validPredecessors.value.push(undefined);
}

function removePredecessor(i: number) {
  possiblePredecessors.value.splice(i, 1);
  validPredecessors.value.splice(i, 1);
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
  const id = possiblePredecessors.value[i].id;
  if (id == undefined) {
    possiblePredecessors.value[i].name = "";
    validParent.value = undefined;
  } else {
    DataObjectService.getDataObject({
      collectionId: props.currentCollectionId,
      dataObjectId: id,
    })
      .then(response => {
        possiblePredecessors.value[i].name = response.name ? response.name : "";
        if (possiblePredecessors.value[i].name == "") {
          validPredecessors.value[i] = undefined;
        } else {
          validPredecessors.value[i] = true;
        }
        validPredecessors.value = [...validPredecessors.value];
        possiblePredecessors.value = [...possiblePredecessors.value];
      })
      .catch(() => {
        possiblePredecessors.value[i].name = "";
        validPredecessors.value[i] = false;
      });
  }
}

function create() {
  DataObjectService.createDataObject({
    collectionId: props.currentCollectionId,
    dataObject: newDataObject.value,
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
      const error = "Error while creating data object: " + e.statusText;
      console.log(error);
      emitter.emit("error", error);
    });
}

function update() {
  if (!newDataObject.value.id) {
    console.log("Unknown dataObject id");
    return;
  }
  DataObjectService.updateDataObject({
    collectionId: props.currentCollectionId,
    dataObjectId: newDataObject.value.id,
    dataObject: newDataObject.value,
  })
    .then(() => {
      emit("data-object-changed");
    })
    .catch(e => {
      const error = "Error while updating data object: " + e.statusText;
      console.log(error);
      emitter.emit("error", error);
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
            <b-form-input
              v-model="newDataObject.name"
              required
              placeholder="Name"
            >
            </b-form-input>
          </b-col>
        </b-row>

        <b-row class="mb-3">
          <b-col cols="2"> Description </b-col>
          <b-col cols="8">
            <b-form-textarea
              v-model="newDataObject.description"
              placeholder="Description"
              rows="3"
              max-rows="6"
            >
            </b-form-textarea>
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
                :state="validPredecessors[i]"
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
