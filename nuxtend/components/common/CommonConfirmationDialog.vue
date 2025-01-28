<script setup lang="ts">
interface ConfirmationDialogProps {
  promptText: string;
  confirmButtonText: string;
}

defineProps<ConfirmationDialogProps>();

const showDialog = defineModel<boolean>("showDialog", {
  required: true,
  default: false,
});

const emit = defineEmits(["confirmed"]);

const confirm = () => {
  showDialog.value = false;
  emit("confirmed");
};
</script>

<template>
  <v-dialog
    v-model="showDialog"
    max-width="475"
    persistent
    @keydown.esc="showDialog = false"
    @keydown.enter="confirm"
  >
    <v-card class="pa-0 bg-canvas">
      <v-card-text>
        <div class="text-h4 text-textbody1">
          {{ promptText }}
        </div>
      </v-card-text>
      <template #actions>
        <v-btn variant="flat" color="treeview" @click="showDialog = false">
          Cancel
        </v-btn>

        <v-btn variant="flat" color="error" @click="confirm">
          {{ confirmButtonText }}
        </v-btn>
      </template>
    </v-card>
  </v-dialog>
</template>
