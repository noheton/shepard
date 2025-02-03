<script setup lang="ts">
defineProps<{
  items: {
    label: string;
    onClick: () => void;
    icon: string;
  }[];
}>();

const emit = defineEmits<{
  (e: "expansion-state-changed", value: boolean): void;
}>();
</script>

<template>
  <v-menu class="pa-0">
    <template #activator="{ props }">
      <v-btn
        icon="mdi-dots-horizontal"
        v-bind="props"
        variant="plain"
        density="compact"
        color="primary"
        @click.stop.prevent
      />
      {{ emit("expansion-state-changed", props["aria-expanded"] === "true") }}
    </template>
    <v-list>
      <v-list-item
        v-for="({ icon, label, onClick }, index) in items"
        :key="index"
        color="canvas"
        density="compact"
        :prepend-icon="icon"
        :title="label"
        @click="onClick"
      />
    </v-list>
  </v-menu>
</template>

<style lang="scss" scoped>
.v-list {
  padding-top: 0;
  padding-bottom: 0;

  :deep(.v-list-item__spacer) {
    width: 16px;
  }
}
</style>
