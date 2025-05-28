<script setup lang="ts">
const props = defineProps<{
  title: string;
  iconType: "collection" | "container";
  contentTitle: string;
  content: string;
  buttonText: string;
  buttonLink: string;
}>();

const iconSrcMap: Record<string, string> = {
  collection: new URL("../../../assets/collection_icon.svg", import.meta.url)
    .href,
  container: new URL("../../../assets/container_icon.svg", import.meta.url)
    .href,
};

const iconSrc = iconSrcMap[props.iconType];
</script>

<template>
  <v-card
    max-width="520"
    max-height="356"
    color="canvas"
    class="d-flex flex-column"
  >
    <template #prepend>
      <v-img :src="iconSrc" height="32" width="32" />
    </template>
    <template #title>
      <div class="text-subtitle-2">{{ title }}</div>
    </template>
    <template #text>
      <div class="text-h3 text-semibold">{{ contentTitle }}</div>
      <div class="text-body-1">
        {{ content }}
      </div>
    </template>
    <template #actions>
      <div class="d-flex justify-end w-100 mt-auto">
        <v-btn
          :to="{ path: buttonLink }"
          class="bg-primary text-canvas"
          variant="flat"
        >
          {{ buttonText }}
        </v-btn>
      </div>
    </template>
  </v-card>
</template>
