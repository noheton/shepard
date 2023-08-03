<script setup lang="ts">
import EntitySelectionPopover from "@/components/generic/EntitySelectionPopover.vue";
import TimeseriesService from "@/services/timeseriesService";
import { handleError, logError } from "@/utils/error-handling";
import type {
  BasicEntity,
  ResponseError,
  Timeseries,
  TimeseriesContainer,
  TimeseriesReference,
} from "@dlr-shepard/shepard-client";
import { refDebounced } from "@vueuse/core";
import { reactive, ref } from "vue";
import { useSearchContainers } from "../search/InlineSearchContainers";

const props = defineProps({
  modalId: {
    type: String,
    default: "TimeseriesReferenceModal",
  },
  modalName: {
    type: String,
    default: "TimeseriesReferenceModal",
  },
});

const userInputSearchContainer = ref("");
const userInputSearchContainerDebounced = refDebounced(
  userInputSearchContainer,
  700,
);
const { results } = useSearchContainers(
  userInputSearchContainerDebounced,
  "TIMESERIES",
);

const emit = defineEmits(["create"]);

interface Option {
  value: Timeseries;
  text: string;
}

const getInitialFormData = () => ({
  name: "",
  startTime: "",
  endTime: "",
  startDate: "",
  endDate: "",
  currentContainerId: "",
  selected: new Array<Timeseries>(),
});

const formData = reactive(getInitialFormData());
const timeseriesAvailable = ref<Option[]>([]);
const currentContainer = ref<TimeseriesContainer>();
const validContainer = ref<boolean>();

function convertTimeseriesToOption(ts: Timeseries): Option {
  const attrs = [
    ts.measurement,
    ts.device,
    ts.location,
    ts.symbolicName,
    ts.field,
  ];
  return {
    value: { ...ts },
    text: attrs.join(" - "),
  };
}

function reset() {
  Object.assign(formData, getInitialFormData());
  timeseriesAvailable.value = [];
  currentContainer.value = undefined;
  validContainer.value = undefined;
  userInputSearchContainer.value = "";
}

function handleOk() {
  const startTs = Date.parse(formData.startDate + " " + formData.startTime);
  const endTs = Date.parse(formData.endDate + " " + formData.endTime);
  const newTimeseriesReference: TimeseriesReference = {
    timeseriesContainerId: +formData.currentContainerId,
    name: formData.name,
    timeseries: [],
    start: startTs * 1e6,
    end: endTs * 1e6,
  };
  formData.selected.forEach(option => {
    if (option) newTimeseriesReference.timeseries.push(option);
  });
  emit("create", newTimeseriesReference);
}

function resetSelection() {
  timeseriesAvailable.value = [];
  formData.selected = [];
}

function chooseContainer(container: BasicEntity) {
  if (!container.id) return;
  userInputSearchContainer.value = String(container.id);
  formData.currentContainerId = String(container.id);
  fetchContainer(container.id);
}

function fetchContainer(id: number) {
  resetSelection();
  TimeseriesService.getTimeseriesContainer({
    timeseriesContainerId: id,
  })
    .then(container => {
      currentContainer.value = container;
      validContainer.value = true;
      fetchTimeseriesAvailable(id);
    })
    .catch(e => {
      logError(e as ResponseError, "fetching timeseries container");
      currentContainer.value = undefined;
      validContainer.value = false;
    });
}

function fetchTimeseriesAvailable(id: number) {
  TimeseriesService.getTimeseriesAvailable({
    timeseriesContainerId: id,
  })
    .then(result => {
      timeseriesAvailable.value = result.map(ts =>
        convertTimeseriesToOption(ts),
      );
    })
    .catch(e => {
      handleError(e as ResponseError, "fetching all timeseries");
    });
}

function validateDate(input: string) {
  const parsed = Date.parse(input);
  return isNaN(parsed) ? "" : new Date(parsed).toISOString().split("T")[0];
}

function validateTime(input: string) {
  const parsed = Date.parse("1970-01-01 " + input);
  return isNaN(parsed) ? "" : new Date(parsed).toLocaleTimeString();
}
</script>

<template>
  <b-modal
    :id="props.modalId"
    ref="modal"
    size="lg"
    :title="props.modalName"
    lazy
    @show="reset()"
    @ok="handleOk()"
  >
    <b-container>
      <b-form-group label-cols="3" label="Name" label-for="input-name">
        <b-form-input
          id="input-name"
          v-model="formData.name"
          placeholder="Name"
          required
        ></b-form-input>
      </b-form-group>

      <b-form-group
        label-cols="3"
        label="Container ID"
        label-for="userFormInput"
      >
        <b-form-input
          id="userFormInput"
          v-model="userInputSearchContainer"
          placeholder="Timeseries container id"
          required
          :state="validContainer"
          @blur="
            if (!isNaN(+userInputSearchContainer))
              fetchContainer(+userInputSearchContainer);
          "
        ></b-form-input>
        <small v-if="currentContainer">
          <em> {{ currentContainer.name }} </em>
        </small>
        <small v-else>Please enter a valid container id</small>
        <EntitySelectionPopover
          :results="results"
          title-text="search for timeseries containers by name, username, id or description"
          @selected="chooseContainer($event)"
        />
      </b-form-group>

      <b-row class="mb-3">
        <b-col cols="6">
          <b-form-group label="Start">
            <b-input-group class="mb-1">
              <b-form-input
                id="start-date"
                v-model="formData.startDate"
                type="text"
                placeholder="YYYY-MM-DD"
                autocomplete="off"
                required
                @blur="formData.startDate = validateDate(formData.startDate)"
              ></b-form-input>
              <b-input-group-append>
                <b-form-datepicker
                  v-model="formData.startDate"
                  button-only
                  right
                  locale="en-US"
                  aria-controls="start-date"
                ></b-form-datepicker> </b-input-group-append
            ></b-input-group>

            <b-input-group class="mb-1">
              <b-form-input
                id="start-time"
                v-model="formData.startTime"
                type="text"
                placeholder="HH:mm:ss"
                @blur="formData.startTime = validateTime(formData.startTime)"
              ></b-form-input>
              <b-input-group-append>
                <b-form-timepicker
                  v-model="formData.startTime"
                  button-only
                  right
                  show-seconds
                  now-button
                  required
                  aria-controls="start-time"
                ></b-form-timepicker>
              </b-input-group-append>
            </b-input-group>
          </b-form-group>
        </b-col>

        <b-col cols="6">
          <b-form-group label="End">
            <b-input-group class="mb-1">
              <b-form-input
                id="end-date"
                v-model="formData.endDate"
                type="text"
                placeholder="YYYY-MM-DD"
                autocomplete="off"
                required
                @blur="formData.endDate = validateDate(formData.endDate)"
              ></b-form-input>
              <b-input-group-append>
                <b-form-datepicker
                  v-model="formData.endDate"
                  button-only
                  right
                  locale="en-US"
                  aria-controls="end-date"
                ></b-form-datepicker> </b-input-group-append
            ></b-input-group>

            <b-input-group class="mb-1">
              <b-form-input
                id="end-time"
                v-model="formData.endTime"
                type="text"
                placeholder="HH:mm:ss"
                @blur="formData.endTime = validateTime(formData.endTime)"
              ></b-form-input>
              <b-input-group-append>
                <b-form-timepicker
                  v-model="formData.endTime"
                  button-only
                  right
                  show-seconds
                  now-button
                  required
                  aria-controls="end-time"
                ></b-form-timepicker>
              </b-input-group-append>
            </b-input-group>
          </b-form-group>
        </b-col>
      </b-row>

      <b-form-group label="Choose timeseries">
        <b-form-select
          v-model="formData.selected"
          class="mb-1"
          :options="timeseriesAvailable"
          :select-size="5"
          multiple
          required
        ></b-form-select>
      </b-form-group>
    </b-container>
  </b-modal>
</template>
