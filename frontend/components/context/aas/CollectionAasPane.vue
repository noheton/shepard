<template>
  <!-- AAS integration disabled on this instance -->
  <v-alert
    v-if="isDisabled"
    type="info"
    variant="tonal"
    class="ma-2"
    density="compact"
  >
    AAS integration is disabled. An instance admin can enable it via
    <strong>Admin → AAS Integration</strong>.
  </v-alert>

  <!-- Collection not yet exposed as a Shell -->
  <v-alert
    v-else-if="isNotFound"
    type="warning"
    variant="tonal"
    class="ma-2"
    density="compact"
  >
    This collection is not currently accessible as an AAS Shell.
  </v-alert>

  <!-- Fetch error -->
  <v-alert
    v-else-if="error"
    type="error"
    variant="tonal"
    class="ma-2"
    density="compact"
  >
    {{ error }}
  </v-alert>

  <!-- Loading -->
  <v-progress-linear v-else-if="isLoading" indeterminate class="ma-2" />

  <!-- AAS identity card -->
  <v-card v-else variant="flat" class="pa-2">
    <v-list density="compact" class="py-0">
      <v-list-item class="px-0">
        <template #prepend>
          <span class="text-caption text-medium-emphasis mr-3" style="min-width:90px">Shell IRI</span>
        </template>
        <div class="d-flex align-center gap-2 flex-wrap">
          <code class="text-caption">{{ shellIri }}</code>
          <ClipboardButton :text="shellIri" success-message="Shell IRI copied" />
        </div>
      </v-list-item>

      <v-list-item v-if="shell?.idShort" class="px-0">
        <template #prepend>
          <span class="text-caption text-medium-emphasis mr-3" style="min-width:90px">idShort</span>
        </template>
        <span class="text-caption">{{ shell.idShort }}</span>
      </v-list-item>

      <v-list-item v-if="shell?.assetInformation?.assetKind" class="px-0">
        <template #prepend>
          <span class="text-caption text-medium-emphasis mr-3" style="min-width:90px">Asset kind</span>
        </template>
        <v-chip size="x-small" label color="primary" variant="tonal">
          {{ shell.assetInformation.assetKind }}
        </v-chip>
      </v-list-item>

      <v-list-item v-if="submodelsTotal > 0" class="px-0">
        <template #prepend>
          <span class="text-caption text-medium-emphasis mr-3" style="min-width:90px">Submodels</span>
        </template>
        <span class="text-caption">{{ submodelsTotal }} DataObject(s)</span>
      </v-list-item>
    </v-list>

    <div class="d-flex mt-2 px-0">
      <v-btn
        variant="tonal"
        size="small"
        prepend-icon="mdi-layers-triple-outline"
        :to="`/aas/shells/${props.collectionAppId}`"
      >
        View AAS Shell
      </v-btn>
    </div>
  </v-card>
</template>

<script setup lang="ts">
import { computed } from "vue";
import { useAasShell } from "~/composables/aas/useAasShell";

const props = defineProps<{
  collectionAppId: string;
}>();

const { shell, submodelsTotal, isLoading, isDisabled, isNotFound, error } =
  useAasShell(props.collectionAppId);

const shellIri = computed(() => `urn:shepard:collection:${props.collectionAppId}`);
</script>
