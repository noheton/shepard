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

  <!-- Not accessible as a Shell (caller lacks read access or plugin gap) -->
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
      <!-- Shell -->
      <v-list-item class="px-0">
        <template #prepend>
          <span class="text-caption text-medium-emphasis mr-3" style="min-width:110px">Shell (Collection)</span>
        </template>
        <div class="d-flex align-center gap-2 flex-wrap">
          <code class="text-caption">{{ shellIri }}</code>
          <ClipboardButton :text="shellIri" success-message="Shell IRI copied" />
          <span v-if="shell?.idShort" class="text-caption text-medium-emphasis ml-1">
            ({{ shell.idShort }})
          </span>
        </div>
      </v-list-item>

      <!-- Submodel -->
      <v-list-item class="px-0">
        <template #prepend>
          <span class="text-caption text-medium-emphasis mr-3" style="min-width:110px">Submodel (DataObject)</span>
        </template>
        <div class="d-flex align-center gap-2 flex-wrap">
          <code class="text-caption">{{ submodelIri }}</code>
          <ClipboardButton :text="submodelIri" success-message="Submodel IRI copied" />
        </div>
      </v-list-item>
    </v-list>

    <div class="d-flex flex-wrap ga-2 mt-2 px-0">
      <v-btn
        variant="tonal"
        size="small"
        prepend-icon="mdi-layers-triple-outline"
        :to="`/aas/shells/${props.collectionAppId}`"
      >
        Open AAS Shell
      </v-btn>
      <v-btn
        variant="tonal"
        size="small"
        prepend-icon="mdi-cube-outline"
        :to="`/aas/submodels/${props.collectionAppId}/${props.dataObjectAppId}`"
      >
        Open Submodel
      </v-btn>
    </div>
  </v-card>
</template>

<script setup lang="ts">
import { computed } from "vue";
import { useAasShell } from "~/composables/aas/useAasShell";

const props = defineProps<{
  collectionAppId: string;
  dataObjectAppId: string;
}>();

const { shell, isLoading, isDisabled, isNotFound, error } = useAasShell(
  props.collectionAppId,
);

const shellIri = computed(() => `urn:shepard:collection:${props.collectionAppId}`);
const submodelIri = computed(() => `urn:shepard:dataobject:${props.dataObjectAppId}`);
</script>
