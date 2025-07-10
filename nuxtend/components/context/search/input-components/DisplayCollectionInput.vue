<script lang="ts" setup>
const props = defineProps<{
  collectionId: number;
}>();

const emit = defineEmits(["clicked"]);

const { collection, isLoading } = useFetchCollection(props.collectionId);
const displayName = computed(() =>
  collection.value
    ? collection.value.name + " (ID: " + props.collectionId + ")"
    : "",
);
</script>

<template>
  <v-text-field
    v-model="displayName"
    :loading="isLoading"
    color="primary"
    density="compact"
    hide-details
    label="Collection"
    type="text"
    variant="outlined"
    @click="emit('clicked')"
  />
</template>
