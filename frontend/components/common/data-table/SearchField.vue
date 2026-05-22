<script setup lang="ts">
const { initialSearchText } = defineProps<{
  initialSearchText: string;
  placeholder?: string;
  searchResultHint?: string;
}>();

const searchText = ref<string | null>(initialSearchText);

const emit = defineEmits<{
  (e: "search-term-updated", value: string | undefined): void;
}>();

const onSearch = () =>
  emit("search-term-updated", searchText.value ? searchText.value : undefined);

let debounceTimer: ReturnType<typeof setTimeout> | null = null;
const onInput = () => {
  if (debounceTimer) clearTimeout(debounceTimer);
  debounceTimer = setTimeout(onSearch, 300);
};

const onClear = () => {
  if (debounceTimer) clearTimeout(debounceTimer);
  searchText.value = null;
  emit("search-term-updated", undefined);
};
</script>

<template>
  <v-text-field
    v-model="searchText"
    :hint="searchResultHint"
    :persistent-hint="true"
    :clearable="true"
    density="compact"
    color="primary"
    :placeholder="placeholder"
    variant="outlined"
    width="599px"
    @input="onInput"
    @keydown.enter="onSearch"
    @click:clear="onClear"
  >
    <template #prepend-inner>
      <v-icon icon="mdi-magnify" size="x-small" />
    </template>
  </v-text-field>
</template>

<style scoped lang="scss">
.v-input--density-compact {
  --v-input-control-height: 42px;
}

:deep(.v-field--appended) {
  padding-inline-end: 3px;
}

:deep(.v-input__control) {
  box-shadow: 0px 12px 30px 0px rgba(16, 24, 40, 0.05);
}

:deep(.v-input__details) {
  padding-top: 16px;
  padding-left: 0 !important;
}

:deep(.v-messages) {
  min-height: 22px;
}

:deep(.v-messages__message) {
  font-size: 14px;
  font-style: normal;
  font-weight: 500;
  line-height: 22px;
  color: rgb(var(--v-theme-low-emphasis));
}
</style>
