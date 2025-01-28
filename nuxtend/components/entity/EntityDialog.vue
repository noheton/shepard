<script setup lang="ts">
interface DataObjectEditDialogProps {
  title: string;
  loading?: boolean;
  submitDisabled: boolean;
}

defineProps<DataObjectEditDialogProps>();
const showDialog = defineModel<boolean>("showDialog", {
  required: true,
  default: false,
});
const emit = defineEmits(["submit"]);
</script>

<template>
  <v-dialog v-model="showDialog" persistent max-width="600">
    <v-card :loading="loading">
      <template #title>
        <div class="d-flex justify-space-between align-baseline">
          <div class="text-h4">{{ title }}</div>
          <v-btn variant="plain" icon="mdi-close" @click="showDialog = false" />
        </div>
      </template>
      <template #text>
        <v-container>
          <slot name="form" />
        </v-container>
      </template>
      <template #actions>
        <v-container>
          <v-row justify="end">
            <v-spacer />
            <v-col cols="auto">
              <v-btn variant="flat" @click="showDialog = false">Cancel</v-btn>
              <v-btn
                :disabled="submitDisabled"
                color="primary"
                variant="flat"
                class="ml-4"
                @click="() => emit('submit')"
              >
                Save Changes
              </v-btn>
            </v-col>
          </v-row>
        </v-container>
      </template>
    </v-card>
  </v-dialog>
</template>
