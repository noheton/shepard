<script setup lang="ts">
import {
  CategoryScale,
  Chart as ChartJS,
  Legend,
  LinearScale,
  LineElement,
  PointElement,
  Title,
  Tooltip,
  type ChartData,
  type ChartOptions,
  type Point,
} from "chart.js";
import { Line } from "vue-chartjs";

ChartJS.register(
  CategoryScale,
  Legend,
  LinearScale,
  PointElement,
  LineElement,
  Title,
  Tooltip,
);

export interface LineChartProps {
  data: ChartData<"line", Point[]>;
  options: ChartOptions<"line">;
}

const props = defineProps<LineChartProps>();
</script>

<template>
  <Line id="chart" :data="props.data" :options="props.options" />
</template>
