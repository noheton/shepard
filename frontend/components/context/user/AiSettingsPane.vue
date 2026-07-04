<script setup lang="ts">
import { useAiSettings } from "~/composables/context/useAiSettings";

const { baseUrl, model, isSaving, save } = useAiSettings();

const localBaseUrl = ref(baseUrl.value);
const localModel = ref(model.value);
const saved = ref(false);

// Keep local copies in sync if another caller mutates the singletons.
watch(baseUrl, (v) => { localBaseUrl.value = v; });
watch(model, (v) => { localModel.value = v; });

async function handleSave() {
  await save(localBaseUrl.value, localModel.value);
  if (!isSaving.value) {
    saved.value = true;
    setTimeout(() => { saved.value = false; }, 2500);
  }
}
</script>

<template>
  <div>
    <div class="top-row">
      <h4 class="text-h4">AI Settings</h4>
    </div>
    <p class="text-body-2 mb-4">
      Per-user LLM provider configuration. When set, these values override the
      instance-wide defaults configured by your administrator. The API key field
      requires encrypted secret storage (U2) which has not yet shipped — it will
      unlock in a future release.
    </p>

    <v-text-field
      v-model="localBaseUrl"
      label="Provider Base URL"
      placeholder="https://api.openai.com/v1"
      hint="Base URL for the LLM HTTP API. Leave blank to use the instance default."
      persistent-hint
      clearable
      :disabled="isSaving"
      class="mb-4"
    />
    <v-text-field
      v-model="localModel"
      label="Model"
      placeholder="gpt-4o"
      hint="Model name or ID. Leave blank to use the instance default."
      persistent-hint
      clearable
      :disabled="isSaving"
      class="mb-4"
    />

    <!-- API key is locked until U2 (encrypted-at-rest secret storage) ships. -->
    <v-text-field
      label="API Key"
      model-value=""
      placeholder="Requires secure settings (U2) — not yet available"
      prepend-inner-icon="mdi-lock-outline"
      disabled
      class="mb-4"
    />
    <v-alert type="info" variant="tonal" density="compact" class="mb-6">
      The API key field will become editable once the encrypted secret storage
      feature ships. Until then, your administrator can configure a
      shared API key at the instance level.
    </v-alert>

    <div class="d-flex align-center gap-3">
      <v-btn
        variant="flat"
        color="primary"
        :loading="isSaving"
        :disabled="isSaving"
        @click="handleSave"
      >
        Save
      </v-btn>
      <v-fade-transition>
        <span v-if="saved" class="text-body-2 text-medium-emphasis">
          <v-icon icon="mdi-check-circle-outline" size="small" class="mr-1" />
          Saved
        </span>
      </v-fade-transition>
    </div>
  </div>
</template>

<style scoped lang="scss">
.top-row {
  display: flex;
  flex-direction: row;
  justify-content: space-between;
  margin-bottom: 16px;
}
.gap-3 {
  gap: 12px;
}
</style>
