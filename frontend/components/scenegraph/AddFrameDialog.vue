<script setup lang="ts">
/**
 * SCENEGRAPH-REST-1-UI — Add frame dialog.
 *
 * Required: name + (parent unless first frame).
 * When `existingFrames.length === 0`, the parent picker is hidden and the
 * new frame becomes the scene's root (backend behaviour per
 * `CreateFrameRequestIO` javadoc).
 */
import type {
  CreateFrameRequestIO,
  FrameIO,
  FrameKind,
} from "~/composables/useSceneGraph";

interface AddFrameDialogProps {
  existingFrames: FrameIO[];
}

const props = defineProps<AddFrameDialogProps>();

const showDialog = defineModel<boolean>("showDialog", {
  required: true,
  default: false,
});

const emit = defineEmits<{
  (e: "submit", body: CreateFrameRequestIO): void;
}>();

const KIND_OPTIONS: FrameKind[] = ["FRAME", "JOINT", "TOOL", "BASE", "TCP"];

const name = ref("");
const parentFrameAppId = ref<string | null>(null);
const kind = ref<FrameKind>("FRAME");
const x = ref<number>(0);
const y = ref<number>(0);
const z = ref<number>(0);
const rx = ref<number>(0);
const ry = ref<number>(0);
const rz = ref<number>(0);

const isFirstFrame = computed(() => props.existingFrames.length === 0);

const parentItems = computed(() =>
  props.existingFrames.map((f) => ({
    title: f.name ? `${f.name} (${f.appId.slice(0, 8)})` : f.appId.slice(0, 8),
    value: f.appId,
  })),
);

const canSubmit = computed(() => {
  if (name.value.trim().length === 0) return false;
  if (!isFirstFrame.value && !parentFrameAppId.value) return false;
  return true;
});

function reset(): void {
  name.value = "";
  parentFrameAppId.value = null;
  kind.value = "FRAME";
  x.value = 0;
  y.value = 0;
  z.value = 0;
  rx.value = 0;
  ry.value = 0;
  rz.value = 0;
}

watch(showDialog, (v) => {
  if (v) reset();
  if (!v) reset();
});

// When the first frame is being added, auto-default kind to BASE (sensible root).
watch(isFirstFrame, (v) => {
  if (v) kind.value = "BASE";
});

function onSubmit(): void {
  if (!canSubmit.value) return;
  const body: CreateFrameRequestIO = {
    name: name.value.trim(),
    parentFrameAppId: isFirstFrame.value ? null : parentFrameAppId.value,
    kind: kind.value,
    x: x.value,
    y: y.value,
    z: z.value,
    rx: rx.value,
    ry: ry.value,
    rz: rz.value,
  };
  emit("submit", body);
  showDialog.value = false;
}
</script>

<template>
  <v-dialog
    v-model="showDialog"
    max-width="600"
    persistent
    @keydown.esc="showDialog = false"
  >
    <v-card data-test="add-frame-dialog">
      <v-card-title>
        {{ isFirstFrame ? "Add root frame" : "Add frame" }}
      </v-card-title>
      <v-card-text>
        <v-text-field
          v-model="name"
          label="Name"
          density="compact"
          autofocus
          data-test="add-frame-name"
        />
        <v-select
          v-if="!isFirstFrame"
          v-model="parentFrameAppId"
          label="Parent frame"
          :items="parentItems"
          item-title="title"
          item-value="value"
          density="compact"
          data-test="add-frame-parent"
        />
        <v-select
          v-model="kind"
          label="Kind"
          :items="KIND_OPTIONS"
          density="compact"
          data-test="add-frame-kind"
        />
        <div class="text-subtitle-2 mt-3">Translation (m)</div>
        <div class="d-flex ga-2">
          <v-text-field
            v-model.number="x"
            label="x"
            type="number"
            step="0.001"
            density="compact"
          />
          <v-text-field
            v-model.number="y"
            label="y"
            type="number"
            step="0.001"
            density="compact"
          />
          <v-text-field
            v-model.number="z"
            label="z"
            type="number"
            step="0.001"
            density="compact"
          />
        </div>
        <div class="text-subtitle-2 mt-3">Rotation (rad)</div>
        <div class="d-flex ga-2">
          <v-text-field
            v-model.number="rx"
            label="rx"
            type="number"
            step="0.001"
            density="compact"
          />
          <v-text-field
            v-model.number="ry"
            label="ry"
            type="number"
            step="0.001"
            density="compact"
          />
          <v-text-field
            v-model.number="rz"
            label="rz"
            type="number"
            step="0.001"
            density="compact"
          />
        </div>
      </v-card-text>
      <template #actions>
        <v-spacer />
        <v-btn variant="text" @click="showDialog = false">Cancel</v-btn>
        <v-btn
          color="primary"
          variant="flat"
          :disabled="!canSubmit"
          data-test="add-frame-submit"
          @click="onSubmit"
        >
          Add
        </v-btn>
      </template>
    </v-card>
  </v-dialog>
</template>
