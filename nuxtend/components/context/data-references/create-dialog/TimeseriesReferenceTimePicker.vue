<script setup lang="ts">
import { vMaska } from "maska/vue";
import { useDate } from "vuetify";

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

const adapter = useDate();
function format(date: Date) {
  return adapter.toISO(date);
}
</script>
<template>
  <v-col class="pa-0 pr-2">
    <v-date-input
      v-model="date"
      v-maska="'####-##-##'"
      label="Date"
      variant="outlined"
      :display-format="format"
      placeholder="YYYY-MM-DD"
      append-inner-icon="mdi-calendar-edit-outline"
      prepend-icon=""
      density="compact"
      required
    />
  </v-col>
  <v-col class="pa-0">
    <v-text-field
      v-model="time"
      v-maska="'##:##:##.###'"
      required
      density="compact"
      :rules="[rules.time]"
      placeholder="HH:mm:ss.###"
      label="Time"
      variant="outlined"
    />
  </v-col>
</template>
