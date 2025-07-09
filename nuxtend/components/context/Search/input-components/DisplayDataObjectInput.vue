<script lang="ts" setup>
const props = defineProps<{
  collectionId: number;
  dataObjectId: number;
}>();

const emit = defineEmits(["clicked"]);

const { dataObject, isLoading } = useFetchDataObject(
  props.collectionId,
  props.dataObjectId,
);
const displayName = computed(() =>
  dataObject.value
    ? dataObject.value.name + " (ID: " + props.dataObjectId + ")"
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
    label="Data Object"
    type="text"
    variant="outlined"
    @click="emit('clicked')"
  />
</template>
