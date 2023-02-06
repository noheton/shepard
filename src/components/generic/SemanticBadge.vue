<script setup lang="ts">
import type { SemanticAnnotation } from "@dlr-shepard/shepard-client";
import { ref, watch } from "vue";

const props = defineProps({
  annotationList: {
    type: Array<SemanticAnnotation>,
    required: true,
  },
});

const attributesList = ref<
  { property: string; value: string; propertyIRI: string; valueIRI: string }[]
>([]);
function formatAnnotations() {
  attributesList.value = props.annotationList.map(a => {
    let split: string[];
    if (a.name?.includes("::")) split = a.name.split("::", 2);
    else if (a.name?.includes("-")) split = a.name.split("-", 2);
    else split = a.name ? [a.name, ""] : ["", ""];
    return {
      property: split[0],
      value: split[1],
      propertyIRI: a.propertyIRI,
      valueIRI: a.valueIRI,
    };
  });
}

watch(() => {
  return props.annotationList;
}, formatAnnotations);
</script>

<template>
  <div>
    <div v-for="(attribute, i) in attributesList" :key="i">
      <small>
        <b-badge class="p-0 mb-3 mr-1 float-left">
          <span class="bg-info py-1 px-2 rounded-left">
            <a :href="attribute.propertyIRI" class="text-white" target="_blank">
              {{ attribute.property }}
            </a>
          </span>
          <span class="bg-primary py-1 px-2 rounded-right">
            <a :href="attribute.valueIRI" class="text-white" target="_blank">
              {{ attribute.value }}
            </a>
          </span>
        </b-badge>
      </small>
    </div>
    <div style="clear: both"></div>
  </div>
</template>
