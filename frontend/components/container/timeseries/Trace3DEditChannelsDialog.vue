<script setup lang="ts">
/**
 * Trace3DEditChannelsDialog — edit axis assignments on an already-rendered Trace3D view.
 *
 * Wraps Trace3DChannelPicker and emits "save" with the new Trace3DChannelSelection
 * when the user confirms. Handles annotation persistence on "save" snackbar click.
 */
import Trace3DChannelPicker from "./Trace3DChannelPicker.vue";
import type { ChannelV2, Trace3DChannelSelection, Channel5Tuple } from "./Trace3DChannelPicker.vue";

const props = defineProps<{
  containerId: number;
  containerAppId: string;
  channels: ChannelV2[];
  initial: Partial<Trace3DChannelSelection>;
}>();

const emit = defineEmits<{
  save: [Trace3DChannelSelection];
}>();

const open = defineModel<boolean>({ default: false });
const pickerRef = ref<InstanceType<typeof Trace3DChannelPicker> | null>(null);
const canConfirm = ref(false);
const openCount = ref(0);

watch(open, (v) => { if (v) openCount.value++; });

// ── annotation save (TS-AXIS-AUTO snackbar "Save" action) ─────────────────────

async function saveAnnotations() {
  if (!pickerRef.value || !props.channels.length) return;
  const sel = pickerRef.value.getSelection();
  const roleEntries: [string, Channel5Tuple | null][] = [
    ["x", sel.x], ["y", sel.y], ["z", sel.z],
    ["rot_a", sel.rot_a], ["rot_b", sel.rot_b], ["rot_c", sel.rot_c],
  ];
  for (const [role, tuple] of roleEntries) {
    if (!tuple) continue;
    const ch = props.channels.find(
      c =>
        (c.measurement ?? "") === tuple.measurement &&
        (c.device       ?? "") === tuple.device &&
        (c.field        ?? "") === tuple.field &&
        (c.location     ?? "") === tuple.location &&
        (c.symbolicName ?? "") === tuple.symbolicName,
    );
    if (!ch?.shepardId) continue;
    await $fetch(
      `/v2/containers/${props.containerAppId}/channels/${ch.shepardId}/annotations`,
      { method: "POST", body: { value: role } },
    ).catch(() => {});
  }
}

function onApply() {
  if (!pickerRef.value) return;
  emit("save", pickerRef.value.getSelection());
  open.value = false;
}
</script>

<template>
  <v-dialog v-model="open" max-width="480">
    <v-card>
      <v-card-title class="d-flex align-center ga-2 pt-4">
        <v-icon color="primary">mdi-pencil-outline</v-icon>
        Edit channel assignments
      </v-card-title>
      <v-card-subtitle class="pb-2">
        Reassign channels to spatial axes for this Trace3D view.
      </v-card-subtitle>
      <v-card-text class="pt-2">
        <Trace3DChannelPicker
          :key="openCount"
          ref="pickerRef"
          :container-id="containerId"
          :container-app-id="containerAppId"
          :channels="channels"
          :initial="initial"
          @update:can-confirm="canConfirm = $event"
          @save-annotations-requested="saveAnnotations"
        />
      </v-card-text>
      <v-card-actions class="pb-4 px-4">
        <v-spacer />
        <v-btn variant="text" @click="open = false">Cancel</v-btn>
        <v-btn
          color="primary"
          variant="tonal"
          :disabled="!canConfirm"
          prepend-icon="mdi-check"
          @click="onApply"
        >
          Apply
        </v-btn>
      </v-card-actions>
    </v-card>
  </v-dialog>
</template>
