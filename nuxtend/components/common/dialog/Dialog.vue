<script setup lang="ts">
interface DialogProps {
  title: string;
  loading?: boolean;
  submitDisabled: boolean;
}

defineProps<DialogProps>();
const showDialog = defineModel<boolean>("showDialog", {
  required: true,
  default: false,
});
const emit = defineEmits(["submit"]);
</script>

<template>
  <v-dialog v-model="showDialog" persistent max-width="600">
    <v-card :loading="loading" color="canvas">
      <template #title>
        <div class="d-flex justify-space-between align-baseline">
          <div class="text-h4 text-wrap">{{ title }}</div>
          <v-btn
            variant="plain"
            density="compact"
            icon="mdi-close"
            @click="showDialog = false"
          />
        </div>
      </template>
      <template #text>
        <slot name="form" />
      </template>
      <template #actions>
        <v-row justify="end">
          <v-spacer />
          <v-col cols="auto">
            <v-btn variant="flat" color="treeview" @click="showDialog = false">
              Cancel
            </v-btn>
            <v-btn
              :disabled="submitDisabled"
              color="primary"
              variant="flat"
              class="ml-4"
              @click="emit('submit')"
            >
              Save Changes
            </v-btn>
          </v-col>
        </v-row>
      </template>
    </v-card>
  </v-dialog>
</template>

<style lang="scss" scoped></style>
