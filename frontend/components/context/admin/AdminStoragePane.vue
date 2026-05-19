<script setup lang="ts">
import { v2BaseUrl } from "~/composables/common/api/useV2ShepardApi";
import { AdminFragments } from "./adminMenuItems";

interface TimescaleStats {
  hypertableSizeBytes: number;
  channelCount: number;
  containerCount: number;
  uncompressedChunkBytes: number;
  compressionRatio: number | null;
}

interface MongoStats {
  storageSizeBytes: number;
  dataSizeBytes: number;
  indexSizeBytes: number;
  collectionCount: number;
}

interface StorageOverview {
  timescaledb: TimescaleStats;
  mongodb: MongoStats;
}

const overview = ref<StorageOverview | null>(null);
const loading = ref(false);
const fetchError = ref(false);

async function loadOverview() {
  loading.value = true;
  fetchError.value = false;
  try {
    const { data: session } = useAuth();
    const token = session.value?.accessToken;
    if (!token) return;
    const res = await fetch(`${v2BaseUrl()}/v2/admin/storage-overview`, {
      headers: { Authorization: `Bearer ${token}` },
      credentials: "include",
    });
    if (res.ok) {
      overview.value = await res.json() as StorageOverview;
    } else {
      fetchError.value = true;
    }
  } catch {
    fetchError.value = true;
  } finally {
    loading.value = false;
  }
}

function fmtBytes(b: number): string {
  if (b === 0) return "0 B";
  if (b < 1_024) return `${b} B`;
  if (b < 1_048_576) return `${(b / 1_024).toFixed(1)} KB`;
  if (b < 1_073_741_824) return `${(b / 1_048_576).toFixed(1)} MB`;
  return `${(b / 1_073_741_824).toFixed(2)} GB`;
}

loadOverview();
</script>

<template>
  <div :id="AdminFragments.STORAGE_OVERVIEW" class="d-flex flex-column ga-4">
    <div class="d-flex align-center ga-3">
      <h4 class="text-h4">Storage Overview</h4>
      <v-btn
        icon="mdi-refresh"
        variant="text"
        size="small"
        :loading="loading"
        @click="loadOverview"
      />
    </div>

    <v-alert v-if="fetchError" type="error" variant="tonal">
      Could not load storage overview. Check that the backend is reachable and
      that you have the instance-admin role.
    </v-alert>

    <v-progress-linear v-if="loading && !overview" indeterminate />

    <template v-if="overview">
      <!-- TimescaleDB card -->
      <v-card variant="outlined">
        <v-card-title class="d-flex align-center ga-2 pt-4 pb-2">
          <v-icon color="primary">mdi-chart-timeline-variant</v-icon>
          TimescaleDB — timeseries payload storage
        </v-card-title>
        <v-card-text>
          <v-row dense>
            <v-col cols="12" sm="6" md="3">
              <div class="text-caption text-medium-emphasis">On-disk size</div>
              <div class="text-h6">{{ fmtBytes(overview.timescaledb.hypertableSizeBytes) }}</div>
              <div class="text-caption text-medium-emphasis">compressed hypertable</div>
            </v-col>
            <v-col cols="12" sm="6" md="3">
              <div class="text-caption text-medium-emphasis">Uncompressed estimate</div>
              <div class="text-h6">{{ fmtBytes(overview.timescaledb.uncompressedChunkBytes) }}</div>
              <div class="text-caption text-medium-emphasis">before-compression chunks</div>
            </v-col>
            <v-col cols="12" sm="6" md="3">
              <div class="text-caption text-medium-emphasis">Compression ratio</div>
              <div class="text-h6">
                <template v-if="overview.timescaledb.compressionRatio != null">
                  {{ overview.timescaledb.compressionRatio.toFixed(1) }}×
                </template>
                <span v-else class="text-medium-emphasis text-body-2">no compressed chunks yet</span>
              </div>
            </v-col>
            <v-col cols="12" sm="6" md="3">
              <div class="text-caption text-medium-emphasis">Channels / containers</div>
              <div class="text-h6">{{ overview.timescaledb.channelCount.toLocaleString() }}</div>
              <div class="text-caption text-medium-emphasis">
                across {{ overview.timescaledb.containerCount.toLocaleString() }} container{{ overview.timescaledb.containerCount === 1 ? "" : "s" }}
              </div>
            </v-col>
          </v-row>
        </v-card-text>
      </v-card>

      <!-- MongoDB card -->
      <v-card variant="outlined">
        <v-card-title class="d-flex align-center ga-2 pt-4 pb-2">
          <v-icon color="success">mdi-database</v-icon>
          MongoDB — files, avatars, structured data, HDF5 metadata
        </v-card-title>
        <v-card-text>
          <v-row dense>
            <v-col cols="12" sm="6" md="3">
              <div class="text-caption text-medium-emphasis">Storage size</div>
              <div class="text-h6">{{ fmtBytes(overview.mongodb.storageSizeBytes) }}</div>
              <div class="text-caption text-medium-emphasis">on-disk (incl. free space)</div>
            </v-col>
            <v-col cols="12" sm="6" md="3">
              <div class="text-caption text-medium-emphasis">Data size</div>
              <div class="text-h6">{{ fmtBytes(overview.mongodb.dataSizeBytes) }}</div>
              <div class="text-caption text-medium-emphasis">documents only</div>
            </v-col>
            <v-col cols="12" sm="6" md="3">
              <div class="text-caption text-medium-emphasis">Index size</div>
              <div class="text-h6">{{ fmtBytes(overview.mongodb.indexSizeBytes) }}</div>
            </v-col>
            <v-col cols="12" sm="6" md="3">
              <div class="text-caption text-medium-emphasis">Collections</div>
              <div class="text-h6">{{ overview.mongodb.collectionCount }}</div>
            </v-col>
          </v-row>
        </v-card-text>
      </v-card>

      <div class="text-caption text-medium-emphasis">
        TimescaleDB on-disk size reflects actual compressed storage via
        <code>hypertable_size()</code>. MongoDB storage size includes pre-allocated free
        pages; data size is the true document footprint. Neo4j disk usage is not yet
        included.
      </div>
    </template>
  </div>
</template>
