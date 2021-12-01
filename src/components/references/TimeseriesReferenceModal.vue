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
              v-model="newTimeseriesReference.name"
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
              placeholder="Timeseries container id"
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

        <b-row class="mb-1">
          <b-col cols="6"> Start </b-col>
          <b-col cols="6"> End </b-col>
        </b-row>

        <b-row class="mb-1">
          <b-col cols="6">
            <b-form-input
              v-model="startDate"
              type="date"
              required
            ></b-form-input>
          </b-col>
          <b-col cols="6">
            <b-form-input v-model="endDate" type="date" required></b-form-input>
          </b-col>
        </b-row>

        <b-row class="mb-3">
          <b-col cols="6">
            <b-form-input
              v-model="startTime"
              type="time"
              required
            ></b-form-input>
          </b-col>
          <b-col cols="6">
            <b-form-input v-model="endTime" type="time" required></b-form-input>
          </b-col>
        </b-row>

        <b-row class="mb-1">
          <b-col cols="12"> Add timeseries </b-col>
        </b-row>

        <b-row class="mb-1">
          <b-col cols="12">
            <b-form-input
              v-model="measurment"
              placeholder="Measurment"
              :state="validTimeseries"
            ></b-form-input>
          </b-col>
        </b-row>

        <b-row class="mb-1">
          <b-col cols="6">
            <b-form-input
              v-model="device"
              placeholder="Device"
              :state="validTimeseries"
            ></b-form-input>
          </b-col>
          <b-col cols="6">
            <b-form-input
              v-model="location"
              placeholder="Location"
              :state="validTimeseries"
            ></b-form-input>
          </b-col>
        </b-row>

        <b-row class="mb-1">
          <b-col cols="6">
            <b-form-input
              v-model="symbolicName"
              placeholder="Symbolic Name"
              :state="validTimeseries"
            ></b-form-input>
          </b-col>
          <b-col cols="6">
            <b-form-input
              v-model="field"
              placeholder="Field"
              :state="validTimeseries"
            ></b-form-input>
          </b-col>
        </b-row>

        <b-row class="mb-3 text-right">
          <b-col cols="12">
            <b-button class="mr-2" variant="primary" @click="handleClear()">
              Clear
            </b-button>
            <b-button variant="success" @click="handleAdd()"> Add </b-button>
          </b-col>
        </b-row>

        <b-row class="mb-1">
          <b-col cols="12"> Added timeseries </b-col>
        </b-row>

        <b-row class="mb-1">
          <b-col cols="12">
            <b-form-select
              v-model="selectedTimeseries"
              :options="timeseries"
              :select-size="5"
              multiple
              required
            ></b-form-select>
          </b-col>
        </b-row>

        <b-row class="mb-3 text-right">
          <b-col cols="12">
            <b-button variant="danger" @click="handleDelete()">
              Remove selected
            </b-button>
          </b-col>
        </b-row>
      </b-container>
    </b-form-group>
  </b-modal>
</template>

<script lang="ts">
import { TimeseriesVue } from "@/utils/api-mixin";
import {
  Timeseries,
  TimeseriesContainer,
  TimeseriesReference,
} from "@dlr-shepard/shepard-client";
import Vue, { VueConstructor } from "vue";

interface Option {
  value: Timeseries;
  text: string;
}

interface TimeseriesRefernceModalData {
  newTimeseriesReference: TimeseriesReference;
  timeseries: Array<Option>;
  selectedTimeseries: Array<Option>;
  measurment: string;
  device: string;
  location: string;
  symbolicName: string;
  field: string;
  startTime: string;
  endTime: string;
  startDate: string;
  endDate: string;
  currentContainerId: string;
  currentContainer?: TimeseriesContainer;
  validContainer?: boolean;
  validTimeseries?: boolean;
}

function initialState(): TimeseriesRefernceModalData {
  return {
    newTimeseriesReference: {
      name: "",
      timeseries: [],
      timeseriesContainerId: 0,
      start: 0,
      end: 0,
    },
    timeseries: [],
    selectedTimeseries: [],
    measurment: "",
    device: "",
    location: "",
    symbolicName: "",
    field: "",
    startTime: "",
    endTime: "",
    startDate: "",
    endDate: "",
    currentContainerId: "",
    currentContainer: undefined,
    validContainer: undefined,
    validTimeseries: undefined,
  };
}

export default (
  Vue as VueConstructor<Vue & InstanceType<typeof TimeseriesVue>>
).extend({
  mixins: [TimeseriesVue],
  props: {
    modalId: {
      type: String,
      default: "TimeseriesReferenceModal",
    },
    modalName: {
      type: String,
      default: "TimeseriesReferenceModal",
    },
  },

  data() {
    return initialState();
  },

  methods: {
    reset() {
      Object.assign(this.$data, initialState());
    },
    handleAdd() {
      if (
        !this.measurment ||
        !this.device ||
        !this.location ||
        !this.symbolicName ||
        !this.field
      ) {
        this.validTimeseries = false;
        return;
      }
      this.validTimeseries = undefined;
      const option: Option = {
        value: {
          measurement: this.measurment,
          device: this.device,
          location: this.location,
          symbolicName: this.symbolicName,
          field: this.field,
        },
        text: [
          this.measurment,
          this.device,
          this.location,
          this.symbolicName,
          this.field,
        ].join(" - "),
      };

      if (
        !this.timeseries.some(timeseries => timeseries.text === option.text)
      ) {
        this.timeseries.push(option);
      }
    },
    handleDelete() {
      this.selectedTimeseries.forEach(selected => {
        const index = this.timeseries.findIndex(
          option => JSON.stringify(option.value) == JSON.stringify(selected),
        );
        if (index > -1) {
          this.timeseries.splice(index, 1);
        }
      });
    },
    handleClear() {
      this.measurment = "";
      this.device = "";
      this.location = "";
      this.symbolicName = "";
      this.field = "";
    },
    handleOk() {
      this.timeseries.forEach(option => {
        this.newTimeseriesReference.timeseries.push(option.value);
      });
      const startTimestamp = Date.parse(this.startDate + " " + this.startTime);
      const endTimestamp = Date.parse(this.endDate + " " + this.endTime);
      this.newTimeseriesReference.start = startTimestamp * 1e6;
      this.newTimeseriesReference.end = endTimestamp * 1e6;
      this.$emit("create", this.newTimeseriesReference);
    },
    fetchContainer() {
      this.timeseriesApi
        ?.getTimeseriesContainer({
          timeseriesContainerId: +this.currentContainerId,
        })
        .then(container => {
          this.currentContainer = container;
          this.validContainer = true;
          if (container.id)
            this.newTimeseriesReference.timeseriesContainerId = container.id;
        })
        .catch(e => {
          const error =
            "Error while getting timeseries container: " + e.statusText;
          console.log(error);
          this.currentContainer = undefined;
          this.validContainer = false;
        });
    },
  },
});
</script>
