<script setup lang="ts">
/**
 * UH1d — Collection Properties pane.
 *
 * Surfaces the per-Collection settings from
 * GET/PATCH /v2/collections/{appId}/properties, starting with the
 * publishToHelmholtzKG opt-out toggle introduced in UH1d.
 *
 * Gate visibility on isAllowedToEdit so read-only members don't see
 * a disabled switch with no explanation.
 */
import { useCollectionProperties } from "~/composables/context/useCollectionProperties";

const props = defineProps<{
  collectionAppId: string;
}>();

const { properties, isLoading, isSaving, error, patch } =
  useCollectionProperties(props.collectionAppId);

async function onTogglePublishToHelmholtzKG(newValue: boolean) {
  await patch({ publishToHelmholtzKG: newValue });
}
</script>

<template>
  <div class="d-flex flex-column ga-2">
    <v-alert
      v-if="error"
      type="error"
      closable
      density="compact"
      @click:close="error = null"
    >
      {{ error }}
    </v-alert>

    <centered-loading-spinner v-if="isLoading && !properties" />

    <v-list v-else lines="two" density="compact">
      <v-list-item>
        <v-list-item-title class="font-weight-medium">
          Publish to Helmholtz Knowledge Graph
        </v-list-item-title>
        <v-list-item-subtitle>
          When enabled, this Collection appears in the Unhide feed
          harvested by the Helmholtz Knowledge Graph. Disable to opt
          this Collection out while keeping the instance-wide feed
          active.
        </v-list-item-subtitle>
        <template #append>
          <v-switch
            :model-value="properties?.publishToHelmholtzKG ?? true"
            color="primary"
            hide-details
            :disabled="isSaving"
            :loading="isSaving"
            @update:model-value="(val) => onTogglePublishToHelmholtzKG(val as boolean)"
          />
        </template>
      </v-list-item>
    </v-list>
  </div>
</template>

<style scoped lang="scss"></style>
