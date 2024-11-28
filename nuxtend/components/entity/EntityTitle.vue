<script setup lang="ts">
interface DataObjectTitleProps {
  idLabel: string;
  entity: {
    id: number;
    name: string;
    createdAt: Date;
    createdBy: string;
    updatedAt: Date | null;
    updatedBy: string | null;
  };
}
defineProps<DataObjectTitleProps>();
</script>

<template>
  <v-container fluid class="pt-0 pl-0 pr-0">
    <v-row no-gutters>
      <v-col cols="12" class="ml-n1 pb-6">
        <h1 class="text-h1">{{ entity.name }}</h1>
      </v-col>
    </v-row>
    <v-row no-gutters class="text-body-2 text-black-400">
      <v-col cols="4">{{ idLabel }}: {{ entity.id }}</v-col>
      <v-col cols="4">
        Created at: {{ entity.createdAt?.toDateString() }} by
        {{ entity.createdBy }}
      </v-col>
      <v-col v-if="entity.updatedAt" cols="4">
        Updated at: {{ entity.updatedAt?.toDateString() }} by
        {{ entity.updatedBy }}
      </v-col>
      <v-spacer v-else />
    </v-row>
  </v-container>
</template>
