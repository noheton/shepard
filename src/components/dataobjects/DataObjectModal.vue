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

<script lang="ts">
import DataObjectService from "@/services/dataObjectService";
import { emitter } from "@/utils/event-bus";
import { DataObject } from "@dlr-shepard/shepard-client";
import Vue, { PropType } from "vue";

interface PossibleDataObject {
  id?: number;
  name: string;
}

interface DataObjectModalData {
  newDataObject: DataObject;
  validParent?: boolean;
  validPredecessors: Array<boolean | undefined>;
  possibleParent: PossibleDataObject;
  possiblePredecessors: PossibleDataObject[];
  possibleAttributes: {
    key: string;
    value: string;
  }[];
}

export default Vue.extend({
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
      newDataObject: { name: "" },
      validParent: undefined,
      validPredecessors: [],
      possibleParent: { id: undefined, name: "" },
      possiblePredecessors: [],
      possibleAttributes: [],
    } as DataObjectModalData;
  },

  methods: {
    prepare() {
      this.newDataObject = this.currentDataObject
        ? { ...this.currentDataObject }
        : { name: "" };
      this.possiblePredecessors = [];
      this.possibleAttributes = [];
      this.validPredecessors = [undefined];

      if (this.currentDataObject?.parentId) {
        let parent = { id: this.currentDataObject.parentId, name: "" };
        this.validateParent();
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
        for (
          let i = 0;
          i < this.currentDataObject?.predecessorIds.length;
          i++
        ) {
          this.possiblePredecessors.push({
            id: this.currentDataObject?.predecessorIds[i],
            name: "",
          });
          this.validatePredecessor(i);
        }
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

    addAttribute() {
      this.possibleAttributes.push({
        key: "",
        value: "",
      });
    },

    removeAttribute(i: number) {
      this.possibleAttributes.splice(i, 1);
    },

    addPredecessor() {
      this.possiblePredecessors.push({
        id: undefined,
        name: "",
      });
      this.validPredecessors.push(undefined);
    },

    removePredecessor(i: number) {
      this.possiblePredecessors.splice(i, 1);
      this.validPredecessors.splice(i, 1);
    },

    validateParent() {
      if (this.possibleParent.id == undefined) {
        this.possibleParent.name = "";
        this.validParent = undefined;
      } else {
        DataObjectService.getDataObject({
          collectionId: this.currentCollectionId,
          dataObjectId: this.possibleParent.id,
        })
          .then(response => {
            this.possibleParent.name = response.name ? response.name : "";
            if (this.possibleParent.name == "") {
              this.validParent = undefined;
            } else {
              this.validParent = true;
            }
          })
          .catch(() => {
            this.possibleParent.name = "";
            this.validParent = false;
          });
      }
    },

    validatePredecessor(i: number) {
      const id = this.possiblePredecessors[i].id;
      if (id == undefined) {
        this.possiblePredecessors[i].name = "";
        this.validParent = undefined;
      } else {
        DataObjectService.getDataObject({
          collectionId: this.currentCollectionId,
          dataObjectId: id,
        })
          .then(response => {
            this.possiblePredecessors[i].name = response.name
              ? response.name
              : "";
            if (this.possiblePredecessors[i].name == "") {
              this.validPredecessors[i] = undefined;
            } else {
              this.validPredecessors[i] = true;
            }
            this.validPredecessors = [...this.validPredecessors];
            this.possiblePredecessors = [...this.possiblePredecessors];
          })
          .catch(() => {
            this.possiblePredecessors[i].name = "";
            this.validPredecessors[i] = false;
          });
      }
    },

    create() {
      DataObjectService.createDataObject({
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
      DataObjectService.updateDataObject({
        collectionId: this.currentCollectionId,
        dataObjectId: this.newDataObject.id,
        dataObject: this.newDataObject,
      })
        .then(() => {
          this.$emit("data-object-changed");
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
