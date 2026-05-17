<script setup lang="ts">
import type { DocPage } from "~/utils/helpMarkdown";

interface DocSection {
  header: string;
  pages: DocPage[];
}

const props = defineProps<{
  sections: DocSection[];
  activePage: string;
}>();

const emit = defineEmits<{
  navigate: [page: string];
}>();
</script>

<template>
  <div>
    <div class="text-subtitle-2 text-medium-emphasis mb-2 px-2">Documentation</div>
    <div v-for="section in props.sections" :key="section.header" class="mb-3">
      <div class="text-caption text-medium-emphasis font-weight-bold px-2 mb-1 text-uppercase">
        {{ section.header }}
      </div>
      <v-btn
        v-for="page in section.pages"
        :key="page.page"
        variant="text"
        density="compact"
        :class="[
          'nav-item',
          'd-flex',
          'justify-start',
          'w-100',
          'text-body-2',
          props.activePage === page.page ? 'active-nav-item' : '',
        ]"
        @click="emit('navigate', page.page)"
      >
        {{ page.title }}
      </v-btn>
    </div>
  </div>
</template>

<style lang="scss" scoped>
.nav-item {
  height: 32px;
  padding-left: 1rem;
  border-left: 3px solid transparent;
  text-transform: none;
  font-size: 0.875rem;
  border-radius: 0;
  letter-spacing: 0;
}

.active-nav-item {
  border-left: 3px solid rgb(var(--v-theme-primary));
  background: rgba(var(--v-theme-primary), 0.08);
}
</style>
