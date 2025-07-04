<script lang="ts" setup>
const props = defineProps<{ entityName: string }>();
const emit = defineEmits(["click"]);
</script>

<template>
  <v-btn
    class="bg-primary text-canvas"
    variant="flat"
    :style="{ marginTop: '3px' }"
    @click="emit('click')"
  >
    {{ props.entityName }}
  </v-btn>
</template>
