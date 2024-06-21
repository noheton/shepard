<script setup lang="ts">
import type { BasicEntity } from "@dlr-shepard/shepard-client";
import type { BPopover } from "bootstrap-vue";
import { ref, type PropType } from "vue";

defineProps({
  results: {
    type: Array as PropType<BasicEntity[]>,
    required: true,
  },
  titleText: {
    type: String,
    default: "",
  },
});

const emit = defineEmits(["selected"]);
const popover = ref<BPopover | null>(null);

function handleOK(entity: BasicEntity) {
  popover.value?.$emit("close");
  emit("selected", entity);
}
</script>

<template>
  <b-popover
    ref="popover"
    custom-class="modal-popover"
    target="userFormInput"
    triggers="focus"
    placement="bottom"
  >
    <template v-if="titleText.length > 0" #title>
      {{ titleText }}
    </template>
    <div v-if="results == undefined"><Loading /></div>
    <div v-else>
      <b-list-group class="mb-2">
        <b-list-group-item
          v-for="(entity, index) in results"
          :key="index"
          append
          @click="handleOK(entity)"
        >
          <div>
            <b>{{ entity.name }}</b> ID: {{ entity.id }}
          </div>
        </b-list-group-item>
      </b-list-group>
    </div>
  </b-popover>
</template>
