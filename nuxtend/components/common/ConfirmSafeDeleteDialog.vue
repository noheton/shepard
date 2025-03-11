<script setup lang="ts">
interface ConfirmSafeDeleteDialog {
  title: string;
  promptText: string;
  targetName: string;
}

const props = defineProps<ConfirmSafeDeleteDialog>();

const showDialog = defineModel<boolean>("showDialog", {
  required: true,
  default: false,
});

const emit = defineEmits(["confirmed"]);

const inputName = ref("");

function namesMatch(): boolean {
  return inputName.value.trim() === props.targetName.trim();
}

onMounted(() => {
  document.getElementById("delete-confirm-input")?.focus();
});
</script>

<template>
  <v-dialog
    v-model="showDialog"
    max-width="475"
    persistent
    @keydown.esc="showDialog = false"
  >
    <v-card class="pa-0 bg-canvas">
      <template #title>
        <div class="d-flex justify-space-between align-baseline">
          <div class="text-h4 text-wrap">
            {{ title }}
          </div>
          <v-btn
            variant="plain"
            density="compact"
            icon="mdi-close"
            @click="showDialog = false"
          />
        </div>
      </template>
      <v-card-text>
        <v-row class="pt-5">
          <v-col>
            <span class="text-body-1 text-textbody1">{{ promptText }}</span>
          </v-col>
        </v-row>
        <v-row>
          <v-col>
            <v-text-field
              id="delete-confirm-input"
              v-model:model-value="inputName"
              label="Enter collection name"
              variant="outlined"
              density="compact"
              require
              color="primary"
              hide-details
              autocomplete="off"
              @keypress="namesMatch"
            />
          </v-col>
        </v-row>
      </v-card-text>
      <template #actions>
        <v-btn variant="flat" color="treeview" @click="showDialog = false">
          Cancel
        </v-btn>
        <v-btn
          variant="flat"
          color="error"
          :disabled="!namesMatch()"
          @click="emit('confirmed')"
        >
          Delete
        </v-btn>
      </template>
    </v-card>
  </v-dialog>
</template>
