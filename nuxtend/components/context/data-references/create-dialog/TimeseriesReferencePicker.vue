<script setup lang="ts">
import { useDate } from "vuetify";
import type { TimeseriesRefItem } from "./CreateDataReferenceDialog.vue";
import type { TimeseriesRef } from "./DataRef";
import TimeseriesReferenceTimePicker from "./TimeseriesReferenceTimePicker.vue";

const adapter = useDate();

const { items, loading } = defineProps<{
  items: TimeseriesRefItem[] | undefined;
  loading: boolean;
}>();

const timeseriesReference = defineModel<TimeseriesRef | undefined>(
  "timeseriesReference",
  {
    required: true,
  },
);

const headers = [
  { title: "Measurement", key: "measurement", sortable: true },
  { title: "Device", key: "device", sortable: true },
  { title: "Location", key: "location", sortable: true },
  { title: "Symbolic Name", key: "symbolicName", sortable: true },
  { title: "Field", key: "field", sortable: true },
];

const selectedTimeseriesList = ref<TimeseriesRefItem[] | undefined>(undefined);

const startDate = ref();
const endDate = ref();
const startTime = ref();
const endTime = ref();

watch(
  () => loading,
  () => {
    if (loading === false) selectedTimeseriesList.value = items;
  },
  { once: true },
);

watch(selectedTimeseriesList, () => {
  if (selectedTimeseriesList.value) {
    if (timeseriesReference.value?.timeseries) {
      timeseriesReference.value.timeseries = selectedTimeseriesList.value;
      return;
    }
    timeseriesReference.value = {
      start: 0,
      end: 0,
      timeseries: selectedTimeseriesList.value,
    };
  }
});

watch([startDate, startTime], () => {
  if (!!startDate.value && !!startTime.value) {
    const formattedStart =
      Date.parse(adapter.toISO(startDate.value) + " " + startTime.value) * 1e6;
    if (timeseriesReference.value?.start !== undefined)
      timeseriesReference.value.start = formattedStart;
    else
      timeseriesReference.value = {
        start: formattedStart,
        end: 0,
        timeseries: [],
      };
  }
});
watch([endDate, endTime], () => {
  if (!!endDate.value && !!endTime.value) {
    const formattedEnd =
      Date.parse(adapter.toISO(endDate.value) + " " + endTime.value) * 1e6;

    if (timeseriesReference.value?.end !== undefined)
      timeseriesReference.value.end = formattedEnd;
    else
      timeseriesReference.value = {
        start: 0,
        end: formattedEnd,
        timeseries: [],
      };
  }
});
</script>

<template>
  <v-row>
    <v-data-table
      v-model="selectedTimeseriesList"
      sort-desc-icon="mdi-triangle-small-down"
      sort-asc-icon="mdi-triangle-small-up"
      density="compact"
      :headers="headers"
      :items="items"
      :item-value="item => item"
      :loading="loading"
      fixed-header
      style="max-height: 300px"
      hover
      hide-default-footer
      show-select
      select-strategy="all"
    >
      <template #bottom>
        <v-divider :thickness="8" color="divider2" opacity="1" />
      </template>
    </v-data-table>
  </v-row>
  <v-row>
    <div class="text-subtitle-1 pt-9 pb-5">Select Time Window (UTC)</div>
  </v-row>
  <v-row>
    <v-col cols="1" class="pa-0"><div class="pt-1">Start*:</div></v-col>
    <TimeseriesReferenceTimePicker
      v-model:date="startDate"
      v-model:time="startTime"
    />
  </v-row>
  <v-row>
    <v-col cols="1" class="pa-0 align-center">
      <div class="pt-1">End*:</div>
    </v-col>
    <TimeseriesReferenceTimePicker
      v-model:date="endDate"
      v-model:time="endTime"
    />
  </v-row>
</template>

<style scoped lang="scss">
.v-table {
  background-color: unset;

  :deep(thead) > tr > th {
    background-color: rgb(var(--v-theme-divider2)) !important;
  }

  :deep(tr):hover {
    background-color: rgb(var(--v-theme-focus1));
  }

  :deep(.mdi) {
    margin-left: 0.2em;
  }
}
</style>
