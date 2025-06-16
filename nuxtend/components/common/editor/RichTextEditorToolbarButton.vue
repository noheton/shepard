<script lang="ts" setup>
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
  onClick: (() => void) | undefined;
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
        :color="props.toggleColor ?? 'primary'"
        :density="props.density ?? 'compact'"
        :disabled="props.disabled"
        :variant="props.variant ?? 'plain'"
        v-bind="tooltipProps"
      >
        <v-btn
          :icon="props.icon"
          :text="props.btnText"
          class="pa-0"
          @click="props.onClick"
        />
      </v-btn-toggle>
    </template>
  </v-tooltip>
</template>

<style lang="scss" scoped></style>
