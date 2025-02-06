<script lang="ts" setup>
import { mergeProps } from "vue";

export interface MenuListEntry {
  iconName: string;
  tooltipText: string;
  callBackFn: (() => boolean) | ((options: never) => boolean) | undefined;
}

interface RichTextEditorToolbarMenuProps {
  baseIcon: string;
  btnText?: string;
  tooltipText: string;
  density?: "default" | "comfortable" | "compact";
  disabled?: boolean;
  variant?:
    | "flat"
    | "text"
    | "elevated"
    | "tonal"
    | "outlined"
    | "plain"
    | undefined;
  menuEntries: MenuListEntry[];
  numOfMenuCols: number;
  menuIconSize: string;
}

const props = defineProps<RichTextEditorToolbarMenuProps>();
</script>
<template>
  <v-menu open-on-hover :disabled="props.disabled">
    <template #activator="{ isActive, props: menu }">
      <v-tooltip location="top" :text="props.tooltipText">
        <template #activator="{ props: tooltip }">
          <v-btn
            style="margin-left: -12px; margin-right: -12px"
            :variant="props.variant ?? 'plain'"
            v-bind="mergeProps(menu, tooltip)"
          >
            <v-icon>{{ baseIcon }}</v-icon>
            <v-icon size="small">
              {{ isActive ? "mdi-chevron-up" : "mdi-chevron-down" }}
            </v-icon>
          </v-btn>
        </template>
      </v-tooltip>
    </template>

    <v-container class="rounded elevation-12 overflow-hidden bg-color-canvas">
      <v-row class="py-0">
        <v-col
          v-for="(item, index) in props.menuEntries"
          :key="index"
          :cols="12 / props.numOfMenuCols"
          class="w-0 fill-height py-0 px-1"
        >
          <v-tooltip location="top" :text="item.tooltipText">
            <template #activator="{ props: tooltip }">
              <v-btn
                v-bind="tooltip"
                :variant="props.variant ?? 'plain'"
                :icon="item.iconName"
                :size="props.menuIconSize"
                @click="item.callBackFn"
              />
            </template>
          </v-tooltip>
        </v-col>
      </v-row>
    </v-container>
  </v-menu>
</template>

<style lang="scss" scoped>
:deep(.bg-color-canvas) {
  background-color: rgb(var(--v-theme-canvas));
}
</style>
