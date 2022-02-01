<template>
  <div class="component">
    <h4 @click="toggleVisible()">
      <span v-if="collapsedState">
        <ExtendIcon />
      </span>
      <span v-else>
        <CollapsIcon />
      </span>
      {{ title }}
    </h4>
    <b-collapse id="collapse" :visible="!collapsedState">
      <slot></slot>
    </b-collapse>
  </div>
</template>

<script lang="ts">
import Vue from "vue";
export default Vue.extend({
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
