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
              v-model="newFileReference.name"
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
              placeholder="File container id"
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
import FileService from "@/services/fileService";
import { FileContainer, FileReference } from "@dlr-shepard/shepard-client";
import Vue from "vue";

interface Option {
  value: string;
  text: string;
}

interface FileReferenceModelData {
  newFileReference: FileReference;
  possibleOids: Array<Option>;
  selected: Array<string>;
  currentContainerId: string;
  currentContainer?: FileContainer;
  validContainer?: boolean;
}

function initialState(): FileReferenceModelData {
  return {
    newFileReference: {
      name: "",
      fileOids: [],
      fileContainerId: 0,
    },
    possibleOids: [],
    selected: [],
    currentContainerId: "",
    currentContainer: undefined,
    validContainer: undefined,
  };
}

export default Vue.extend({
  props: {
    modalId: {
      type: String,
      default: "FileReferenceModal",
    },
    modalName: {
      type: String,
      default: "FileReferenceModal",
    },
  },

  data() {
    return initialState();
  },

  methods: {
    reset() {
      Object.assign(this.$data, initialState());
    },

    handleOk() {
      this.newFileReference.fileOids = this.selected;
      this.$emit("create", this.newFileReference);
    },

    fetchContainer() {
      FileService.getFileContainer({
        fileContainerId: +this.currentContainerId,
      })
        .then(container => {
          this.currentContainer = container;
          this.validContainer = true;
          this.fetchFiles();
          if (container.id)
            this.newFileReference.fileContainerId = container.id;
        })
        .catch(e => {
          const error = "Error while fetching file container: " + e.statusText;
          console.log(error);
          this.currentContainer = undefined;
          this.validContainer = false;
        });
    },

    fetchFiles() {
      FileService.getAllFiles({
        fileContainerId: +this.currentContainerId,
      })
        .then(response => {
          response.forEach(file => {
            if (!file.oid) {
              return;
            }
            const option: Option = {
              value: file.oid,
              text: file.oid + " - " + file.filename,
            };
            this.possibleOids.push(option);
          });
        })
        .catch(e => {
          const error = "Error while fetching all files: " + e.statusText;
          console.log(error);
        });
    },
  },
});
</script>
