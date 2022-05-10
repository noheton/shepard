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
              v-model="newCollection.name"
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
              v-model="newCollection.description"
              placeholder="Description"
              rows="3"
              max-rows="6"
            >
            </b-form-textarea>
          </b-col>
        </b-row>
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
import CollectionService from "@/services/collectionService";
import { emitter } from "@/utils/event-bus";
import { Collection } from "@dlr-shepard/shepard-client";
import Vue, { PropType } from "vue";

interface CollectionModalData {
  newCollection: Collection;
  possibleAttributes: {
    key: string;
    value: string;
  }[];
  validationError: boolean;
}

export default Vue.extend({
  props: {
    modalId: {
      type: String,
      default: "collectionModal",
    },
    modalName: {
      type: String,
      default: "collectionModal",
    },
    currentCollection: {
      type: Object as PropType<Collection>,
      default: undefined,
    },
  },

  data() {
    return {
      newCollection: {},
      possibleAttributes: [] as {
        key: string;
        value: string;
      }[],
      validationError: false,
    } as CollectionModalData;
  },

  methods: {
    prepare() {
      this.newCollection = this.currentCollection
        ? { ...this.currentCollection }
        : { name: "" };
      this.possibleAttributes = [];
      this.validationError = false;

      if (this.currentCollection?.attributes) {
        Object.entries(this.currentCollection?.attributes).forEach(
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
      let attributes: { [key: string]: string } = {};
      this.possibleAttributes.forEach(attr => {
        if (attr.key != "") {
          attributes[attr.key] = attr.value;
        }
      });
      this.newCollection.attributes = attributes;

      if (this.currentCollection) {
        this.updateCollection(this.newCollection);
      } else {
        this.createCollection(this.newCollection);
      }
    },
    createCollection(collection: Collection) {
      CollectionService.createCollection({
        collection: collection,
      })
        .then(response => {
          this.$router.push({
            name: "Collection",
            params: {
              collectionId: String(response.id),
            },
          });
        })
        .catch(e => {
          const error = "Error while creating collection: " + e.statusText;
          console.log(error);
          emitter.emit("error", error);
        });
    },
    updateCollection(collection: Collection) {
      if (!this.currentCollection.id) return;
      CollectionService.updateCollection({
        collectionId: this.currentCollection.id,
        collection: collection,
      })
        .then(() => {
          this.$emit("collection-changed");
        })
        .catch(e => {
          const error = "Error while updating collection: " + e.statusText;
          console.log(error);
          emitter.emit("error", error);
        });
    },
  },
});
</script>
