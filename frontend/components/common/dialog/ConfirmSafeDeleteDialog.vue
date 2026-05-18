<script lang="ts" setup>
interface ConfirmSafeDeleteDialog {
  entityType: string;
  targetName: string;
  /**
   * When set, renders a warning banner above the type-the-name prompt.
   * Used e.g. for containers that have active references — the delete still
   * proceeds, but the user is informed they'll be leaving orphaned references.
   */
  warning?: string;
}

const props = defineProps<ConfirmSafeDeleteDialog>();

const title = `Are you sure you want to delete this ${props.entityType}?`;
const promptText = `Deleting this ${props.entityType} is permanent. To confirm that you want to proceed, please enter the ${props.entityType} name:`;
const label = `Enter ${props.entityType} name`;

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
            density="compact"
            icon="mdi-close"
            variant="plain"
            @click="showDialog = false"
          />
        </div>
      </template>
      <v-card-text>
        <v-alert
          v-if="warning"
          type="warning"
          variant="tonal"
          density="compact"
          class="mt-3"
        >
          {{ warning }}
        </v-alert>
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
              :label="label"
              autocomplete="off"
              color="primary"
              density="compact"
              hide-details
              require
              variant="outlined"
              @keypress="namesMatch"
            />
          </v-col>
        </v-row>
      </v-card-text>
      <template #actions>
        <v-btn color="treeview" variant="flat" @click="showDialog = false">
          Cancel
        </v-btn>
        <v-btn
          :disabled="!namesMatch()"
          color="error"
          variant="flat"
          @click="
            () => {
              emit('confirmed');
              showDialog = false;
            }
          "
        >
          Delete
        </v-btn>
      </template>
    </v-card>
  </v-dialog>
</template>
