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
const props = defineProps<DataObjectTitleProps>();
const createdAt = toShortDateString(props.entity.createdAt);
const updatedAt = props.entity.updatedAt
  ? toShortDateString(props.entity.updatedAt)
  : undefined;
</script>

<template>
  <v-container fluid class="pt-0 pl-0 pr-0">
    <v-row no-gutters>
      <v-col cols="12" class="ml-n1 pb-6">
        <h1 class="text-h1">{{ entity.name }}</h1>
      </v-col>
    </v-row>
    <v-row no-gutters class="text-body-2 text-black-400">
      <v-col cols="2">
        <div>{{ idLabel }}:</div>
        <div>{{ entity.id }}</div>
      </v-col>
      <v-spacer />
      <v-col cols="4">
        <div>Created at:</div>
        <div>
          {{ createdAt }}
          by
          {{ entity.createdBy }}
        </div>
      </v-col>
      <v-spacer />
      <v-col cols="4">
        <template v-if="updatedAt">
          <div>Updated at:</div>
          <div>
            {{ updatedAt }}
            by
            {{ entity.updatedBy }}
          </div>
        </template>
      </v-col>
    </v-row>
  </v-container>
</template>
