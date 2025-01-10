<script setup lang="ts">
interface ConfirmationDialogProps {
  promptText: string;
  confirmButtonText: string;
}

defineProps<ConfirmationDialogProps>();
const emit = defineEmits(["confirmed"]);
const dialog = ref<boolean>(false);

const confirm = () => {
  dialog.value = false;
  emit("confirmed");
};
</script>
<template>
  <v-dialog
    v-model="dialog"
    max-width="475"
    persistent
    @keydown.esc="dialog = false"
    @keydown.enter="confirm"
  >
    <template #activator="{ props: activatorProps }">
      <span v-bind="activatorProps">
        <slot />
      </span>
    </template>

    <v-card class="pa-0 bg-canvas">
      <v-card-text>
        <div class="text-h4 text-textbody1">
          {{ promptText }}
        </div>
      </v-card-text>
      <template #actions>
        <v-btn variant="flat" color="treeview" @click="dialog = false">
          Cancel
        </v-btn>

        <v-btn variant="flat" color="error" @click="confirm">
          {{ confirmButtonText }}
        </v-btn>
      </template>
    </v-card>
  </v-dialog>
</template>
