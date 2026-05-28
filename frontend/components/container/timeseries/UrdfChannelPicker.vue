<script setup lang="ts">
/**
 * UrdfChannelPicker — bind one channel per movable URDF joint.
 *
 * Sibling of {@link ./Trace3DChannelPicker.vue}, but instead of mapping
 * channels to spatial axes (x/y/z + rotation), this maps channels to URDF
 * joint names. Annotation-driven preselection per
 * `project_annotation_preselection_principle.md`:
 *
 *   - A channel annotated with `urn:shepard:urdf:joint = <jointName>` (predicate
 *     {@link URDF_JOINT_PREDICATE}) auto-binds to that joint.
 *   - Heuristic fallback: a channel whose symbolicName or field equals the
 *     joint name (case-insensitive) auto-binds.
 *
 * Task: URDF-WEBVIEW-1 (aidocs/16).
 */
import { ref, computed, watch, onMounted } from "vue";
import {
  type UrdfPickerChannel,
  type UrdfPickerJoint,
  type UrdfJointBinding,
  URDF_JOINT_PREDICATE,
  initialBinding,
  isBindingReady,
} from "~/utils/urdfChannelPicker";

const props = defineProps<{
  containerId: number;
  channels: UrdfPickerChannel[];
  /** Movable joints declared by the loaded URDFRobot. */
  joints: UrdfPickerJoint[];
  /** Initial binding — typically from a prior save; merged with auto-populate. */
  initial?: UrdfJointBinding;
}>();

const emit = defineEmits<{
  "update:canConfirm": [boolean];
  "save-annotations-requested": [];
}>();

// jointName → channel shepardId (or null)
const binding = ref<UrdfJointBinding>({});
const showSaveSnackbar = ref(false);
const isAutoPopulating = ref(false);

const movableJoints = computed(() =>
  props.joints.filter(j => j.jointType !== "fixed"),
);

const channelItems = computed(() =>
  props.channels.map(ch => ({
    title: [ch.device, ch.symbolicName, ch.field].filter(Boolean).join(" · ") || ch.shepardId,
    value: ch.shepardId,
  })),
);

// ── public API ────────────────────────────────────────────────────────────────

function getBinding(): UrdfJointBinding {
  return { ...binding.value };
}

defineExpose({ getBinding });

// ── init / preselect ──────────────────────────────────────────────────────────

async function init() {
  isAutoPopulating.value = true;
  binding.value = initialBinding(movableJoints.value, props.channels);

  if (props.initial) {
    for (const [name, id] of Object.entries(props.initial)) {
      if (id) binding.value[name] = id;
    }
  }

  showSaveSnackbar.value = false;
  isAutoPopulating.value = false;
}

onMounted(init);
watch(() => props.joints, init);
watch(() => props.containerId, init);

// ── user edits ────────────────────────────────────────────────────────────────

function onBindingChange(name: string, val: string | null) {
  binding.value = { ...binding.value, [name]: val };
  if (!isAutoPopulating.value) showSaveSnackbar.value = true;
}

function dismissSnackbar() {
  showSaveSnackbar.value = false;
}

function requestSaveAnnotations() {
  showSaveSnackbar.value = false;
  emit("save-annotations-requested");
}

// ── canConfirm ────────────────────────────────────────────────────────────────

const canConfirm = computed(() => isBindingReady(binding.value));
watch(canConfirm, v => emit("update:canConfirm", v), { immediate: true });
</script>

<template>
  <div class="d-flex flex-column ga-3">
    <div class="text-caption text-medium-emphasis">
      Assign one timeseries channel per movable joint. Channels annotated with
      <code>{{ URDF_JOINT_PREDICATE }} = jointName</code> are preselected
      automatically.
    </div>

    <v-alert v-if="movableJoints.length === 0" type="info" variant="tonal" density="compact">
      No movable joints in the URDF — load a robot description first.
    </v-alert>

    <div
      v-for="joint in movableJoints"
      :key="joint.name"
      class="d-flex align-center ga-2"
    >
      <code class="text-caption" style="min-width:140px">{{ joint.name }}</code>
      <v-chip size="x-small" variant="tonal">{{ joint.jointType }}</v-chip>
      <v-select
        :model-value="binding[joint.name] ?? null"
        :items="channelItems"
        :label="`Channel for ${joint.name}`"
        density="compact"
        variant="outlined"
        hide-details
        clearable
        class="flex-grow-1"
        @update:model-value="(v: string | null) => onBindingChange(joint.name, v)"
      />
    </div>
  </div>

  <!-- Save-as-annotation prompt (mirrors Trace3DChannelPicker) -->
  <v-snackbar
    v-model="showSaveSnackbar"
    :timeout="8000"
    location="bottom end"
    color="surface-variant"
  >
    Joint assignment changed — save as default for this container?
    <template #actions>
      <v-btn variant="text" size="small" @click="dismissSnackbar">Dismiss</v-btn>
      <v-btn variant="tonal" size="small" color="primary" @click="requestSaveAnnotations">
        Save
      </v-btn>
    </template>
  </v-snackbar>
</template>
