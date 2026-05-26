<script setup lang="ts">
import type { Timeseries } from "@dlr-shepard/backend-client";
import type { ColormapName } from "~/utils/colormap";

const props = defineProps<{
  containerId: number;
  channels: Timeseries[];
  startNs: number;
  endNs: number;
}>();

const open = defineModel<boolean>({ default: false });

const colormapOptions: ColormapName[] = ["inferno", "viridis", "plasma"];

const xIdx    = ref<number | null>(null);
const yIdx    = ref<number | null>(null);
const zIdx    = ref<number | null>(null);
const valueIdx = ref<number | null>(null);
const colormap = ref<ColormapName>("inferno");

const channelItems = computed(() =>
  props.channels.map((ch, i) => ({
    title: [ch.device, ch.field, ch.measurement].filter(Boolean).join(" · "),
    value: i,
  })),
);

function openTrace3D() {
  const pick = (i: number | null): Timeseries | null =>
    i !== null ? (props.channels[i] ?? null) : null;
  const x = pick(xIdx.value);
  const y = pick(yIdx.value);
  const z = pick(zIdx.value);
  const v = pick(valueIdx.value);
  if (!x || !y || !z) return;

  const roles = { x, y, z, ...(v ? { value: v } : {}) };
  const rolesParam = btoa(JSON.stringify(roles));

  navigateTo({
    path: "/shapes/render",
    query: {
      containerId: String(props.containerId),
      startNs:    String(props.startNs),
      endNs:      String(props.endNs),
      colormap:   colormap.value,
      roles:      rolesParam,
    },
  });
}

const canOpen = computed(
  () => xIdx.value !== null && yIdx.value !== null && zIdx.value !== null,
);

watch(open, (v) => {
  if (v) {
    xIdx.value = null; yIdx.value = null; zIdx.value = null;
    valueIdx.value = null; colormap.value = "inferno";
  }
});
</script>

<template>
  <v-dialog v-model="open" max-width="480">
    <v-card>
      <v-card-title class="d-flex align-center ga-2 pt-4">
        <v-icon color="primary">mdi-cube-outline</v-icon>
        Visualize in 3D
      </v-card-title>
      <v-card-subtitle class="pb-2">
        Assign channels to spatial axes, then open the Trace3D renderer.
      </v-card-subtitle>
      <v-card-text class="d-flex flex-column ga-3 pt-2">
        <v-select
          v-model="xIdx"
          :items="channelItems"
          label="X axis"
          density="compact"
          variant="outlined"
          clearable
        />
        <v-select
          v-model="yIdx"
          :items="channelItems"
          label="Y axis"
          density="compact"
          variant="outlined"
          clearable
        />
        <v-select
          v-model="zIdx"
          :items="channelItems"
          label="Z axis"
          density="compact"
          variant="outlined"
          clearable
        />
        <v-select
          v-model="valueIdx"
          :items="channelItems"
          label="Color value (optional)"
          density="compact"
          variant="outlined"
          clearable
        />
        <v-select
          v-model="colormap"
          :items="colormapOptions"
          label="Colormap"
          density="compact"
          variant="outlined"
        />
      </v-card-text>
      <v-card-actions class="pb-4 px-4">
        <v-spacer />
        <v-btn variant="text" @click="open = false">Cancel</v-btn>
        <v-btn
          color="primary"
          variant="tonal"
          :disabled="!canOpen"
          prepend-icon="mdi-cube-outline"
          @click="openTrace3D"
        >
          Open Trace3D
        </v-btn>
      </v-card-actions>
    </v-card>
  </v-dialog>
</template>
