<script setup lang="ts">
import type { MenuEntry } from "./menuListTypes";

export interface MenuListProps {
  header: string;
  menuEntries: MenuEntry[];
}

const props = defineProps<MenuListProps>();
const { routeFragment } = useRouteFragment();

onMounted(() => {
  // Manually set menu button's active class
  const activeMenuButton = document.getElementById(
    "menu-" + routeFragment.value,
  );
  activeMenuButton?.classList.add("active-button");
});
</script>

<template>
  <div class="d-flex flex-column align-stretch align-self-stretch">
    <span class="text-subtitle-1 pb-2 ml-8">{{ props.header }}</span>
    <v-btn
      v-for="item in props.menuEntries"
      :id="'menu-' + item.fragment"
      :key="item.fragment"
      :to="{ hash: `#${item.fragment}` }"
      :prepend-icon="item.icon"
      style="justify-content: flex-start"
      :class="[
        'text-body-1',
        routeFragment === item.fragment ? 'active-button' : '',
      ]"
      variant="text"
      density="default"
    >
      {{ item.name }}
    </v-btn>
  </div>
</template>

<style lang="scss" scoped>
.v-btn {
  height: 40px;
  padding-left: 2em;
  border-left: 4px solid transparent;
}

:deep(.v-btn__overlay) {
  color: transparent;
}

.active-button {
  border-left: 4px solid rgb(var(--v-theme-primary));
  border-radius: 0px;

  :deep(.v-btn__overlay) {
    color: rgb(var(--v-theme-primary));
  }
}
</style>
