<script setup lang="ts">
const NUMBER_OF_ITEMS_TO_DISPLAY_WHEN_NOT_SHOWING_ALL = 7;

interface EntityAttributesProps {
  entity: { attributes?: { [key: string]: string } };
}
const props = defineProps<EntityAttributesProps>();

const attributeKeysToDisplay = computed(() => {
  const dataObjectAttributes = Object.keys(props.entity.attributes ?? {});
  if (
    viewAllAttributes.value ||
    dataObjectAttributes.length <=
      NUMBER_OF_ITEMS_TO_DISPLAY_WHEN_NOT_SHOWING_ALL
  )
    return dataObjectAttributes;
  return dataObjectAttributes.slice(
    0,
    NUMBER_OF_ITEMS_TO_DISPLAY_WHEN_NOT_SHOWING_ALL,
  );
});

const showViewAllBtn = computed(
  () =>
    Object.keys(props.entity.attributes ?? {}).length >
    NUMBER_OF_ITEMS_TO_DISPLAY_WHEN_NOT_SHOWING_ALL,
);

const viewAllAttributes = ref(false);
</script>

<template>
  <v-container fluid class="pa-0">
    <v-row
      v-for="(attributeKey, index) in attributeKeysToDisplay"
      :key="index"
      no-gutters
    >
      <v-col cols="2" class="text-body-2 black-600 px-0 py-1">
        {{ attributeKey }}:
      </v-col>
      <v-col class="text-body-2 black-600 px-0 py-1">
        {{ entity.attributes?.[attributeKey] }}
      </v-col>
    </v-row>
    <v-row v-if="showViewAllBtn" no-gutters>
      <DesignComponentsViewAllButton v-model:view-all="viewAllAttributes" />
    </v-row>
  </v-container>
</template>
