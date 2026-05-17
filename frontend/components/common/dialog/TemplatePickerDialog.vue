<script setup lang="ts">
import type { ShepardTemplateIO } from "@dlr-shepard/backend-client";

defineProps<{
  title: string;
  templates: ShepardTemplateIO[];
  loading: boolean;
  isInstantiating?: boolean;
}>();

const showDialog = defineModel<boolean>("showDialog", {
  required: true,
  default: false,
});

const emit = defineEmits<{
  (e: "select", template: ShepardTemplateIO): void;
  (e: "start-blank"): void;
}>();

const { mobile } = useDisplay();
</script>

<template>
  <v-dialog v-model="showDialog" persistent max-width="600" :fullscreen="mobile">
    <v-card color="canvas">
      <template #title>
        <div class="d-flex justify-space-between align-baseline">
          <div class="text-h4">{{ title }}</div>
          <v-btn
            variant="plain"
            icon="mdi-close"
            :disabled="isInstantiating"
            @click="showDialog = false"
          />
        </div>
      </template>
      <template #text>
        <div class="px-2">
          <p class="text-body-2 text-medium-emphasis mb-4">
            Choose a template to get started quickly, or start from a blank form.
          </p>

          <CenteredLoadingSpinner v-if="loading" />

          <div
            v-else-if="templates.length === 0"
            class="text-center py-6 text-medium-emphasis"
          >
            <v-icon icon="mdi-file-document-outline" size="40" class="mb-2" />
            <div>No templates available.</div>
          </div>

          <v-row v-else dense>
            <v-col
              v-for="template in templates"
              :key="template.appId"
              cols="12"
              sm="6"
            >
              <v-card
                variant="outlined"
                class="template-card pa-3 h-100"
                :loading="isInstantiating"
                :disabled="isInstantiating"
                @click="emit('select', template)"
              >
                <div class="d-flex align-start ga-2">
                  <v-icon
                    icon="mdi-file-document-outline"
                    color="primary"
                    size="20"
                    class="mt-1 flex-shrink-0"
                  />
                  <div>
                    <div class="text-subtitle-2 font-weight-bold">
                      {{ template.name }}
                    </div>
                    <div
                      v-if="template.description"
                      class="text-body-2 text-medium-emphasis mt-1"
                    >
                      {{ template.description }}
                    </div>
                  </div>
                </div>
              </v-card>
            </v-col>
          </v-row>
        </div>
      </template>
      <template #actions>
        <div class="d-flex px-6 pb-4 w-100 align-center">
          <v-btn
            variant="text"
            color="primary"
            prepend-icon="mdi-file-outline"
            :disabled="isInstantiating"
            @click="emit('start-blank')"
          >
            Start from blank
          </v-btn>
          <v-spacer />
          <v-btn
            variant="flat"
            color="treeview"
            :disabled="isInstantiating"
            @click="showDialog = false"
          >
            Cancel
          </v-btn>
        </div>
      </template>
    </v-card>
  </v-dialog>
</template>

<style lang="scss" scoped>
.template-card {
  cursor: pointer;
  transition: border-color 0.15s;

  &:hover:not(.v-card--disabled) {
    border-color: rgb(var(--v-theme-primary)) !important;
  }
}
</style>
