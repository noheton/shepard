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
    <b-container>
      <b-form-group label-cols="3" label="Name" label-for="input-name">
        <b-form-input
          id="input-name"
          v-model="newTimeseriesReference.name"
          placeholder="Name"
          required
        ></b-form-input>
      </b-form-group>

      <b-form-group label-cols="3" label="Container ID" label-for="input-id">
        <b-form-input
          id="input-id"
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
      </b-form-group>

      <b-row class="mb-3">
        <b-col cols="6">
          <b-form-group label="Start">
            <b-form-datepicker
              v-model="startDate"
              class="mb-1"
              required
            ></b-form-datepicker>
            <b-form-timepicker
              v-model="startTime"
              class="mb-1"
              show-seconds
              now-button
              reset-button
              required
            ></b-form-timepicker>
          </b-form-group>
        </b-col>

        <b-col cols="6">
          <b-form-group label="End">
            <b-form-datepicker
              v-model="endDate"
              class="mb-1"
              required
            ></b-form-datepicker>
            <b-form-timepicker
              v-model="endTime"
              class="mb-1"
              show-seconds
              now-button
              reset-button
              required
            ></b-form-timepicker>
          </b-form-group>
        </b-col>
      </b-row>

      <b-form-group label="Choose timeseries">
        <b-form-select
          v-model="currentTimeseries"
          class="mb-1"
          :options="timeseriesAvailable"
          required
        ></b-form-select>
        <b-input-group prepend="Field" class="mb-1">
          <b-form-input v-model="field" placeholder="Field"></b-form-input>
        </b-input-group>
        <b-button class="float-right" variant="success" @click="handleAdd()">
          Add
        </b-button>
      </b-form-group>

      <b-form-group label="Added timeseries">
        <b-form-select
          v-model="selectedTimeseries"
          class="mb-1"
          :options="timeseries"
          :select-size="5"
          multiple
          required
        ></b-form-select>

        <b-button class="float-right" variant="danger" @click="handleDelete()">
          Remove selected
        </b-button>
      </b-form-group>
    </b-container>
  </b-modal>
</template>

<script lang="ts">
import TimeseriesService from "@/services/timeseriesService";
import { handleError, logError } from "@/utils/error-handling";
import type {
  ResponseError,
  Timeseries,
  TimeseriesContainer,
  TimeseriesReference,
} from "@dlr-shepard/shepard-client";
import { defineComponent } from "vue";

interface Option {
  value: Timeseries | null;
  text: string;
}

interface TimeseriesRefernceModalData {
  newTimeseriesReference: TimeseriesReference;
  timeseriesAvailable: Array<Option>;
  currentTimeseries: Timeseries | null;
  timeseries: Array<Option>;
  selectedTimeseries: Array<Option>;
  field: string;
  startTime: string;
  endTime: string;
  startDate: string;
  endDate: string;
  currentContainerId: string;
  currentContainer?: TimeseriesContainer;
  validContainer?: boolean;
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
    timeseriesAvailable: [{ value: null, text: "Please select an option" }],
    currentTimeseries: null,
    timeseries: [],
    selectedTimeseries: [],
    field: "value",
    startTime: "",
    endTime: "",
    startDate: "",
    endDate: "",
    currentContainerId: "",
    currentContainer: undefined,
    validContainer: undefined,
  };
}

function convertTimeseriesToOption(ts: Timeseries, field = true): Option {
  const attrs = [ts.measurement, ts.device, ts.location, ts.symbolicName];
  if (field) {
    attrs.push(ts.field);
  }
  return {
    value: { ...ts },
    text: attrs.join(" - "),
  };
}

export default defineComponent({
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
  emits: ["create"],
  data() {
    return initialState();
  },

  methods: {
    reset() {
      Object.assign(this.$data, initialState());
    },
    resetSelection() {
      this.timeseriesAvailable = [
        { value: null, text: "Please select an option" },
      ];
      this.currentTimeseries = null;
      this.timeseries = [];
      this.selectedTimeseries = [];
    },
    handleAdd() {
      if (!this.currentTimeseries || !this.field) {
        return;
      }
      this.currentTimeseries.field = this.field;
      const option = convertTimeseriesToOption(this.currentTimeseries);

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
      this.currentTimeseries = null;
      this.field = "";
    },
    handleOk() {
      this.timeseries.forEach(option => {
        if (option.value)
          this.newTimeseriesReference.timeseries.push(option.value);
      });
      const startTimestamp = Date.parse(this.startDate + " " + this.startTime);
      const endTimestamp = Date.parse(this.endDate + " " + this.endTime);
      this.newTimeseriesReference.start = startTimestamp * 1e6;
      this.newTimeseriesReference.end = endTimestamp * 1e6;
      this.$emit("create", this.newTimeseriesReference);
    },
    fetchContainer() {
      if (this.currentContainer?.id == +this.currentContainerId) return;
      this.resetSelection();
      TimeseriesService.getTimeseriesContainer({
        timeseriesContainerId: +this.currentContainerId,
      })
        .then(container => {
          this.fetchTimeseriesAvailable();
          this.currentContainer = container;
          this.validContainer = true;
          if (container.id)
            this.newTimeseriesReference.timeseriesContainerId = container.id;
        })
        .catch(e => {
          logError(e as ResponseError, "fetching timeseries container");
          this.currentContainer = undefined;
          this.validContainer = false;
        });
    },
    fetchTimeseriesAvailable() {
      TimeseriesService.getTimeseriesAvailable({
        timeseriesContainerId: +this.currentContainerId,
      })
        .then(result => {
          this.timeseriesAvailable = result.map(ts =>
            convertTimeseriesToOption(ts, false),
          );
        })
        .catch(e => {
          handleError(e as ResponseError, "fetching all timeseries");
        });
    },
  },
});
</script>
