<script setup lang="ts">
export interface ContextMenuItem {
  label: string;
  onClick: () => void;
  icon: string;
}

defineProps<{
  items: ContextMenuItem[];
}>();

const emit = defineEmits<{
  (e: "expansion-state-changed", value: boolean): void;
}>();
</script>

<template>
  <v-menu class="pa-0">
    <template #activator="{ props: activatorProps }">
      <v-btn
        icon="mdi-dots-horizontal"
        v-bind="activatorProps"
        variant="plain"
        density="compact"
        color="primary"
        @click.stop.prevent
      />
      {{
        emit(
          "expansion-state-changed",
          activatorProps["aria-expanded"] === "true",
        )
      }}
    </template>
    <v-list bg-color="canvas">
      <v-list-item
        v-for="({ icon, label, onClick }, index) in items"
        :key="index"
        density="compact"
        @click="onClick"
      >
        <template #prepend>
          <v-icon :icon="icon" color="textbody1" class="opacity-100" />
        </template>
        <template #title>
          <span class="text-body-1 mr-8">{{ label }}</span>
        </template>
      </v-list-item>
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
