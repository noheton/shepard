<script setup lang="ts">
import type { Timeseries } from "@dlr-shepard/backend-client";
import type { ColormapName } from "~/utils/colormap";

interface ChannelV2 {
  shepardId: string;
  measurement?: string;
  device?: string;
  field?: string;
  location?: string;
  symbolicName?: string;
}

const props = defineProps<{
  containerId: number;
  channels: Timeseries[];
  /** Optional: v2 channel list carrying shepardId for auto-populate. */
  channelsV2?: ChannelV2[];
  startNs: number;
  endNs: number;
}>();

const open = defineModel<boolean>({ default: false });

const colormapOptions: ColormapName[] = ["inferno", "viridis", "plasma"];

const xIdx      = ref<number | null>(null);
const yIdx      = ref<number | null>(null);
const zIdx      = ref<number | null>(null);
const valueIdx  = ref<number | null>(null);
const eulerAIdx = ref<number | null>(null);
const eulerBIdx = ref<number | null>(null);
const eulerCIdx = ref<number | null>(null);
const colormap  = ref<ColormapName>("inferno");

/** Tracks which axis indices were auto-populated (not manually set by the user). */
const autoSelectedAxes = ref<Set<string>>(new Set());

/** If true, show the "save role annotations" snackbar. */
const showSaveSnackbar = ref(false);

const channelItems = computed(() =>
  props.channels.map((ch, i) => ({
    title: [ch.device, ch.field, ch.measurement].filter(Boolean).join(" · "),
    value: i,
  })),
);

/**
 * Look up the index in `channels` that corresponds to a shepardId.
 * Returns null if no match.
 */
function indexByShepardId(shepardId: string | null | undefined): number | null {
  if (!shepardId || !props.channelsV2) return null;
  const v2idx = props.channelsV2.findIndex((c) => c.shepardId === shepardId);
  if (v2idx < 0) return null;
  // channelsV2 and channels are parallel arrays (same order)
  return v2idx < props.channels.length ? v2idx : null;
}

type RoleKey = "x" | "y" | "z" | "rot_a" | "rot_b" | "rot_c";

interface SpatialRolesResponse {
  x?: string | null;
  y?: string | null;
  z?: string | null;
  rot_a?: string | null;
  rot_b?: string | null;
  rot_c?: string | null;
}

async function autoPopulateFromAnnotations() {
  if (!props.channelsV2?.length) return;

  try {
    const data = await $fetch<SpatialRolesResponse>(
      `/v2/timeseries-containers/${props.containerId}/channels/spatial-roles`,
    );

    const roleToRef: Record<RoleKey, ReturnType<typeof ref<number | null>>> = {
      x: xIdx, y: yIdx, z: zIdx, rot_a: eulerAIdx, rot_b: eulerBIdx, rot_c: eulerCIdx,
    };

    autoSelectedAxes.value = new Set();
    for (const [role, refObj] of Object.entries(roleToRef) as [RoleKey, typeof xIdx][]) {
      const shepardId = data[role];
      const idx = indexByShepardId(shepardId);
      if (idx !== null) {
        refObj.value = idx;
        autoSelectedAxes.value.add(role);
      }
    }
  } catch {
    // Auto-populate is best-effort; silently ignore errors (e.g. 404 on new container)
  }
}

function onAxisChange(role: string) {
  // If the user manually changes an axis that was auto-populated, mark it as manual
  autoSelectedAxes.value.delete(role);
  // Show snackbar prompting to save as annotation
  showSaveSnackbar.value = true;
}

async function saveRoleAnnotations() {
  if (!props.channelsV2) return;
  showSaveSnackbar.value = false;

  const roleToIdx: [RoleKey, typeof xIdx][] = [
    ["x", xIdx], ["y", yIdx], ["z", zIdx],
    ["rot_a", eulerAIdx], ["rot_b", eulerBIdx], ["rot_c", eulerCIdx],
  ];

  for (const [role, refObj] of roleToIdx) {
    const idx = refObj.value;
    if (idx === null) continue;
    const channel = props.channelsV2[idx];
    if (!channel?.shepardId) continue;

    await $fetch(
      `/v2/timeseries-containers/${props.containerId}/channels/${channel.shepardId}/annotations`,
      { method: "POST", body: { value: role } },
    ).catch(() => {
      // Best-effort; ignore errors per individual annotation
    });
  }
}

function openTrace3D() {
  const pick = (i: number | null): Timeseries | null =>
    i !== null ? (props.channels[i] ?? null) : null;
  const x  = pick(xIdx.value);
  const y  = pick(yIdx.value);
  const z  = pick(zIdx.value);
  const v  = pick(valueIdx.value);
  const eA = pick(eulerAIdx.value);
  const eB = pick(eulerBIdx.value);
  const eC = pick(eulerCIdx.value);
  if (!x || !y || !z) return;

  const roles = {
    x, y, z,
    ...(v  ? { value: v  } : {}),
    ...(eA ? { rot_a: eA } : {}),
    ...(eB ? { rot_b: eB } : {}),
    ...(eC ? { rot_c: eC } : {}),
  };
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

watch(open, async (v) => {
  if (v) {
    xIdx.value = null; yIdx.value = null; zIdx.value = null;
    valueIdx.value = null;
    eulerAIdx.value = null; eulerBIdx.value = null; eulerCIdx.value = null;
    colormap.value = "inferno";
    autoSelectedAxes.value = new Set();
    showSaveSnackbar.value = false;
    // Auto-populate from annotations (best-effort)
    await autoPopulateFromAnnotations();
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
          @update:model-value="onAxisChange('x')"
        />
        <v-select
          v-model="yIdx"
          :items="channelItems"
          label="Y axis"
          density="compact"
          variant="outlined"
          clearable
          @update:model-value="onAxisChange('y')"
        />
        <v-select
          v-model="zIdx"
          :items="channelItems"
          label="Z axis"
          density="compact"
          variant="outlined"
          clearable
          @update:model-value="onAxisChange('z')"
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
        <v-divider class="my-1" />
        <div class="text-caption text-medium-emphasis">
          Tool orientation — KUKA Euler A/B/C (optional)
        </div>
        <v-select
          v-model="eulerAIdx"
          :items="channelItems"
          label="Euler A  (rot around world Z)"
          density="compact"
          variant="outlined"
          clearable
          @update:model-value="onAxisChange('rot_a')"
        />
        <v-select
          v-model="eulerBIdx"
          :items="channelItems"
          label="Euler B  (rot around world Y)"
          density="compact"
          variant="outlined"
          clearable
          @update:model-value="onAxisChange('rot_b')"
        />
        <v-select
          v-model="eulerCIdx"
          :items="channelItems"
          label="Euler C  (rot around world X)"
          density="compact"
          variant="outlined"
          clearable
          @update:model-value="onAxisChange('rot_c')"
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

  <!-- TS-AXIS-AUTO: prompt user to persist manual axis selections as annotations -->
  <v-snackbar
    v-model="showSaveSnackbar"
    :timeout="8000"
    location="bottom end"
    color="surface-variant"
  >
    Axis assignment changed — save as default for this container?
    <template #actions>
      <v-btn variant="text" size="small" @click="showSaveSnackbar = false">Dismiss</v-btn>
      <v-btn variant="tonal" size="small" color="primary" @click="saveRoleAnnotations">
        Save
      </v-btn>
    </template>
  </v-snackbar>
</template>
