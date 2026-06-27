<script setup lang="ts">
/**
 * SCENEGRAPH-REST-1-UI — inspector pane for the selected frame.
 *
 * Editable: name, parentFrameAppId, kind, transform (x,y,z,rx,ry,rz). Save
 * dispatches a PATCH via the parent page (optimistic update happens in the
 * page; this component only collects the form state + emits `save`).
 *
 * "Copy as JSON-LD" is a power-user affordance for downstream tools — it
 * serialises the in-form FrameIO using the public JSON-LD context the
 * backend emits on `Accept: application/ld+json` per the
 * `docs/reference/scene-graph.md` contract.
 */
import type { FrameIO, FrameKind } from "~/composables/useSceneGraph";

interface SceneGraphFrameInspectorProps {
  frame: FrameIO;
  /** Sibling frames the user can choose as parent (excluding the frame itself). */
  candidateParents: FrameIO[];
  canWrite?: boolean;
}

const props = withDefaults(defineProps<SceneGraphFrameInspectorProps>(), {
  canWrite: true,
});

const emit = defineEmits<{
  (e: "save", patch: Partial<FrameIO>): void;
  (e: "request-delete", frame: FrameIO): void;
}>();

const KIND_OPTIONS: FrameKind[] = ["FRAME", "JOINT", "TOOL", "BASE", "TCP"];

// Local form state — initialised from the selected frame.
const name = ref<string>(props.frame.name ?? "");
const parentFrameAppId = ref<string | null>(props.frame.parentFrameAppId ?? null);
const kind = ref<FrameKind>((props.frame.kind ?? "FRAME") as FrameKind);
const x = ref<number>(props.frame.x ?? 0);
const y = ref<number>(props.frame.y ?? 0);
const z = ref<number>(props.frame.z ?? 0);
const rx = ref<number>(props.frame.rx ?? 0);
const ry = ref<number>(props.frame.ry ?? 0);
const rz = ref<number>(props.frame.rz ?? 0);

// Reset when the selected frame changes upstream.
watch(
  () => props.frame.appId,
  () => {
    name.value = props.frame.name ?? "";
    parentFrameAppId.value = props.frame.parentFrameAppId ?? null;
    kind.value = (props.frame.kind ?? "FRAME") as FrameKind;
    x.value = props.frame.x ?? 0;
    y.value = props.frame.y ?? 0;
    z.value = props.frame.z ?? 0;
    rx.value = props.frame.rx ?? 0;
    ry.value = props.frame.ry ?? 0;
    rz.value = props.frame.rz ?? 0;
  },
);

const isDirty = computed<boolean>(() => {
  return (
    (name.value ?? "") !== (props.frame.name ?? "") ||
    (parentFrameAppId.value ?? null) !== (props.frame.parentFrameAppId ?? null) ||
    kind.value !== (props.frame.kind ?? "FRAME") ||
    x.value !== (props.frame.x ?? 0) ||
    y.value !== (props.frame.y ?? 0) ||
    z.value !== (props.frame.z ?? 0) ||
    rx.value !== (props.frame.rx ?? 0) ||
    ry.value !== (props.frame.ry ?? 0) ||
    rz.value !== (props.frame.rz ?? 0)
  );
});

const parentItems = computed(() => {
  const arr = props.candidateParents
    .filter((f) => f.appId !== props.frame.appId)
    .map((f) => ({
      title: f.name ? `${f.name} (${f.appId.slice(0, 8)})` : f.appId.slice(0, 8),
      value: f.appId,
    }));
  // empty string → clear parent (make root).
  arr.unshift({ title: "(no parent — make root)", value: "" });
  return arr;
});

function onSave(): void {
  const patch: Partial<FrameIO> = {
    name: name.value,
    parentFrameAppId: parentFrameAppId.value,
    kind: kind.value,
    x: x.value,
    y: y.value,
    z: z.value,
    rx: rx.value,
    ry: ry.value,
    rz: rz.value,
  };
  emit("save", patch);
}

function copyAsJsonLd(): void {
  const doc = {
    "@context": "https://schema.shepard.dlr.de/v2/scene-graph",
    "@type": "CoordinateFrame",
    appId: props.frame.appId,
    name: name.value,
    parentFrameAppId: parentFrameAppId.value,
    kind: kind.value,
    x: x.value,
    y: y.value,
    z: z.value,
    rx: rx.value,
    ry: ry.value,
    rz: rz.value,
  };
  void navigator.clipboard?.writeText(JSON.stringify(doc, null, 2));
}
</script>

<template>
  <v-card
    data-test="frame-inspector"
    class="frame-inspector pa-4"
    variant="outlined"
  >
    <div class="d-flex align-center mb-2">
      <div class="text-h6 flex-grow-1">Frame inspector</div>
      <v-btn
        size="small"
        variant="text"
        title="Copy this frame as JSON-LD"
        data-test="frame-copy-jsonld"
        @click="copyAsJsonLd"
      >
        Copy as JSON-LD
      </v-btn>
    </div>
    <div class="text-caption text-textbody2 mb-2">
      appId:
      <span class="text-monospace">{{ frame.appId }}</span>
    </div>

    <v-text-field
      v-model="name"
      label="Name"
      density="compact"
      :disabled="!canWrite"
      data-test="frame-input-name"
    />

    <v-select
      v-model="parentFrameAppId"
      label="Parent frame"
      :items="parentItems"
      item-title="title"
      item-value="value"
      density="compact"
      :disabled="!canWrite"
      data-test="frame-input-parent"
    />

    <v-select
      v-model="kind"
      label="Kind"
      :items="KIND_OPTIONS"
      density="compact"
      :disabled="!canWrite"
      data-test="frame-input-kind"
    />

    <div class="text-subtitle-2 mt-3">Translation (m)</div>
    <div class="d-flex ga-2">
      <v-text-field
        v-model.number="x"
        label="x"
        type="number"
        step="0.001"
        density="compact"
        :disabled="!canWrite"
        data-test="frame-input-x"
      />
      <v-text-field
        v-model.number="y"
        label="y"
        type="number"
        step="0.001"
        density="compact"
        :disabled="!canWrite"
        data-test="frame-input-y"
      />
      <v-text-field
        v-model.number="z"
        label="z"
        type="number"
        step="0.001"
        density="compact"
        :disabled="!canWrite"
        data-test="frame-input-z"
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
        :disabled="!canWrite"
        data-test="frame-input-rx"
      />
      <v-text-field
        v-model.number="ry"
        label="ry"
        type="number"
        step="0.001"
        density="compact"
        :disabled="!canWrite"
        data-test="frame-input-ry"
      />
      <v-text-field
        v-model.number="rz"
        label="rz"
        type="number"
        step="0.001"
        density="compact"
        :disabled="!canWrite"
        data-test="frame-input-rz"
      />
    </div>

    <div class="d-flex ga-2 mt-4">
      <v-btn
        color="primary"
        :disabled="!canWrite || !isDirty"
        data-test="frame-save"
        @click="onSave"
      >
        Save
      </v-btn>
      <v-spacer />
      <v-btn
        color="error"
        variant="outlined"
        :disabled="!canWrite"
        data-test="frame-delete"
        @click="emit('request-delete', frame)"
      >
        Delete subtree
      </v-btn>
    </div>
  </v-card>
</template>

<style scoped>
.text-monospace {
  font-family: var(--v-font-family-mono, monospace);
}
</style>
