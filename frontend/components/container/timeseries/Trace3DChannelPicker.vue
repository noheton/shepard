<script setup lang="ts">
/**
 * Trace3DChannelPicker — shared axis-assignment panel used by both
 * ViewRecipeBuilderDialog (new trace) and Trace3DEditChannelsDialog (edit).
 *
 * Owns: 7 v-selects (x/y/z/value/rot_a/b/c), colormap picker,
 *       auto-populate from /channels/spatial-roles, and the
 *       "save as annotation" snackbar (TS-AXIS-AUTO principle).
 */
import type { ColormapName } from "~/utils/colormap";

export interface Channel5Tuple {
  measurement: string;
  device: string;
  location: string;
  symbolicName: string;
  field: string;
}

export interface ChannelV2 {
  shepardId: string;
  measurement?: string;
  device?: string;
  location?: string;
  symbolicName?: string;
  field?: string;
}

export interface Trace3DChannelSelection {
  x:      Channel5Tuple | null;
  y:      Channel5Tuple | null;
  z:      Channel5Tuple | null;
  value:  Channel5Tuple | null;
  rot_a:  Channel5Tuple | null;
  rot_b:  Channel5Tuple | null;
  rot_c:  Channel5Tuple | null;
  colormap: ColormapName;
}

const props = defineProps<{
  containerId: number;
  channels: ChannelV2[];
  /** Initial selection — null slots left blank, then auto-populate fills them. */
  initial?: Partial<Trace3DChannelSelection>;
}>();

const emit = defineEmits<{
  "update:canConfirm": [boolean];
  "save-annotations-requested": [];
}>();

const colormapOptions: ColormapName[] = ["inferno", "viridis", "plasma"];

// ── internal key helpers ──────────────────────────────────────────────────────

const norm = (s: string | null | undefined) => (s ?? "").trim();
function tupleKey(ch: { measurement?: string; device?: string; location?: string; symbolicName?: string; field?: string }): string {
  return `${norm(ch.measurement)}|${norm(ch.device)}|${norm(ch.location)}|${norm(ch.symbolicName)}|${norm(ch.field)}`;
}

const keyToChannel = computed<Map<string, ChannelV2>>(() => {
  const m = new Map<string, ChannelV2>();
  for (const ch of props.channels) m.set(tupleKey(ch), ch);
  return m;
});

const channelItems = computed(() =>
  props.channels.map(ch => ({
    title: [ch.device, ch.symbolicName, ch.field].filter(Boolean).join(" · "),
    value: tupleKey(ch),
  })),
);

// ── axis state ────────────────────────────────────────────────────────────────

const xKey     = ref<string | null>(null);
const yKey     = ref<string | null>(null);
const zKey     = ref<string | null>(null);
const valueKey = ref<string | null>(null);
const rotAKey  = ref<string | null>(null);
const rotBKey  = ref<string | null>(null);
const rotCKey  = ref<string | null>(null);
const colormap = ref<ColormapName>("inferno");

const showSaveSnackbar = ref(false);
const isAutoPopulating = ref(false);

// ── public API: expose selection to parent ────────────────────────────────────

function channelFor(key: string | null): Channel5Tuple | null {
  if (!key) return null;
  const ch = keyToChannel.value.get(key);
  if (!ch) return null;
  return {
    measurement:  ch.measurement  ?? "",
    device:       ch.device       ?? "",
    location:     ch.location     ?? "",
    symbolicName: ch.symbolicName ?? "",
    field:        ch.field        ?? "",
  };
}

function getSelection(): Trace3DChannelSelection {
  return {
    x:      channelFor(xKey.value),
    y:      channelFor(yKey.value),
    z:      channelFor(zKey.value),
    value:  channelFor(valueKey.value),
    rot_a:  channelFor(rotAKey.value),
    rot_b:  channelFor(rotBKey.value),
    rot_c:  channelFor(rotCKey.value),
    colormap: colormap.value,
  };
}

defineExpose({ getSelection });

// ── auto-populate from /channels/spatial-roles ────────────────────────────────

type RoleKey = "x" | "y" | "z" | "rot_a" | "rot_b" | "rot_c";

async function autoPopulateFromAnnotations() {
  if (!props.channels.length) return;
  try {
    const data = await $fetch<Record<RoleKey, string | null>>(
      `/v2/timeseries-containers/${props.containerId}/channels/spatial-roles`,
    );
    const roleToRef: Record<RoleKey, typeof xKey> = {
      x: xKey, y: yKey, z: zKey, rot_a: rotAKey, rot_b: rotBKey, rot_c: rotCKey,
    };
    for (const [role, shepardId] of Object.entries(data) as [RoleKey, string | null][]) {
      if (!shepardId) continue;
      const ch = props.channels.find(c => c.shepardId === shepardId);
      if (ch) {
        const r = roleToRef[role];
        // Only fill if currently unset
        if (r && r.value === null) r.value = tupleKey(ch);
      }
    }
  } catch {
    // best-effort
  }
}

// ── initialise ────────────────────────────────────────────────────────────────

async function init() {
  isAutoPopulating.value = true;

  xKey.value = null; yKey.value = null; zKey.value = null;
  valueKey.value = null;
  rotAKey.value = null; rotBKey.value = null; rotCKey.value = null;
  showSaveSnackbar.value = false;

  // Seed from initial prop (e.g. current bindings in edit mode)
  if (props.initial) {
    const toKey = (t: Channel5Tuple | null | undefined) =>
      t ? tupleKey(t) : null;
    xKey.value     = toKey(props.initial.x)     ?? null;
    yKey.value     = toKey(props.initial.y)     ?? null;
    zKey.value     = toKey(props.initial.z)     ?? null;
    valueKey.value = toKey(props.initial.value) ?? null;
    rotAKey.value  = toKey(props.initial.rot_a) ?? null;
    rotBKey.value  = toKey(props.initial.rot_b) ?? null;
    rotCKey.value  = toKey(props.initial.rot_c) ?? null;
    if (props.initial.colormap) colormap.value = props.initial.colormap;
  }

  // Fill still-null slots from annotations
  await autoPopulateFromAnnotations();

  isAutoPopulating.value = false;
}

onMounted(init);
watch(() => props.containerId, init);

// ── axis change → snackbar ────────────────────────────────────────────────────

function onAxisChange() {
  if (isAutoPopulating.value) return;
  showSaveSnackbar.value = true;
}

function dismissSnackbar() {
  showSaveSnackbar.value = false;
}

function requestSaveAnnotations() {
  showSaveSnackbar.value = false;
  emit("save-annotations-requested");
}

// ── canConfirm ────────────────────────────────────────────────────────────────

const canConfirm = computed(
  () => xKey.value !== null && yKey.value !== null && zKey.value !== null,
);

watch(canConfirm, v => emit("update:canConfirm", v), { immediate: true });
</script>

<template>
  <div class="d-flex flex-column ga-3">
    <v-select
      v-model="xKey"
      :items="channelItems"
      label="X axis"
      density="compact"
      variant="outlined"
      clearable
      @update:model-value="onAxisChange"
    />
    <v-select
      v-model="yKey"
      :items="channelItems"
      label="Y axis"
      density="compact"
      variant="outlined"
      clearable
      @update:model-value="onAxisChange"
    />
    <v-select
      v-model="zKey"
      :items="channelItems"
      label="Z axis"
      density="compact"
      variant="outlined"
      clearable
      @update:model-value="onAxisChange"
    />
    <v-select
      v-model="valueKey"
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
      v-model="rotAKey"
      :items="channelItems"
      label="Euler A  (rot around world Z)"
      density="compact"
      variant="outlined"
      clearable
      @update:model-value="onAxisChange"
    />
    <v-select
      v-model="rotBKey"
      :items="channelItems"
      label="Euler B  (rot around world Y)"
      density="compact"
      variant="outlined"
      clearable
      @update:model-value="onAxisChange"
    />
    <v-select
      v-model="rotCKey"
      :items="channelItems"
      label="Euler C  (rot around world X)"
      density="compact"
      variant="outlined"
      clearable
      @update:model-value="onAxisChange"
    />
  </div>

  <!-- TS-AXIS-AUTO: save manual selection as annotation default -->
  <v-snackbar
    v-model="showSaveSnackbar"
    :timeout="8000"
    location="bottom end"
    color="surface-variant"
  >
    Axis assignment changed — save as default for this container?
    <template #actions>
      <v-btn variant="text" size="small" @click="dismissSnackbar">Dismiss</v-btn>
      <v-btn variant="tonal" size="small" color="primary" @click="requestSaveAnnotations">
        Save
      </v-btn>
    </template>
  </v-snackbar>
</template>
