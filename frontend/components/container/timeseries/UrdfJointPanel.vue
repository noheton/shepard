<script setup lang="ts">
/**
 * UrdfJointPanel — manual Vuetify sliders for each movable URDF joint.
 *
 * Used in static-view mode (no TS animation active). The parent passes the
 * jointSpecs derived from the loaded URDFRobot (typically inside an
 * `@robot-loaded` handler on UrdfCanvas/UrdfView) and receives
 * `update:jointValues` events back. Drives the URDF directly via the same
 * jointValues prop UrdfAnimator uses.
 *
 * Task: URDF-WEBVIEW-1 (aidocs/16).
 */
import { ref, watch } from "vue";

export interface UrdfJointSpec {
  /** Joint name as declared in the URDF. */
  name: string;
  /** "revolute" (radians), "prismatic" (metres), "continuous" (radians, no limit). */
  jointType: string;
  /** Lower limit (radians or metres). Null for continuous joints. */
  lower: number | null;
  /** Upper limit. Null for continuous joints. */
  upper: number | null;
}

const props = withDefaults(
  defineProps<{
    /** Joints to expose. Derived from URDFRobot.joints (caller-side). */
    joints: UrdfJointSpec[];
    /** Initial / current values; the slider state syncs to this when provided. */
    modelValue?: Record<string, number>;
  }>(),
  {
    modelValue: () => ({}),
  },
);

const emit = defineEmits<{
  "update:modelValue": [values: Record<string, number>];
}>();

// Local mirror of the joint values — sliders bind here, then we emit upward.
const local = ref<Record<string, number>>({ ...props.modelValue });

watch(
  () => props.modelValue,
  (v) => { local.value = { ...local.value, ...v }; },
  { deep: true },
);

watch(
  () => props.joints,
  (specs) => {
    // Seed any missing keys at zero (or at the lower limit if positive).
    const next: Record<string, number> = { ...local.value };
    for (const s of specs) {
      if (next[s.name] !== undefined) continue;
      if (s.lower !== null && s.lower > 0) next[s.name] = s.lower;
      else                                  next[s.name] = 0;
    }
    local.value = next;
  },
  { immediate: true, deep: false },
);

function onSliderChange(name: string, val: number) {
  local.value = { ...local.value, [name]: val };
  emit("update:modelValue", local.value);
}

function resetAll() {
  const next: Record<string, number> = {};
  for (const s of props.joints) {
    if (s.lower !== null && s.lower > 0) next[s.name] = s.lower;
    else                                  next[s.name] = 0;
  }
  local.value = next;
  emit("update:modelValue", local.value);
}

function fmt(v: number | undefined): string {
  if (v === undefined || !isFinite(v)) return "0.00";
  return v.toFixed(3);
}

function sliderMin(s: UrdfJointSpec): number {
  if (s.lower !== null) return s.lower;
  return s.jointType === "prismatic" ? -1 : -Math.PI;
}
function sliderMax(s: UrdfJointSpec): number {
  if (s.upper !== null) return s.upper;
  return s.jointType === "prismatic" ?  1 :  Math.PI;
}
function sliderStep(s: UrdfJointSpec): number {
  const range = sliderMax(s) - sliderMin(s);
  return Math.max(range / 1000, 1e-4);
}
</script>

<template>
  <v-card variant="outlined">
    <v-card-title class="text-subtitle-2 d-flex align-center ga-2 pt-3 px-4 pb-1">
      <v-icon size="small" color="primary">mdi-tune-variant</v-icon>
      Joint controls
      <v-spacer />
      <v-btn size="x-small" variant="tonal" prepend-icon="mdi-restore" @click="resetAll">
        Reset
      </v-btn>
    </v-card-title>
    <v-card-text class="py-2 px-4">
      <v-alert v-if="joints.length === 0" type="info" variant="tonal" density="compact">
        No movable joints declared in the URDF.
      </v-alert>
      <div
        v-for="spec in joints"
        :key="spec.name"
        class="d-flex align-center ga-3 mb-2"
      >
        <code class="text-caption" style="min-width:140px">{{ spec.name }}</code>
        <v-chip size="x-small" variant="tonal">{{ spec.jointType }}</v-chip>
        <v-slider
          :model-value="local[spec.name] ?? 0"
          :min="sliderMin(spec)"
          :max="sliderMax(spec)"
          :step="sliderStep(spec)"
          color="primary"
          density="compact"
          hide-details
          class="flex-grow-1"
          @update:model-value="(v: number) => onSliderChange(spec.name, v)"
        />
        <span class="text-caption text-medium-emphasis" style="min-width:64px; text-align:right">
          {{ fmt(local[spec.name]) }}
        </span>
      </div>
    </v-card-text>
  </v-card>
</template>
