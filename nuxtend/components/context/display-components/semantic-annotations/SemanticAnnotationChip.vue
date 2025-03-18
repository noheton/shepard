<script setup lang="ts">
interface SemanticAnnotationChipProps {
  property: string;
  propertyIri: string;
  value: string;
  valueIri: string;
}
const props = defineProps<SemanticAnnotationChipProps>();
</script>

<template>
  <li>
    <v-chip
      color="primary"
      variant="outlined"
      rounded="lg"
      class="semantic-key"
    >
      <a target="_blank" :href="props.propertyIri">{{ props.property }}</a>
    </v-chip>
    <v-chip color="primary" variant="flat" rounded="lg" class="semantic-value">
      <a target="_blank" :href="props.valueIri">{{ props.value }}</a>
      <template #close>
        <v-icon icon="mdi-close-circle" @click.stop="() => {}" />
      </template>
    </v-chip>
  </li>
</template>

<style lang="scss" scoped>
li {
  display: inline-block;
}
.semantic-key {
  border-top-right-radius: 0 !important;
  border-bottom-right-radius: 0 !important;
}
.semantic-value {
  border-top-left-radius: 0 !important;
  border-bottom-left-radius: 0 !important;
}
a {
  color: inherit;
  text-decoration: none;
  &:hover {
    text-decoration: underline;
  }
}
</style>
