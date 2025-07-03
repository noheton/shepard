<script lang="ts" setup>
import { Mode, type MenuButton } from "vanilla-jsoneditor";
import JsonEditorVue from "json-editor-vue";

const searchQuery = defineModel<string>("searchQuery", {
  required: false,
  default: JSON.stringify(
    {
      OR: [
        {
          property: "name",
          operator: "contains",
          value: "My",
        },
        {
          NOT: {
            property: "id",
            operator: "gt",
            value: 12,
          },
        },
      ],
    },
    null,
    2,
  ),
});

function handleMenu(menuButtons: MenuButton[]) {
  return menuButtons
    .filter(v => v.text !== "table" && v.type === "button")
    .filter(v => v.icon?.iconName !== "filter")
    .map(v => {
      if (v.text == "tree") v.className += " jse-last";
      return v;
    });
}
</script>

<template>
  <JsonEditorVue
    v-model="searchQuery"
    :mode="Mode.text"
    @render-menu="handleMenu"
  />
</template>

<style lang="scss" scoped></style>
