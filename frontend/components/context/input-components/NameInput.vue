<script setup lang="ts">
const name = defineModel<string>("name", {
  required: true,
});
</script>

<template>
  <SimpleInput v-model:input-string="name" label="Name" />
</template>
