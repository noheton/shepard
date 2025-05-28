<script setup lang="ts">
withDefaults(
  defineProps<{
    title: string;
    content: string;
    imageSrc: string;
    imageWidth: string;
    imageHeight: string;
    height?: string;
    additionalContent?: string;
    inverted?: boolean;
  }>(),
  { height: "270", additionalContent: undefined, inverted: false },
);
</script>
<template>
  <v-container
    fluid
    :class="[
      'd-flex',
      'justify-center',
      'align-center',
      inverted ? 'bg-canvas' : 'bg-divider2',
    ]"
    :height="height"
  >
    <div class="d-flex" style="width: 900px">
      <v-col
        v-if="inverted"
        class="d-flex flex-column align-center justify-center"
      >
        <v-img :src="imageSrc" :width="imageWidth" :height="imageHeight" />
      </v-col>
      <v-col style="max-width: 400px">
        <v-row class="text-subtitle-1 text-semibold">
          {{ title }}
        </v-row>
        <v-row class="text-body-1 pt-6">
          {{ content }}
        </v-row>
        <v-row v-if="additionalContent" class="my-4" />
        <v-row v-if="additionalContent">
          {{ additionalContent }}
        </v-row>
      </v-col>
      <v-col
        v-if="!inverted"
        class="d-flex flex-column align-center justify-center"
      >
        <v-img :src="imageSrc" :width="imageWidth" :height="imageHeight" />
      </v-col>
    </div>
  </v-container>
</template>
