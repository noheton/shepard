<script setup lang="ts">
import type { TimeseriesRefItem } from "./CreateDataReferenceDialog.vue";
import type { TimeseriesRef } from "./DataRef";
import TimeseriesReferenceTimePicker from "./TimeseriesReferenceTimePicker.vue";

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

type ChannelOption = {
  title: string;
  subtitle: string;
  value: TimeseriesRefItem;
};

const selectedChannels = ref<ChannelOption[]>([]);

const startDate = ref();
const endDate = ref();
const startTime = ref();
const endTime = ref();

const channelOptions = computed<ChannelOption[]>(() => {
  if (!items) return [];
  return items.map(item => ({
    title: `${item.measurement} · ${item.field}`,
    subtitle: `${item.device} / ${item.location} / ${item.symbolicName}`,
    value: item,
  }));
});

watch(selectedChannels, () => {
  const selectedItems = selectedChannels.value.map(opt => opt.value);
  if (selectedItems.length > 0) {
    if (timeseriesReference.value?.timeseries !== undefined) {
      timeseriesReference.value.timeseries = selectedItems;
    } else {
      timeseriesReference.value = {
        start: 0,
        end: 0,
        timeseries: selectedItems,
      };
    }
  } else {
    if (timeseriesReference.value) {
      timeseriesReference.value.timeseries = [];
    }
  }
});

watch([startDate, startTime], () => {
  if (!!startDate.value && !!startTime.value) {
    const formattedStart =
      Date.parse(startDate.value + " " + startTime.value) * 1e6;
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
    const formattedEnd = Date.parse(endDate.value + " " + endTime.value) * 1e6;

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
    <v-col cols="12" class="pb-1">
      <v-autocomplete
        v-model="selectedChannels"
        :items="channelOptions"
        :loading="loading"
        item-title="title"
        item-value="value"
        label="Channels* (type to filter by measurement or field)"
        placeholder="e.g. vibration · value"
        density="compact"
        variant="outlined"
        bg-color="canvas"
        multiple
        chips
        closable-chips
        clearable
        no-data-text="No channels available in this container"
        :return-object="true"
      >
        <template #chip="{ props: chipProps, item }">
          <v-chip
            v-bind="chipProps"
            :text="item.raw.title"
            size="small"
          />
        </template>
        <template #item="{ props: itemProps, item }">
          <v-list-item
            v-bind="itemProps"
            :title="item.raw.title"
            :subtitle="item.raw.subtitle"
            density="compact"
          />
        </template>
      </v-autocomplete>
    </v-col>
  </v-row>
  <v-row>
    <div class="text-subtitle-1 pt-6 pb-5">Select Time Window (UTC)</div>
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
