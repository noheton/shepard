<script setup lang="ts">
import { ref } from "vue";

const props = defineProps({
  title: {
    type: String,
    required: true,
  },
  collapsed: {
    type: Boolean,
    default: false,
  },
});

const collapsedState = ref<boolean>(props.collapsed);

function toggleVisible() {
  collapsedState.value = !collapsedState.value;
}
</script>

<template>
  <div>
    <h4 @click="toggleVisible()">
      <span v-if="collapsedState">
        <ExtendIcon font-scale="0.5" />
      </span>
      <span v-else>
        <CollapseIcon font-scale="0.5" />
      </span>
      {{ title }}
    </h4>
    <b-collapse id="collapse" :visible="!collapsedState">
      <slot></slot>
    </b-collapse>
  </div>
</template>
