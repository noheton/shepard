<script setup lang="ts">
import { useDisplay } from "vuetify";
interface InformationDialogProps {
  title: string;
  loading?: boolean;
  maxWidth?: number;
}

const {
  title,
  loading = false,
  maxWidth = 600,
} = defineProps<InformationDialogProps>();
const showDialog = defineModel<boolean>("showDialog", {
  required: true,
  default: false,
});
const { mobile } = useDisplay();
</script>

<template>
  <v-dialog v-model="showDialog" persistent :max-width="maxWidth" :fullscreen="mobile">
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
        <slot name="text" />
      </template>
      <template #actions>
        <slot name="actions">
          <!-- Default if no slot content for actions is provided -->
          <v-row justify="end">
            <v-spacer />
            <v-col cols="auto">
              <v-btn
                color="primary"
                variant="flat"
                class="ml-4"
                @click="showDialog = false"
              >
                Close
              </v-btn>
            </v-col>
          </v-row>
        </slot>
      </template>
    </v-card>
  </v-dialog>
</template>
