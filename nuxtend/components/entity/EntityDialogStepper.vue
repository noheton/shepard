<script setup lang="ts">
interface EntityDialogStepperProps {
  title: string;
  loading?: boolean;
  submitDisabled: boolean;
}

defineProps<EntityDialogStepperProps>();
const showDialog = defineModel<boolean>("showDialog", {
  required: true,
  default: false,
});
const emit = defineEmits(["submit"]);

const currentStep = ref<number>(1);
</script>

<template>
  <v-dialog v-model="showDialog" persistent max-width="600">
    <v-card>
      <template #title>
        <div class="d-flex justify-space-between align-baseline">
          <div class="text-h4">{{ title }}</div>
          <v-btn variant="plain" icon="mdi-close" @click="showDialog = false" />
        </div>
      </template>
      <template #text>
        <!-- TODO: Make grey stepper background on top full width -->
        <v-stepper
          v-model:model-value="currentStep"
          class="d-flex flex-column"
          min-height="426px"
          :items="['Collection Properties', 'Additional Information']"
          selected-class="selected-step"
          flat
        >
          <template #[`item.1`]>
            <div class="text-subtitle-2 pt-6">Collection Properties</div>
            <slot name="step1-form" />
          </template>

          <template #[`item.2`]>
            <div class="text-subtitle-2 pt-6 pb-3">Additional Information</div>
            <slot name="step2-form" />
          </template>
          <v-spacer />
          <template #actions>
            <div class="d-flex px-6">
              <v-btn
                v-if="currentStep === 2"
                :disabled="submitDisabled"
                variant="flat"
                color="treeview"
                @click="currentStep = 1"
              >
                <!-- TODO: Better disabled color -->
                Back
              </v-btn>
              <v-spacer />
              <v-btn
                variant="flat"
                color="treeview"
                class="mr-4"
                @click="showDialog = false"
              >
                Cancel
              </v-btn>
              <v-btn
                v-if="currentStep === 1"
                :disabled="submitDisabled"
                variant="flat"
                color="primary"
                @click="currentStep = 2"
              >
                Next
              </v-btn>
              <v-btn
                v-else
                :disabled="submitDisabled"
                color="primary"
                variant="flat"
                @click="() => emit('submit')"
              >
                Save Changes
              </v-btn>
            </div>
          </template>
        </v-stepper>
      </template>
    </v-card>
  </v-dialog>
</template>

<style lang="scss" scoped>
.v-dialog {
  :deep(.v-stepper-header) {
    padding-left: 24px;
    padding-right: 24px;
    box-shadow: unset;
    background-color: rgb(var(--v-theme-divider2));
  }
  :deep(.v-divider) {
    opacity: 1;
    color: rgb(var(--v-theme-divider1));
  }

  :deep(.v-overlay__content > .v-card > .v-card-text) {
    padding-left: 0;
    padding-right: 0;
  }

  :deep(.selected-step) {
    opacity: 1;
    color: rgb(var(--v-theme-textbody1));
    .v-stepper-item__avatar {
      background-color: rgb(var(--v-theme-primary));
    }
  }
}
</style>
