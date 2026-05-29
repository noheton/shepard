<script lang="ts" setup>
interface ContainerTitleAndMetadataDisplayProps {
  id: number;
  name: string;
  typeLabel: string;
  nItems?: number;
  database?: string;
  /** Lifecycle status string (DRAFT / IN_REVIEW / READY / PUBLISHED / ARCHIVED).
   *  When present, a StatusChip is rendered next to the title. Null/undefined = no chip. */
  status?: string | null;
}

defineProps<ContainerTitleAndMetadataDisplayProps>();
</script>

<template>
  <v-container class="pt-0 pl-0 pr-0" fluid>
    <v-row no-gutters>
      <v-col class="ml-n1 pb-6 d-flex align-center ga-3" cols="12">
        <h1 class="text-h1">{{ name }}</h1>
        <StatusChip v-if="status" :status="status" />
      </v-col>
    </v-row>
    <v-row class="text-body-2 text-medium-emphasis">
      <v-col>
        <strong>Container Type:</strong>
        {{ typeLabel }}
      </v-col>
      <v-col>
        <strong>Container ID:</strong>
        {{ id }}
      </v-col>
      <v-col v-if="nItems !== undefined">
        <strong>Number of items:</strong>
        {{ nItems }}
      </v-col>
      <v-col v-if="!!database">
        <strong>Database:</strong>
        {{ database }}
      </v-col>
      <v-col class="text-right">
        <slot name="buttons" />
      </v-col>
    </v-row>
  </v-container>
</template>

<style lang="scss" scoped>
strong {
  display: block;
}

.text-right {
  flex-grow: 2;
}
</style>
