<script setup lang="ts">
/**
 * SCENEGRAPH-REST-1-UI — Add joint dialog.
 *
 * Required: parent + child (per `CreateJointRequestIO` javadoc — a joint
 * without endpoints is meaningless).
 */
import type {
  CreateJointRequestIO,
  FrameIO,
  JointType,
} from "~/composables/useSceneGraph";

interface AddJointDialogProps {
  existingFrames: FrameIO[];
}

const props = defineProps<AddJointDialogProps>();

const showDialog = defineModel<boolean>("showDialog", {
  required: true,
  default: false,
});

const emit = defineEmits<{
  (e: "submit", body: CreateJointRequestIO): void;
}>();

const TYPE_OPTIONS: JointType[] = [
  "REVOLUTE",
  "PRISMATIC",
  "FIXED",
  "CONTINUOUS",
];

const name = ref("");
const parentFrameAppId = ref<string | null>(null);
const childFrameAppId = ref<string | null>(null);
const axisX = ref<number>(0);
const axisY = ref<number>(0);
const axisZ = ref<number>(1);
const limitMin = ref<number>(0);
const limitMax = ref<number>(0);
const type = ref<JointType>("FIXED");
const homeAngle = ref<number>(0);

const frameItems = computed(() =>
  props.existingFrames.map((f) => ({
    title: f.name ? `${f.name} (${f.appId.slice(0, 8)})` : f.appId.slice(0, 8),
    value: f.appId,
  })),
);

const canSubmit = computed<boolean>(() => {
  if (!parentFrameAppId.value || !childFrameAppId.value) return false;
  if (parentFrameAppId.value === childFrameAppId.value) return false;
  return true;
});

function reset(): void {
  name.value = "";
  parentFrameAppId.value = null;
  childFrameAppId.value = null;
  axisX.value = 0;
  axisY.value = 0;
  axisZ.value = 1;
  limitMin.value = 0;
  limitMax.value = 0;
  type.value = "FIXED";
  homeAngle.value = 0;
}

watch(showDialog, (v) => {
  if (v) reset();
  if (!v) reset();
});

function onSubmit(): void {
  if (!canSubmit.value) return;
  const body: CreateJointRequestIO = {
    name: name.value.trim() || null,
    parentFrameAppId: parentFrameAppId.value!,
    childFrameAppId: childFrameAppId.value!,
    axisX: axisX.value,
    axisY: axisY.value,
    axisZ: axisZ.value,
    limitMin: limitMin.value,
    limitMax: limitMax.value,
    type: type.value,
    homeAngle: homeAngle.value,
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
    <v-card data-test="add-joint-dialog">
      <v-card-title>Add joint</v-card-title>
      <v-card-text>
        <v-text-field
          v-model="name"
          label="Name (optional)"
          density="compact"
          data-test="add-joint-name"
        />
        <v-select
          v-model="parentFrameAppId"
          label="Parent frame *"
          :items="frameItems"
          item-title="title"
          item-value="value"
          density="compact"
          data-test="add-joint-parent"
        />
        <v-select
          v-model="childFrameAppId"
          label="Child frame *"
          :items="frameItems"
          item-title="title"
          item-value="value"
          density="compact"
          data-test="add-joint-child"
        />
        <v-select
          v-model="type"
          label="Type"
          :items="TYPE_OPTIONS"
          density="compact"
          data-test="add-joint-type"
        />
        <div class="text-subtitle-2 mt-3">Axis (unit vector)</div>
        <div class="d-flex ga-2">
          <v-text-field
            v-model.number="axisX"
            label="x"
            type="number"
            step="0.001"
            density="compact"
          />
          <v-text-field
            v-model.number="axisY"
            label="y"
            type="number"
            step="0.001"
            density="compact"
          />
          <v-text-field
            v-model.number="axisZ"
            label="z"
            type="number"
            step="0.001"
            density="compact"
          />
        </div>
        <div class="text-subtitle-2 mt-3">Limits</div>
        <div class="d-flex ga-2">
          <v-text-field
            v-model.number="limitMin"
            label="min"
            type="number"
            step="0.001"
            density="compact"
          />
          <v-text-field
            v-model.number="limitMax"
            label="max"
            type="number"
            step="0.001"
            density="compact"
          />
          <v-text-field
            v-model.number="homeAngle"
            label="home"
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
          data-test="add-joint-submit"
          @click="onSubmit"
        >
          Add
        </v-btn>
      </template>
    </v-card>
  </v-dialog>
</template>
