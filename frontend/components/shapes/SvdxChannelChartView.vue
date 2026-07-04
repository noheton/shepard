<script setup lang="ts">
/**
 * SvdxChannelChartView — catalogue view for the SvdxChannelChartShape VIEW_RECIPE.
 *
 * The backend SvdxChannelChartRenderer (plugins/fileformat-svdx) returns a
 * channelBindings array where each entry's channelSelector is a JSON object:
 *   { channelName?, symbolName?, dataType?, amsNetId?, port?, manifest? }
 *
 * This component groups bindings by dataType (the default svdx-ui:groupBy knob),
 * renders a per-channel catalogue table, and highlights MISSING bindings.
 *
 * No timeseries data fetch is needed — this is a pure manifest catalogue view.
 * The resolved.channelRef (FileReference appId for the .svdx file) is
 * available in the raw binding but not needed here.
 *
 * Backlog: SVDX-CHANNEL-CHART-VUE-2026-06-29 (aidocs/16-dispatcher-backlog.md).
 */
import {
  parseSvdxSelector,
  groupSvdxBindingsByDataType,
  extractManifest,
  type SvdxBindingRow,
  type SvdxManifestInfo,
} from "~/utils/svdxChannelChart";

interface RawBinding {
  role: string;
  status: string;
  channelSelector: string;
  unit: string | null;
  required: boolean;
}

const props = defineProps<{
  bindings: RawBinding[];
}>();

const rows = computed<SvdxBindingRow[]>(() =>
  props.bindings.map(b => ({
    role: b.role,
    status: b.status,
    selector: parseSvdxSelector(b.channelSelector),
  })),
);

const manifest = computed<SvdxManifestInfo | null>(() => extractManifest(rows.value));

const groups = computed(() => {
  const m = groupSvdxBindingsByDataType(rows.value);
  // REAL32 first (most common TwinCAT float type), then alphabetical
  return [...m.entries()].sort(([a], [b]) => {
    if (a === "REAL32") return -1;
    if (b === "REAL32") return 1;
    return a.localeCompare(b);
  });
});

const channelCount = computed(() => rows.value.filter(r => r.role.startsWith("channel-")).length);
const acquisitionCount = computed(() => rows.value.filter(r => r.role.startsWith("acquisition-")).length);
const missingCount = computed(() => rows.value.filter(r => r.status === "MISSING").length);
const okCount = computed(() => rows.value.filter(r => r.status === "OK").length);
</script>

<template>
  <v-card variant="outlined" data-testid="svdx-channel-chart-view">
    <!-- ── header ── -->
    <v-card-title class="d-flex align-center ga-2 flex-wrap py-3">
      <v-icon size="small" color="primary">mdi-waveform</v-icon>
      <span>SVDX Channel Catalogue</span>
      <v-chip v-if="channelCount > 0" size="x-small" variant="tonal" color="primary">
        {{ channelCount }} channels
      </v-chip>
      <v-chip v-if="acquisitionCount > 0" size="x-small" variant="tonal" color="secondary">
        {{ acquisitionCount }} acquisitions
      </v-chip>
      <v-chip v-if="missingCount > 0" size="x-small" variant="tonal" color="warning">
        {{ missingCount }} missing
      </v-chip>
      <v-chip v-if="okCount > 0" size="x-small" variant="tonal" color="success">
        {{ okCount }} resolved
      </v-chip>
    </v-card-title>

    <!-- ── manifest summary ── -->
    <v-card-text v-if="manifest" class="pt-0 pb-2">
      <div class="d-flex flex-wrap ga-2">
        <v-chip
          v-if="manifest.projectName"
          size="x-small"
          prepend-icon="mdi-folder-outline"
          variant="outlined"
        >
          {{ manifest.projectName }}
        </v-chip>
        <v-chip
          v-if="manifest.channelCount"
          size="x-small"
          prepend-icon="mdi-chart-line"
          variant="outlined"
        >
          {{ manifest.channelCount }} ch
        </v-chip>
        <v-chip
          v-if="manifest.acquisitionCount"
          size="x-small"
          prepend-icon="mdi-record-circle-outline"
          variant="outlined"
        >
          {{ manifest.acquisitionCount }} acq
        </v-chip>
        <v-chip
          v-if="manifest.amsNetIds"
          size="x-small"
          prepend-icon="mdi-lan"
          variant="outlined"
        >
          AMS {{ manifest.amsNetIds }}
        </v-chip>
        <v-chip
          v-if="manifest.ports"
          size="x-small"
          prepend-icon="mdi-ethernet"
          variant="outlined"
        >
          :{{ manifest.ports }}
        </v-chip>
        <v-chip
          v-if="manifest.dataTypes"
          size="x-small"
          prepend-icon="mdi-code-braces"
          variant="outlined"
        >
          {{ manifest.dataTypes }}
        </v-chip>
      </div>
    </v-card-text>

    <v-divider v-if="manifest" />

    <!-- ── per-dataType groups ── -->
    <v-card-text class="pt-2">
      <template v-for="[dataType, groupRows] in groups" :key="dataType">
        <div class="d-flex align-center ga-2 mb-1 mt-2">
          <v-chip size="x-small" color="primary" variant="tonal" label>
            {{ dataType }}
          </v-chip>
          <span class="text-caption text-medium-emphasis">{{ groupRows.length }} entries</span>
        </div>

        <v-table density="compact" class="mb-3" data-testid="svdx-channel-table">
          <thead>
            <tr>
              <th class="text-caption">Role</th>
              <th class="text-caption">Channel / Symbol</th>
              <th class="text-caption">AMS Net ID</th>
              <th class="text-caption">Port</th>
              <th class="text-caption">Status</th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="row in groupRows"
              :key="row.role"
              :data-testid="`svdx-row-${row.role}`"
            >
              <td><code class="text-caption">{{ row.role }}</code></td>
              <td class="text-caption">
                <span v-if="row.selector?.channelName">{{ row.selector.channelName }}</span>
                <span v-else-if="row.selector?.symbolName" class="text-medium-emphasis font-italic">
                  {{ row.selector.symbolName }}
                </span>
                <span v-else class="text-disabled">—</span>
              </td>
              <td class="text-caption" style="font-family: monospace">
                {{ row.selector?.amsNetId ?? "—" }}
              </td>
              <td class="text-caption" style="font-family: monospace">
                {{ row.selector?.port ?? "—" }}
              </td>
              <td>
                <v-chip
                  size="x-small"
                  :color="row.status === 'OK' ? 'success' : row.status === 'MISSING' ? 'warning' : 'default'"
                  variant="tonal"
                >
                  {{ row.status }}
                </v-chip>
              </td>
            </tr>
          </tbody>
        </v-table>
      </template>

      <!-- MISSING binding explanatory alert -->
      <v-alert
        v-if="missingCount > 0"
        type="warning"
        variant="tonal"
        density="compact"
        class="mt-2"
        data-testid="svdx-missing-alert"
      >
        {{ missingCount }} binding{{ missingCount !== 1 ? "s" : "" }} could not be resolved —
        the focus entity may not have an associated <code>.svdx</code> file reference.
      </v-alert>

      <v-alert
        v-if="rows.length === 0"
        type="info"
        variant="tonal"
        density="compact"
        class="mt-2"
        data-testid="svdx-empty-alert"
      >
        No channel bindings returned. Check that the template has
        <code>svdx-ui:groupBy</code> and that the focus entity carries a
        <code>.svdx</code> file reference.
      </v-alert>
    </v-card-text>
  </v-card>
</template>
