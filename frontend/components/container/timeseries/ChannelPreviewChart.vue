<script setup lang="ts">
import type { TimeseriesEntity } from "@dlr-shepard/backend-client";
import { useFetchChannelPreview } from "~/composables/container/useFetchChannelPreview";
import type { TimeseriesSeries } from "~/components/common/chart/types";

const props = defineProps<{
  channel: TimeseriesEntity;
  containerId: number;
}>();

const { data, loading } = useFetchChannelPreview(props.containerId, props.channel);

const series = computed<TimeseriesSeries[]>(() => {
  if (!data.value.length) return [];
  const label = [props.channel.device, props.channel.field]
    .filter(Boolean)
    .join(" · ");
  return [{ name: label, data: data.value, color: "#4097CC" }];
});
</script>

<template>
  <div class="channel-preview pa-2">
    <div v-if="loading" class="d-flex justify-center align-center" style="height:200px">
      <v-progress-circular indeterminate size="24" />
    </div>
    <div
      v-else-if="!series.length"
      class="d-flex justify-center align-center text-body-2 text-medium-emphasis"
      style="height:200px"
    >
      No data available
    </div>
    <TimeseriesChart v-else :series="series" height="200px" />
  </div>
</template>
