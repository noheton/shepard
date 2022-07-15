<template>
  <b-modal
    :id="modalId"
    ref="modal"
    size="lg"
    :title="modalName"
    lazy
    @show="reset()"
    @ok="handleOk()"
  >
    <b-form-group>
      <b-container>
        <b-row class="mb-3">
          <b-col cols="3"> Name </b-col>
          <b-col cols="9">
            <b-form-input
              v-model="newStructuredDataReference.name"
              placeholder="Name"
              required
            ></b-form-input>
          </b-col>
        </b-row>

        <b-row class="mb-3">
          <b-col cols="3"> Container ID </b-col>
          <b-col cols="9">
            <b-form-input
              v-model="currentContainerId"
              placeholder="Structured data container id"
              type="number"
              required
              :state="validContainer"
              @blur="fetchContainer()"
            ></b-form-input>
            <small v-if="currentContainer">
              <em> {{ currentContainer.name }} </em>
            </small>
            <small v-else>Please enter a valid container id</small>
          </b-col>
        </b-row>

        <b-row class="mb-3">
          <b-col cols="3"> Oids </b-col>
          <b-col cols="9">
            <b-form-select
              v-model="selected"
              :options="possibleOids"
              multiple
              required
            ></b-form-select>
          </b-col>
        </b-row>
      </b-container>
    </b-form-group>
  </b-modal>
</template>

<script lang="ts">
import StructuredDataService from "@/services/structuredDataService";
import type {
  StructuredDataContainer,
  StructuredDataReference,
} from "@dlr-shepard/shepard-client";
import { defineComponent } from "vue";

interface Option {
  value: string;
  text: string;
}

interface StructuredDataReferenceModelData {
  newStructuredDataReference: StructuredDataReference;
  possibleOids: Array<Option>;
  selected: Array<string>;
  currentContainerId: string;
  currentContainer?: StructuredDataContainer;
  validContainer?: boolean;
}

function initialState(): StructuredDataReferenceModelData {
  return {
    newStructuredDataReference: {
      name: "",
      structuredDataOids: [],
      structuredDataContainerId: 0,
    },
    possibleOids: [],
    selected: [],
    currentContainerId: "",
    currentContainer: undefined,
    validContainer: undefined,
  };
}

export default defineComponent({
  props: {
    modalId: {
      type: String,
      default: "StructuredDataReferenceModal",
    },
    modalName: {
      type: String,
      default: "StructuredDataReferenceModal",
    },
  },
  emits: ["create"],
  data() {
    return initialState();
  },

  methods: {
    reset() {
      Object.assign(this.$data, initialState());
    },

    handleOk() {
      this.newStructuredDataReference.structuredDataOids = this.selected;
      this.$emit("create", this.newStructuredDataReference);
    },

    fetchContainer() {
      StructuredDataService.getStructuredDataContainer({
        structureddataContainerId: +this.currentContainerId,
      })
        .then(container => {
          this.currentContainer = container;
          this.validContainer = true;
          this.fetchStructuredData();
          if (container.id)
            this.newStructuredDataReference.structuredDataContainerId =
              container.id;
        })
        .catch(e => {
          const error =
            "Error while fetching structured data container: " + e.statusText;
          console.log(error);
          this.currentContainer = undefined;
          this.validContainer = false;
        });
    },

    fetchStructuredData() {
      StructuredDataService.getAllStructuredDatas({
        structureddataContainerId: +this.currentContainerId,
      })
        .then(response => {
          response.forEach(strdata => {
            if (!strdata.oid) {
              return;
            }
            const option: Option = {
              value: strdata.oid,
              text: strdata.oid + " - " + strdata.name,
            };
            this.possibleOids.push(option);
          });
        })
        .catch(e => {
          const error =
            "Error while getting all structured datas: " + e.statusText;
          console.log(error);
        });
    },
  },
});
</script>
