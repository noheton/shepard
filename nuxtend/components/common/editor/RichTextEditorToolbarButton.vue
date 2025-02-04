<script setup lang="ts">
interface RichTextEditorToolbarButtonProps {
  btnText?: string;
  tooltipText: string;
  icon?: string;
  density?: "default" | "comfortable" | "compact";
  disabled?: boolean;
  isTogglingDisabled?: boolean;
  variant?:
    | "flat"
    | "text"
    | "elevated"
    | "tonal"
    | "outlined"
    | "plain"
    | undefined;
  toggleColor?: string;
  onClick: (() => boolean) | undefined;
}

const toggle = defineModel<number | null>();

const props = defineProps<RichTextEditorToolbarButtonProps>();

watch(toggle, (newVal, _) => {
  if (props.isTogglingDisabled && newVal != null) {
    toggle.value = null;
  }
});
</script>

<template>
  <v-tooltip :text="props.tooltipText">
    <template #activator="{ props: tooltipProps }">
      <v-btn-toggle
        v-model="toggle"
        v-bind="tooltipProps"
        :density="props.density ?? 'compact'"
        :disabled="props.disabled"
        :variant="props.variant ?? 'plain'"
        :color="props.toggleColor ?? 'primary'"
      >
        <v-btn
          class="pa-0"
          :icon="props.icon"
          :text="props.btnText"
          @click="props.onClick"
        />
      </v-btn-toggle>
    </template>
  </v-tooltip>
</template>

<style scoped lang="scss"></style>
