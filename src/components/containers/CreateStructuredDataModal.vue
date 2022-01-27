<template>
  <div>
    <b-modal
      :id="modalId"
      ref="modal"
      size="lg"
      :title="modalName"
      @show="handlePrepare()"
      @ok="handleOk()"
    >
      <b-form-group>
        <b-container>
          <b-row class="mb-3">
            <b-col cols="2"> Name </b-col>
            <b-col cols="10">
              <b-form-input
                v-model="newStructuredDataName"
                variant="primary"
                placeholder="Name"
                required
              >
              </b-form-input>
            </b-col>
          </b-row>

          <b-row class="mb-3">
            <b-col cols="2"> JSON String </b-col>
            <b-col cols="10">
              <b-form-textarea
                v-model="newStructuredDataPayload.payload"
                variant="primary"
                placeholder="Insert Structured Data Payload as valid JSON"
                rows="3"
                max-rows="6"
                :state="validJSON"
                @change="validateJSON(newStructuredDataPayload.payload)"
              >
              </b-form-textarea>
            </b-col>
          </b-row>
          <b-row>
            <b-col cols="2"> Preview </b-col>
            <b-col cols="10">
              <b-link @click="toggleReadMore()">
                <span v-if="readMore"><CollapsIcon /></span>
                <span v-else><ExtendIcon /></span>
              </b-link>
              <b>Payload:</b>
              <span v-if="newStructuredDataPayload.payload">
                <span v-if="readMore">
                  <pre class="payload">{{
                    newStructuredDataPayload.payload | pretty
                  }}</pre>
                </span>
              </span>
            </b-col>
          </b-row>
        </b-container>
      </b-form-group>
      <div id="jsoneditor" ref="input"></div>
    </b-modal>
  </div>
</template>

<script lang="ts">
import { StructuredDataVue } from "@/utils/api-mixin";
import { StructuredDataPayload } from "@dlr-shepard/shepard-client";
import Vue, { VueConstructor } from "vue";

interface CreateStructuredDataModalData {
  newStructuredDataPayload: StructuredDataPayload;
  readMore: boolean;
  newStructuredDataName?: string;
  validJSON?: boolean;
}

function initialState(): CreateStructuredDataModalData {
  return {
    newStructuredDataPayload: {},
    newStructuredDataName: undefined,
    validJSON: undefined,
    readMore: false,
  };
}

export default (
  Vue as VueConstructor<Vue & InstanceType<typeof StructuredDataVue>>
).extend({
  filters: {
    pretty: function (value: string) {
      if (value) {
        try {
          return JSON.stringify(JSON.parse(value), null, 2);
        } catch (e) {
          console.log("no valid JSON");
        }
      }
    },
  },
  mixins: [StructuredDataVue],
  props: {
    modalId: {
      type: String,
      default: "CreateStructuredDataModal",
    },
    modalName: {
      type: String,
      default: "CreateStructuredDataModal",
    },
  },

  data() {
    return initialState();
  },
  methods: {
    handlePrepare() {
      initialState();
      if (this.newStructuredDataPayload.structuredData?.name)
        this.newStructuredDataPayload.structuredData = {
          name: this.newStructuredDataPayload.structuredData?.name,
        };
    },
    validateJSON(payload: string) {
      try {
        JSON.parse(payload);
        this.validJSON = true;
      } catch (e) {
        this.validJSON = false;
      }
    },

    handleOk() {
      this.newStructuredDataPayload.structuredData = {
        name: this.newStructuredDataName,
      };
      if (!this.newStructuredDataPayload.payload)
        this.newStructuredDataPayload.payload = "{}";

      this.$emit("created", this.newStructuredDataPayload);
    },
    toggleReadMore() {
      this.readMore = !this.readMore;
    },
  },
});
</script>

<style scoped>
.payload {
  color: #e83e8c;
}
</style>
