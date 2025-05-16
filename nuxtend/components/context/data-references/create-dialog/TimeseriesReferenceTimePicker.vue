<script setup lang="ts">
import { vMaska } from "maska/vue";

const date = defineModel<string>("date", {
  required: true,
});
const time = defineModel<string>("time", {
  required: true,
});

const showDatePicker = ref(false);
const datePicker = ref<string>();
const dateInput = ref<string>();
const timeInput = ref<string>();

const rules = {
  time: (value: string): boolean | string => {
    const timeRegex = /^([01]\d|2[0-3]):([0-5]\d):([0-5]\d)(\.\d+)?$/;
    return timeRegex.test(value) || "Invalid time format. Use HH:mm:ss.msec";
  },
  date: (value: string): boolean | string => {
    const dateRegex = /^\d{4}-\d{2}-\d{2}$/;
    if (!dateRegex.test(value)) {
      return "Invalid date format. Use YYYY-MM-DD";
    }

    const date = new Date(value);
    const [year, month, day] = value.split("-").map(Number);

    if (
      isNaN(date.getTime()) ||
      date.getFullYear() !== year ||
      date.getMonth() + 1 !== month ||
      date.getDate() !== day
    ) {
      return "Invalid calendar date";
    }

    return true;
  },
};
watch(dateInput, () => {
  if (dateInput.value) {
    datePicker.value = dateInput.value;
    if (rules.date(dateInput.value)) date.value = dateInput.value;
  }
});
watch(timeInput, () => {
  if (timeInput.value && rules.time(timeInput.value))
    time.value = timeInput.value;
});

function formatDate(value: string) {
  try {
    const dateObj = new Date(value);
    const year = dateObj.getFullYear();
    const month = String(dateObj.getMonth() + 1).padStart(2, "0");
    const day = String(dateObj.getDate()).padStart(2, "0");
    return `${year}-${month}-${day}`;
  } catch {
    return "";
  }
}

function handleDateSelection(selectedDate: string) {
  dateInput.value = formatDate(selectedDate);
  showDatePicker.value = false;
}
function handleDateBlur() {
  nextTick(() => {
    showDatePicker.value = false;
  });
}
</script>
<template>
  <v-col class="pa-0 pr-2">
    <v-text-field
      v-model="dateInput"
      v-maska="'####-##-##'"
      label="Date"
      variant="outlined"
      bg-color="canvas"
      placeholder="YYYY-MM-DD"
      append-inner-icon="mdi-calendar-edit-outline"
      prepend-icon=""
      density="compact"
      :rules="[rules.date]"
      required
      @keydown.enter="showDatePicker = false"
      @blur="handleDateBlur"
    >
      <v-menu
        v-model="showDatePicker"
        :close-on-content-click="false"
        transition="scale-transition"
        min-width="auto"
        activator="parent"
      >
        <v-date-picker
          :model-value="datePicker"
          @update:model-value="handleDateSelection"
        />
      </v-menu>
    </v-text-field>
  </v-col>
  <v-col class="pa-0">
    <v-text-field
      v-model="timeInput"
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
