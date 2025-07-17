<script setup lang="ts">
import type { RelationshipTableElement } from "../relationshipTableElement";

defineProps<{
  value: RelationshipTableElement["information"]["type"];
}>();
</script>

<template>
  <template v-if="value.type === 'Link'">Link</template>
  <template v-else-if="value.type === 'Data Object'">
    Data Object (ID: {{ value.id }})
  </template>
  <template v-else-if="value.type === 'Data Object Reference'">
    <DataObjectReferenceTypeCell :value="value" />
  </template>
  <template v-else-if="value.type === 'Collection Reference'">
    <CollectionReferenceTypeCell :value="value" />
  </template>
</template>
