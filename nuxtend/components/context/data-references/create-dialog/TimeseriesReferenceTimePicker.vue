<script setup lang="ts">
const date = defineModel<string>("date", {
  required: true,
});
const time = defineModel<string>("time", {
  required: true,
});
const rules = {
  time: (value: string): boolean | string => {
    const timeRegex = /^([01]\d|2[0-3]):([0-5]\d):([0-5]\d)(\.\d+)?$/;
    return timeRegex.test(value) || "Invalid time format. Use HH:mm:ss.###";
  },
};
</script>
<template>
  <v-col class="pa-0 pr-2">
    <v-date-input
      v-model="date"
      label="Date"
      variant="outlined"
      append-inner-icon="mdi-calendar-edit-outline"
      prepend-icon=""
      density="compact"
      required
    />
  </v-col>

  <v-col class="pa-0">
    <v-text-field
      v-model="time"
      required
      density="compact"
      :rules="[rules.time]"
      placeholder="HH:mm:ss.###"
      label="Time"
      variant="outlined"
    />
  </v-col>
</template>
