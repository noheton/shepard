<script setup lang="ts">
import {
  AdminFeaturesApi,
  type FeatureToggleIO,
} from "@dlr-shepard/backend-client";
import { useV2ShepardApi } from "~/composables/common/api/useV2ShepardApi";
import { useFetchFeatureToggles } from "~/composables/context/admin/useFetchFeatureToggles";
import { AdminFragments } from "./adminMenuItems";

const { features, isLoading, refresh } = useFetchFeatureToggles();

const patchingName = ref<string | null>(null);
const patchError = ref<string | null>(null);

async function onToggle(feature: FeatureToggleIO, newValue: boolean) {
  patchError.value = null;
  patchingName.value = feature.name;
  try {
    await useV2ShepardApi(AdminFeaturesApi).value.patchFeature({
      name: feature.name,
      patchFeatureToggleIO: { enabled: newValue },
    });
    await refresh();
  } catch (e) {
    patchError.value = `Failed to update "${feature.name}". Please try again.`;
    handleError(e, `patching feature toggle "${feature.name}"`);
  } finally {
    patchingName.value = null;
  }
}
</script>

<template>
  <div :id="AdminFragments.FEATURE_TOGGLES" class="d-flex flex-column ga-4">
    <h4 class="text-h4">Feature Toggles</h4>

    <v-alert
      v-if="patchError"
      type="error"
      closable
      @click:close="patchError = null"
    >
      {{ patchError }}
    </v-alert>

    <centered-loading-spinner v-if="isLoading && features.length === 0" />

    <EmptyListIcon
      v-else-if="!isLoading && features.length === 0"
      label="No feature toggles registered"
    />

    <v-list v-else lines="two">
      <v-list-item
        v-for="feature in features"
        :key="feature.name"
        :disabled="patchingName === feature.name"
      >
        <template #prepend>
          <v-progress-circular
            v-if="patchingName === feature.name"
            indeterminate
            size="24"
            class="mr-4"
          />
        </template>

        <v-list-item-title>
          <strong>{{ feature.name }}</strong>
        </v-list-item-title>
        <v-list-item-subtitle>{{ feature.description }}</v-list-item-subtitle>

        <template #append>
          <v-switch
            :model-value="feature.enabled"
            color="primary"
            hide-details
            :disabled="patchingName !== null"
            @update:model-value="(val) => onToggle(feature, val as boolean)"
          />
        </template>
      </v-list-item>
    </v-list>
  </div>
</template>

<style scoped lang="scss"></style>
