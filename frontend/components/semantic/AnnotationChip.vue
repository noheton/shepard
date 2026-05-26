<script lang="ts" setup>
/**
 * SEMA-V6-005 — AnnotationChip.
 *
 * Compact chip rendering a semantic annotation as `predicate: value`.
 * - Tooltip shows the full predicate IRI.
 * - Delete button visible when `canDelete` is true.
 * - Click opens edit mode (via the `edit` emit — caller decides how to handle).
 *
 * Uses the existing SemanticAnnotation shape (propertyName / valueName /
 * propertyIRI / valueIRI) so it works against both the legacy per-entity
 * endpoints and the upcoming polymorphic /v2/annotations/* surface.
 */
import type { SemanticAnnotation } from "@dlr-shepard/backend-client";

const props = defineProps<{
  annotation: SemanticAnnotation;
  /** Show the delete (×) button. Should be true when the caller has write/manage permission. */
  canDelete?: boolean;
}>();

const emit = defineEmits<{
  /** Emitted when the user clicks the chip body — caller should open an edit dialog. */
  edit: [annotation: SemanticAnnotation];
  /** Emitted when the user confirms deletion — caller should call deleteAnnotation. */
  delete: [annotation: SemanticAnnotation];
}>();

const showDeleteConfirm = ref(false);

/** Resolved predicate label: prefer propertyName, fall back to the local part of the IRI. */
const predicateLabel = computed(() => {
  if (props.annotation.propertyName) return props.annotation.propertyName;
  const iri = props.annotation.propertyIRI ?? "";
  return iri.split(/[/#]/).pop() ?? iri;
});

/** Resolved value label: prefer valueName, fall back to IRI local name. */
const valueLabel = computed(() => {
  if (props.annotation.valueName) return props.annotation.valueName;
  const iri = props.annotation.valueIRI ?? "";
  return iri.split(/[/#]/).pop() ?? iri;
});

function onChipClick() {
  emit("edit", props.annotation);
}

function onDeleteClick(e: Event) {
  e.stopPropagation();
  showDeleteConfirm.value = true;
}

function onDeleteConfirmed() {
  showDeleteConfirm.value = false;
  emit("delete", props.annotation);
}
</script>

<template>
  <span class="annotation-chip-root">
    <v-tooltip :text="annotation.propertyIRI ?? predicateLabel" location="top" max-width="360">
      <template #activator="{ props: tip }">
        <!-- Key chip: predicate label -->
        <v-chip
          v-bind="tip"
          color="primary"
          variant="outlined"
          rounded="lg"
          size="small"
          class="annotation-chip-key"
          :aria-label="`Annotation predicate: ${predicateLabel}`"
          @click.stop="onChipClick"
        >
          {{ predicateLabel }}
        </v-chip>
      </template>
    </v-tooltip>
    <!-- Value chip: object label -->
    <v-chip
      color="primary"
      variant="flat"
      rounded="lg"
      size="small"
      class="annotation-chip-value"
      :aria-label="`Annotation value: ${valueLabel}`"
      @click.stop="onChipClick"
    >
      {{ valueLabel }}
      <template v-if="canDelete" #close>
        <v-icon
          icon="mdi-close-circle"
          aria-label="Delete annotation"
          @click.stop="onDeleteClick"
        />
      </template>
    </v-chip>

    <!-- Inline delete confirmation dialog -->
    <ConfirmDeleteDialog
      v-if="showDeleteConfirm"
      v-model:show-dialog="showDeleteConfirm"
      @confirmed="onDeleteConfirmed"
    />
  </span>
</template>

<style lang="scss" scoped>
.annotation-chip-root {
  display: inline-flex;
  align-items: center;
}

.annotation-chip-key {
  border-top-right-radius: 0 !important;
  border-bottom-right-radius: 0 !important;
  cursor: pointer;
}

.annotation-chip-value {
  border-top-left-radius: 0 !important;
  border-bottom-left-radius: 0 !important;
  cursor: pointer;
}
</style>
