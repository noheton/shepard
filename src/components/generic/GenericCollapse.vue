<template>
  <div class="component">
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

<script lang="ts">
import { defineComponent } from "vue";
export default defineComponent({
  props: {
    title: {
      type: String,
      required: true,
    },
    collapsed: {
      type: Boolean,
      default: false,
    },
  },
  data() {
    return { collapsedState: this.collapsed };
  },
  methods: {
    toggleVisible() {
      this.collapsedState = !this.collapsedState;
    },
  },
});
</script>
