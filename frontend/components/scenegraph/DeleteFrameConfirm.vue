<script setup lang="ts">
/**
 * SCENEGRAPH-REST-1-UI — delete-frame confirm dialog.
 *
 * Surfaces the subtree size so the user understands the blast radius
 * (`DELETE /v2/scene-graphs/{appId}/frames/{frameAppId}` cascades via
 * `:HAS_PARENT_FRAME*` per the docs). Wraps the existing
 * `ConfirmDeleteDialog` shape rather than reinventing it.
 */
import type { FrameIO } from "~/composables/useSceneGraph";

interface DeleteFrameConfirmProps {
  frame: FrameIO | null;
  descendantCount: number;
}

const props = defineProps<DeleteFrameConfirmProps>();

const showDialog = defineModel<boolean>("showDialog", {
  required: true,
  default: false,
});

const emit = defineEmits<{
  (e: "confirmed"): void;
}>();

const label = computed(() => {
  if (!props.frame) return "frame";
  return props.frame.name ? `"${props.frame.name}"` : props.frame.appId.slice(0, 8);
});

const message = computed(() => {
  if (!props.frame) return "Delete this frame?";
  const total = props.descendantCount + 1;
  if (props.descendantCount === 0) {
    return `Delete frame ${label.value}? This cannot be undone.`;
  }
  return `Delete frame ${label.value} and its ${props.descendantCount} descendant(s)? This will remove ${total} frame(s) and cannot be undone.`;
});

function confirm(): void {
  showDialog.value = false;
  emit("confirmed");
}
</script>

<template>
  <v-dialog
    v-model="showDialog"
    max-width="500"
    persistent
    @keydown.esc="showDialog = false"
    @keydown.enter="confirm"
  >
    <v-card data-test="delete-frame-confirm">
      <v-card-text>
        <div class="text-h6">{{ message }}</div>
      </v-card-text>
      <template #actions>
        <v-btn variant="flat" color="treeview" @click="showDialog = false">
          Cancel
        </v-btn>
        <v-btn
          variant="flat"
          color="error"
          data-test="delete-frame-confirm-button"
          @click="confirm"
        >
          Delete
        </v-btn>
      </template>
    </v-card>
  </v-dialog>
</template>
