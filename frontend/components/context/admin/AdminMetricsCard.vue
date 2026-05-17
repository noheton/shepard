<script setup lang="ts">
import { AdminMetricsApi, type AdminMetricsSummary } from "@dlr-shepard/backend-client";
import { useV2ShepardApi } from "~/composables/common/api/useV2ShepardApi";

const metrics = ref<AdminMetricsSummary | null>(null);
const isLoading = ref(true);
const forbidden = ref(false);

async function load() {
  isLoading.value = true;
  forbidden.value = false;
  try {
    metrics.value = await useV2ShepardApi(AdminMetricsApi).value.getMetricsSummary();
  } catch (error: unknown) {
    const status = (error as { response?: { status?: number } })?.response?.status;
    if (status === 403 || status === 401) {
      forbidden.value = true;
    } else {
      handleError(error, "loading instance metrics");
    }
  } finally {
    isLoading.value = false;
  }
}

load();

function formatBytes(bytes: number): string {
  const mb = bytes / (1024 * 1024);
  if (mb >= 1024) return `${(mb / 1024).toFixed(1)} GB`;
  return `${mb.toFixed(0)} MB`;
}

function formatUptime(millis: number): string {
  const totalSeconds = Math.floor(millis / 1000);
  const days = Math.floor(totalSeconds / 86400);
  const hours = Math.floor((totalSeconds % 86400) / 3600);
  const minutes = Math.floor((totalSeconds % 3600) / 60);
  const parts: string[] = [];
  if (days > 0) parts.push(`${days}d`);
  if (hours > 0) parts.push(`${hours}h`);
  parts.push(`${minutes}m`);
  return parts.join(' ');
}

function formatCacheRatio(ratio: number | null): string {
  if (ratio === null) return '—';
  return `${(ratio * 100).toFixed(1)}%`;
}

function heapPercent(m: AdminMetricsSummary): number {
  if (m.jvmHeapMaxBytes <= 0) return 0;
  return Math.round((m.jvmHeapUsedBytes / m.jvmHeapMaxBytes) * 100);
}

function heapColor(m: AdminMetricsSummary): string {
  const pct = heapPercent(m);
  if (pct >= 85) return 'error';
  if (pct >= 65) return 'warning';
  return 'success';
}
</script>

<template>
  <div v-if="!forbidden" class="d-flex flex-column ga-4">
    <h4 class="text-h4">Instance Health</h4>

    <centered-loading-spinner v-if="isLoading" />

    <v-card v-else-if="metrics" variant="outlined">
      <v-card-text>
        <v-row dense>
          <!-- JVM Heap -->
          <v-col cols="12" sm="6">
            <div class="text-caption text-medium-emphasis mb-1">JVM Heap</div>
            <div class="d-flex align-center ga-2">
              <v-progress-linear
                :model-value="heapPercent(metrics)"
                :color="heapColor(metrics)"
                rounded
                height="8"
                class="flex-grow-1"
              />
              <span class="text-body-2 text-no-wrap">
                {{ formatBytes(metrics.jvmHeapUsedBytes) }} / {{ formatBytes(metrics.jvmHeapMaxBytes) }}
              </span>
            </div>
          </v-col>

          <!-- Uptime -->
          <v-col cols="12" sm="6">
            <div class="text-caption text-medium-emphasis mb-1">Uptime</div>
            <v-chip color="primary" variant="tonal" size="small">
              <v-icon start icon="mdi-clock-outline" />
              {{ formatUptime(metrics.uptimeMillis) }}
            </v-chip>
          </v-col>

          <!-- HTTP Requests -->
          <v-col cols="12" sm="6">
            <div class="text-caption text-medium-emphasis mb-1">HTTP Requests (since start)</div>
            <div class="d-flex align-center ga-2 flex-wrap">
              <v-chip color="secondary" variant="tonal" size="small">
                {{ metrics.httpRequestsTotal.toLocaleString() }} total
              </v-chip>
              <v-chip
                v-if="metrics.httpMeanRequestMillis !== null"
                color="secondary"
                variant="tonal"
                size="small"
              >
                {{ metrics.httpMeanRequestMillis.toFixed(1) }} ms avg
              </v-chip>
            </div>
          </v-col>

          <!-- Permissions Cache -->
          <v-col cols="12" sm="6">
            <div class="text-caption text-medium-emphasis mb-1">Permissions Cache</div>
            <div class="d-flex align-center ga-2 flex-wrap">
              <v-chip
                :color="metrics.permissionsCacheHitRatio !== null && metrics.permissionsCacheHitRatio >= 0.8 ? 'success' : 'warning'"
                variant="tonal"
                size="small"
              >
                {{ formatCacheRatio(metrics.permissionsCacheHitRatio) }} hit ratio
              </v-chip>
              <v-chip variant="tonal" size="small" color="secondary">
                {{ metrics.permissionsCacheHits.toLocaleString() }} hits
              </v-chip>
              <v-chip variant="tonal" size="small" color="secondary">
                {{ metrics.permissionsCacheMisses.toLocaleString() }} misses
              </v-chip>
            </div>
          </v-col>
        </v-row>
      </v-card-text>
      <v-card-actions>
        <v-btn
          size="small"
          variant="text"
          prepend-icon="mdi-refresh"
          :loading="isLoading"
          @click="load"
        >
          Refresh
        </v-btn>
      </v-card-actions>
    </v-card>
  </div>
</template>

<style scoped lang="scss"></style>
