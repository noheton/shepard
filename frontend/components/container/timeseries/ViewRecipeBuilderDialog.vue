<script setup lang="ts">
import type { Timeseries } from "@dlr-shepard/backend-client";
import Trace3DChannelPicker from "./Trace3DChannelPicker.vue";
import type { ChannelV2, Channel5Tuple, Trace3DChannelSelection } from "./Trace3DChannelPicker.vue";

const props = defineProps<{
  containerId: number;
  channels: Timeseries[];
  /** v2 channel list carrying shepardId for auto-populate and annotation save. */
  channelsV2?: ChannelV2[];
  startNs: number;
  endNs: number;
}>();

const open = defineModel<boolean>({ default: false });

const pickerRef = ref<InstanceType<typeof Trace3DChannelPicker> | null>(null);
const canOpen = ref(false);
const openCount = ref(0);

watch(open, (v) => { if (v) openCount.value++; });

// When v2 channels haven't loaded yet (or container predates TS-SEMANTIC-01),
// fall back to synthesising ChannelV2 entries from the v1 channel list so the
// picker always has something to show. shepardId is intentionally empty for
// synthesised entries — saveAnnotations skips them (falsy guard).
const resolvedChannels = computed<ChannelV2[]>(() => {
  if (props.channelsV2?.length) return props.channelsV2;
  return props.channels.map(ch => ({
    shepardId: "",
    measurement: ch.measurement,
    device: ch.device,
    location: ch.location,
    symbolicName: ch.symbolicName,
    field: ch.field,
  }));
});

// ── save annotations (TS-AXIS-AUTO) ──────────────────────────────────────────

async function saveAnnotations() {
  if (!pickerRef.value || !resolvedChannels.value.length) return;
  const sel = pickerRef.value.getSelection();
  const roleEntries: [string, Channel5Tuple | null][] = [
    ["x", sel.x], ["y", sel.y], ["z", sel.z],
    ["rot_a", sel.rot_a], ["rot_b", sel.rot_b], ["rot_c", sel.rot_c],
  ];
  for (const [role, tuple] of roleEntries) {
    if (!tuple) continue;
    const ch = resolvedChannels.value.find(
      c =>
        (c.measurement ?? "") === tuple.measurement &&
        (c.device       ?? "") === tuple.device &&
        (c.field        ?? "") === tuple.field &&
        (c.location     ?? "") === tuple.location &&
        (c.symbolicName ?? "") === tuple.symbolicName,
    );
    if (!ch?.shepardId) continue;
    await $fetch(
      `/v2/timeseries-containers/${props.containerId}/channels/${ch.shepardId}/annotations`,
      { method: "POST", body: { value: role } },
    ).catch(() => {});
  }
}

// ── open Trace3D render page ──────────────────────────────────────────────────

function openTrace3D() {
  if (!pickerRef.value) return;
  const sel: Trace3DChannelSelection = pickerRef.value.getSelection();
  if (!sel.x || !sel.y || !sel.z) return;

  const roles: Record<string, Channel5Tuple> = { x: sel.x, y: sel.y, z: sel.z };
  if (sel.value) roles.value = sel.value;
  if (sel.rot_a) roles.rot_a = sel.rot_a;
  if (sel.rot_b) roles.rot_b = sel.rot_b;
  if (sel.rot_c) roles.rot_c = sel.rot_c;

  navigateTo({
    path: "/shapes/render",
    query: {
      containerId: String(props.containerId),
      startNs:    String(props.startNs),
      endNs:      String(props.endNs),
      colormap:   sel.colormap,
      roles:      btoa(JSON.stringify(roles)),
    },
  });
}
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
      <v-card-text class="pt-2">
        <Trace3DChannelPicker
          :key="openCount"
          ref="pickerRef"
          :container-id="containerId"
          :channels="resolvedChannels"
          @update:can-confirm="canOpen = $event"
          @save-annotations-requested="saveAnnotations"
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
