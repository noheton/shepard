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
              @blur="validateDataObject(possibleParent)"
            >
            </b-form-input>
          </b-col>
          <b-col cols="5">
            <b-form-input
              v-model="possibleParent.name"
              :class="{ validationField: validationError }"
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
                @blur="validateDataObject(predecessor)"
              ></b-form-input>
            </b-col>
            <b-col cols="5">
              <b-form-input
                v-model="predecessor.name"
                :class="{ validationField: validationError }"
                readonly
                placeholder="Name"
              ></b-form-input>
            </b-col>
            <b-col cols="1">
              <b-button
                v-show="i == possiblePredecessors.length - 1"
                class="small-button"
                variant="success"
                @click="
                  possiblePredecessors.push({
                    id: undefined,
                    name: '',
                  })
                "
              >
                <CreateIcon />
              </b-button>
            </b-col>
            <b-col cols="1">
              <b-button
                v-show="i || (!i && possiblePredecessors.length > 1)"
                class="small-button"
                variant="danger"
                @click="possiblePredecessors.splice(i, 1)"
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
                  @click="
                    possibleAttributes.push({
                      key: '',
                      value: '',
                    })
                  "
                >
                  <CreateIcon />
                </b-button>
              </b-col>
              <b-col cols="1">
                <b-button
                  v-show="i || (!i && possibleAttributes.length > 1)"
                  class="small-button"
                  variant="danger"
                  @click="possibleAttributes.splice(i, 1)"
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

<script lang="ts">
import { DataObjectVue } from "@/utils/api-mixin";
import { emitter } from "@/utils/event-bus";
import { DataObject } from "@dlr-shepard/shepard-client";
import Vue, { PropType, VueConstructor } from "vue";

interface DataObjectModalData {
  newDataObject: DataObject;
  possibleParent: {
    id?: number;
    name: string;
  };
  possiblePredecessors: {
    id?: number;
    name: string;
  }[];
  possibleAttributes: {
    key: string;
    value: string;
  }[];
  validationError: boolean;
}

export default (
  Vue as VueConstructor<Vue & InstanceType<typeof DataObjectVue>>
).extend({
  mixins: [DataObjectVue],
  props: {
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
  },

  data() {
    return {
      newDataObject: {},
      possibleParent: { id: 0, name: "" },
      possiblePredecessors: [] as {
        id?: number;
        name: string;
      }[],
      possibleAttributes: [] as {
        key: string;
        value: string;
      }[],
      validationError: false,
    } as DataObjectModalData;
  },

  methods: {
    prepare() {
      this.newDataObject = this.currentDataObject
        ? { ...this.currentDataObject }
        : { name: "" };
      this.possiblePredecessors = [];
      this.possibleAttributes = [];
      this.validationError = false;

      if (this.currentDataObject?.parentId) {
        let parent = { id: this.currentDataObject.parentId, name: "" };
        this.validateDataObject(parent);
        this.possibleParent = parent;
      } else {
        this.possibleParent = {
          id: undefined,
          name: "",
        };
      }

      if (
        this.currentDataObject?.predecessorIds &&
        this.currentDataObject?.predecessorIds.length > 0
      ) {
        this.currentDataObject?.predecessorIds?.forEach(element => {
          let pre = { id: element, name: "" };
          this.validateDataObject(pre);
          this.possiblePredecessors.push(pre);
        });
      } else {
        this.possiblePredecessors.push({
          id: undefined,
          name: "",
        });
      }

      if (this.currentDataObject?.attributes) {
        Object.entries(this.currentDataObject?.attributes).forEach(
          ([key, value]) => {
            this.possibleAttributes.push({ key: key, value: value });
          },
        );
      }
      if (this.possibleAttributes.length == 0) {
        this.possibleAttributes.push({
          key: "",
          value: "",
        });
      }
    },

    handleOk() {
      if (this.possibleParent.id != undefined) {
        this.newDataObject.parentId = this.possibleParent.id;
      }

      let preIds: number[] = [];
      this.possiblePredecessors.forEach(pre => {
        if (pre.id) {
          preIds.push(pre.id);
        }
      });
      this.newDataObject.predecessorIds = preIds;

      let attributes: { [key: string]: string } = {};
      this.possibleAttributes.forEach(attr => {
        if (attr.key != "") {
          attributes[attr.key] = attr.value;
        }
      });
      this.newDataObject.attributes = attributes;

      if (this.newDataObject.id) {
        this.update();
      } else {
        this.create();
      }
    },

    validateDataObject(obj: { id: number; name: string }) {
      this.dataObjectApi
        ?.getDataObject({
          collectionId: this.currentCollectionId,
          dataObjectId: obj.id,
        })
        .then(response => {
          obj.name = response.name ? response.name : "";
          this.validationError = false;
        })
        .catch(e => {
          obj.name = "";
          const error = "Error while validating data object: " + e.statusText;
          console.log(error);
          this.validationError = true;
        });
    },

    create() {
      this.dataObjectApi
        ?.createDataObject({
          collectionId: this.currentCollectionId,
          dataObject: this.newDataObject,
        })
        .then(response => {
          this.$router.push({
            name: "DataObject",
            params: {
              collectionId: String(this.currentCollectionId),
              dataObjectId: String(response.id),
            },
          });
        })
        .catch(e => {
          const error = "Error while creating data object: " + e.statusText;
          console.log(error);
          emitter.emit("error", error);
        });
    },

    update() {
      if (!this.newDataObject.id) {
        console.log("Unknown dataObject id");
        return;
      }
      this.dataObjectApi
        ?.updateDataObject({
          collectionId: this.currentCollectionId,
          dataObjectId: this.newDataObject.id,
          dataObject: this.newDataObject,
        })
        .then(() => {
          this.$emit("dataObjectChanged");
        })
        .catch(e => {
          const error = "Error while updating data object: " + e.statusText;
          console.log(error);
          emitter.emit("error", error);
        });
    },
  },
});
</script>
