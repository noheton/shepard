<script setup lang="ts">
defineProps<{ collectionId: number }>();

const predecessorIds = defineModel<number[]>("predecessorIds", {
  required: true,
});
</script>

<template>
  <v-row>
    <v-col
      v-for="(predecessorDO, index) in predecessorIds"
      :key="index"
      cols="12"
      :class="`d-flex ${index !== 0 ? 'pt-2' : ''} ${index + 1 !== predecessorIds.length ? 'pb-2' : 'pb-1'}`"
    >
      <DataObjectAutocomplete
        :key="predecessorDO"
        input-label="Predecessor"
        :initial-data-object-id="predecessorDO ?? undefined"
        :collection-id="collectionId"
        @search-ended="
          (id: number | null) => {
            const newValue = id ?? -1;
            predecessorIds[index] = newValue;
          }
        "
      />
      <v-btn
        icon="mdi-delete-outline"
        variant="text"
        @click="predecessorIds = predecessorIds.filter((_, i) => i !== index)"
      />
    </v-col>
    <v-col>
      <v-btn
        prepend-icon="mdi-plus-circle"
        variant="flat"
        style="background-color: rgb(var(--v-theme-treeview))"
        @click="predecessorIds.push(-1)"
      >
        Add Predecessor
      </v-btn>
    </v-col>
  </v-row>
</template>
