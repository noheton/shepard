<script setup lang="ts">
import type { Roles } from "@dlr-shepard/backend-client";
import type { PropType } from "vue";

const props = defineProps({
  roles: {
    type: Object as PropType<Roles | undefined>,
    default: undefined,
  },
});
</script>

<template>
  <span v-if="props.roles">
    <ManagerIcon
      v-if="props.roles.owner || props.roles.manager"
      v-b-tooltip.hover
      title="Manager"
      font-scale="0.8"
    />
    <WriterIcon
      v-else-if="props.roles.writer"
      v-b-tooltip.hover
      title="Writer"
      font-scale="0.8"
    />
    <ReaderIcon
      v-else-if="props.roles.reader"
      v-b-tooltip.hover
      title="Reader"
      font-scale="0.8"
    />
  </span>
</template>
