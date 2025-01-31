<script setup lang="ts">
defineProps<{ collectionId: number; predecessorIds: number[] }>();
const emit = defineEmits<{
  (e: "predecessorsChanged", value: number[]): void;
}>();
</script>

<template>
  <v-row>
    <template v-for="(predecessorDO, index) in predecessorIds" :key="index">
      <v-col cols="11" class="pt-1">
        <DataObjectEditSearchAutocomplete
          :key="predecessorDO"
          input-label="Predecessor"
          :initial-data-object-id="predecessorDO ?? undefined"
          :collection-id="collectionId"
          @search-ended="
            (id: number | null) => {
              const newValue = id ?? -1;
              emit(
                'predecessorsChanged',
                predecessorIds.map((p, i) => (i !== index ? p : newValue)),
              );
            }
          "
        />
      </v-col>
      <v-col cols="1" class="pt-1">
        <v-btn
          icon="mdi-delete-outline"
          variant="plain"
          @click="
            emit(
              'predecessorsChanged',
              predecessorIds.filter((_, i) => i !== index),
            )
          "
        />
      </v-col>
    </template>
  </v-row>

  <v-row>
    <v-col class="pt-1">
      <v-btn
        prepend-icon="mdi-plus-circle"
        variant="flat"
        style="background-color: rgb(var(--v-theme-treeview))"
        @click="emit('predecessorsChanged', [...predecessorIds, -1])"
      >
        Add Predecessor
      </v-btn>
    </v-col>
  </v-row>
</template>
