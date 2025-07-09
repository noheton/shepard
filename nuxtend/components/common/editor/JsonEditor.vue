<script lang="ts" setup>
import { Mode, type MenuButton } from "vanilla-jsoneditor";
import JsonEditorVue from "json-editor-vue";
import "vanilla-jsoneditor/themes/jse-theme-dark.css";
import { useTheme } from "vuetify/framework";

const theme = useTheme();
const isDarkMode = computed(() => theme.global.current.value.dark);

const json = defineModel<string>("json", {
  required: false,
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
    v-model="json"
    :class="isDarkMode ? 'jse-theme-dark' : ''"
    :mode="Mode.text"
    class="json-editor"
    @render-menu="handleMenu"
  />
</template>

<style lang="scss" scoped>
.json-editor {
  --jse-theme-color: rgb(var(--v-theme-primary));
  --jse-theme-color-highlight: rgba(var(--v-theme-divider1), 0.4);
}
</style>
