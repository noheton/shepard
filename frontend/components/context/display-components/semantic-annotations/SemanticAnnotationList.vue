<script lang="ts" setup>
import type {
  ResponseError,
  SemanticAnnotation,
} from "@dlr-shepard/backend-client";

const emit = defineEmits<{
  (e: "annotations", value: SemanticAnnotation[]): void;
}>();

const props = defineProps<{ annotated: Annotated; canDelete: boolean; limit?: number }>();

const annotations = ref<SemanticAnnotation[]>([]);
const isLoading = ref<boolean>(true);
// UI-016: when `limit` is set, the overflow chip ("+N more") is clickable to
// reveal the rest of the annotations on the same row. Default is collapsed.
const expanded = ref<boolean>(false);

const effectiveLimit = computed(() =>
  props.limit && !expanded.value ? props.limit : undefined,
);
const displayed = computed(() =>
  effectiveLimit.value
    ? annotations.value.slice(0, effectiveLimit.value)
    : annotations.value,
);
const hiddenCount = computed(() =>
  effectiveLimit.value
    ? Math.max(0, annotations.value.length - effectiveLimit.value)
    : 0,
);

async function fetchSemanticAnnotations() {
  isLoading.value = true;
  try {
    annotations.value = await props.annotated.fetchAnnotations();
    emit("annotations", annotations.value);
  } catch (e) {
    handleError(e as ResponseError, "fetching semantic annotations");
  } finally {
    isLoading.value = false;
  }
}

fetchSemanticAnnotations();

onAnnotationsUpdated(fetchSemanticAnnotations);
</script>

<template>
  <div v-if="!isLoading">
    <ul v-if="annotations.length > 0">
      <SemanticAnnotationChip
        v-for="annotation in displayed"
        :key="annotation.id"
        :annotated-type="annotated"
        :annotation="annotation"
        :can-delete="canDelete"
      />
      <v-chip
        v-if="hiddenCount > 0"
        size="x-small"
        variant="text"
        class="text-medium-emphasis annotation-overflow-chip"
        :data-testid="`annotations-overflow-chip`"
        :aria-label="`Show ${hiddenCount} more annotations`"
        @click.stop.prevent="expanded = true"
      >+{{ hiddenCount }} more</v-chip>
      <v-chip
        v-if="props.limit && expanded && annotations.length > props.limit"
        size="x-small"
        variant="text"
        class="text-medium-emphasis annotation-collapse-chip"
        :data-testid="`annotations-collapse-chip`"
        aria-label="Collapse annotations"
        @click.stop.prevent="expanded = false"
      >Show less</v-chip>
    </ul>
  </div>
  <CenteredLoadingSpinner v-else />
</template>

<style lang="scss">
ul {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 16px;
}
.annotation-overflow-chip,
.annotation-collapse-chip {
  cursor: pointer;
  &:hover {
    text-decoration: underline;
  }
}
</style>
