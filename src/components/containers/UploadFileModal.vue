<template>
  <div>
    <b-modal
      :id="modalId"
      ref="modal"
      size="lg"
      :title="modalName"
      lazy
      @show="handlePrepare()"
      @ok="handleOk()"
    >
      <b-form-file
        v-model="newFile"
        variant="primary"
        placeholder="Upload File"
      >
      </b-form-file>
    </b-modal>
  </div>
</template>

<script lang="ts">
import { FileVue } from "@/utils/api-mixin";
import Vue, { VueConstructor } from "vue";

interface UploadFileModalData {
  newFile?: Blob;
}

export default (
  Vue as VueConstructor<Vue & InstanceType<typeof FileVue>>
).extend({
  mixins: [FileVue],
  props: {
    modalId: {
      type: String,
      default: "FileContainerModal",
    },
    modalName: {
      type: String,
      default: "FileContainerModal",
    },
  },

  data() {
    return {
      newFile: {},
    } as UploadFileModalData;
  },

  methods: {
    handlePrepare() {
      this.newFile = undefined;
    },

    handleOk() {
      this.$emit("created", this.newFile);
    },
  },
});
</script>
