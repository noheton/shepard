<script setup lang="ts">
const searchText = defineModel<string | null>();
const emit = defineEmits(["onSearch"]);
</script>

<template>
  <v-text-field
    v-model="searchText"
    :clearable="true"
    density="compact"
    placeholder="Search"
    variant="outlined"
    :hide-details="true"
    width="599px"
    style="box-shadow: 0px 12px 30px 0px rgba(16, 24, 40, 0.05)"
    @keydown.enter="emit('onSearch')"
  >
    <template #append-inner>
      <v-btn
        variant="flat"
        color="primary"
        text="Search"
        @click="emit('onSearch')"
      />
    </template>
  </v-text-field>
</template>

<style scoped>
.v-input--density-compact {
  --v-input-control-height: 42px;
}

:deep(.v-field--appended) {
  padding-inline-end: 4px;
}
</style>
