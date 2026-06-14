<script setup lang="ts">
/**
 * NdtGridCanvas — MFFD-RENDER-NDT-GRID slice 3.
 *
 * CSS-grid mosaic renderer for the NdtGridTransformExecutor VIEW envelope
 * (materialized via POST /v2/mappings/{templateAppId}/materialize). Replaces
 * the placeholder shipped in slice 1.
 *
 * Colour modes:
 *   mean-delta-t  — normalise cell values [min,max]→[0,1], apply colormap.
 *   pass-fail     — green for OK/PASS, red for NOK/FAIL, grey for missing.
 *
 * The parent page owns the fetch; this component is pure rendering + events.
 * Backlog: MFFD-RENDER-NDT-GRID (aidocs/16-dispatcher-backlog.md).
 */
import {
  type NdtGridEnvelope,
  buildCellLookup,
  cellKey,
  cellValueRange,
  computeMeanDtColours,
  computePassFailColours,
  resolveColourMap,
} from "~/utils/ndtGridCanvas";

const props = defineProps<{
  envelope: NdtGridEnvelope;
}>();

const emit = defineEmits<{
  (e: "select", dataObjectAppId: string): void;
}>();

// ── colour computation ───────────────────────────────────────────────────────

const resolvedColourMap = computed(() => resolveColourMap(props.envelope.colourMap));

const cellLookup = computed(() => buildCellLookup(props.envelope.cells));

const cellColours = computed(() =>
  props.envelope.colourMode === "pass-fail"
    ? computePassFailColours(props.envelope.cells)
    : computeMeanDtColours(props.envelope.cells, resolvedColourMap.value),
);

function colourFor(row: string, col: string): string {
  return cellColours.value.get(cellKey(row, col)) ?? "#e0e0e0";
}

function cellAt(row: string, col: string) {
  return cellLookup.value.get(cellKey(row, col));
}

const valueRange = computed(() => cellValueRange(props.envelope.cells));

// ── tooltip ──────────────────────────────────────────────────────────────────

const tooltip = ref<{
  visible: boolean;
  x: number;
  y: number;
  row: string;
  col: string;
  value?: number;
  quality?: string;
  hasData: boolean;
}>({ visible: false, x: 0, y: 0, row: "", col: "", hasData: false });

function onCellEnter(row: string, col: string, ev: MouseEvent) {
  const cell = cellAt(row, col);
  tooltip.value = {
    visible: true,
    x: ev.clientX + 14,
    y: ev.clientY + 14,
    row,
    col,
    value: cell?.value,
    quality: cell?.quality,
    hasData: !!cell,
  };
}

function onCellMove(ev: MouseEvent) {
  tooltip.value.x = ev.clientX + 14;
  tooltip.value.y = ev.clientY + 14;
}

function onCellLeave() {
  tooltip.value.visible = false;
}

function onCellClick(row: string, col: string) {
  const cell = cellAt(row, col);
  if (cell?.dataObjectAppId) emit("select", cell.dataObjectAppId);
}

// ── grid sizing ──────────────────────────────────────────────────────────────

const colCount = computed(() => props.envelope.columns.length);
const rowCount = computed(() => props.envelope.rows.length);
</script>

<template>
  <v-card variant="outlined" data-test="ndt-grid-canvas">
    <v-card-title class="d-flex align-center ga-2 flex-wrap py-3">
      <v-icon size="small" color="primary">mdi-grid</v-icon>
      <span>NDT Grid Mosaic</span>
      <v-chip size="x-small" variant="tonal" color="secondary">
        {{ envelope.rowDimension }} × {{ envelope.columnDimension }}
      </v-chip>
      <v-chip size="x-small" variant="tonal">{{ envelope.cells.length }} cells</v-chip>
      <v-spacer />
      <v-chip
        v-if="envelope.colourMode === 'pass-fail'"
        size="x-small"
        color="success"
        variant="flat"
      >
        Pass / Fail
      </v-chip>
      <v-chip v-else size="x-small" color="primary" variant="flat">Mean ΔT</v-chip>
    </v-card-title>

    <v-card-text>
      <div class="ndt-grid-wrapper">
        <div
          class="ndt-grid"
          :style="`--ndt-cols: ${colCount}; --ndt-rows: ${rowCount}`"
          data-test="ndt-grid"
        >
          <!-- column header row -->
          <div class="ndt-corner" />
          <div
            v-for="col in envelope.columns"
            :key="`hdr-${col}`"
            class="ndt-col-label text-caption text-medium-emphasis"
            :title="col"
          >
            {{ col }}
          </div>

          <!-- data rows -->
          <template v-for="row in envelope.rows" :key="`row-${row}`">
            <div class="ndt-row-label text-caption text-medium-emphasis" :title="row">
              {{ row }}
            </div>
            <button
              v-for="col in envelope.columns"
              :key="`cell-${row}-${col}`"
              class="ndt-cell"
              :class="{ 'ndt-cell--empty': !cellAt(row, col), 'ndt-cell--has-data': !!cellAt(row, col) }"
              :style="{ background: colourFor(row, col) }"
              :aria-label="`${row} · ${col}${cellAt(row, col) ? '' : ' (no data)'}`"
              :data-row="row"
              :data-col="col"
              @mouseenter="onCellEnter(row, col, $event)"
              @mousemove="onCellMove"
              @mouseleave="onCellLeave"
              @click="onCellClick(row, col)"
            />
          </template>
        </div>
      </div>

      <!-- mean-delta-t legend -->
      <div
        v-if="envelope.colourMode !== 'pass-fail' && valueRange"
        class="ndt-legend d-flex align-center ga-2 mt-3"
        data-test="ndt-legend-meanDt"
      >
        <span class="text-caption text-medium-emphasis">Low {{ valueRange.min.toFixed(2) }}</span>
        <div class="ndt-legend-bar" />
        <span class="text-caption text-medium-emphasis">{{ valueRange.max.toFixed(2) }} High (°C)</span>
      </div>

      <!-- pass-fail legend -->
      <div
        v-else-if="envelope.colourMode === 'pass-fail'"
        class="d-flex align-center ga-4 mt-3"
        data-test="ndt-legend-passFail"
      >
        <span class="d-flex align-center ga-1">
          <span class="ndt-swatch" style="background:#4caf50" />
          <span class="text-caption">OK / PASS</span>
        </span>
        <span class="d-flex align-center ga-1">
          <span class="ndt-swatch" style="background:#f44336" />
          <span class="text-caption">NOK / FAIL</span>
        </span>
        <span class="d-flex align-center ga-1">
          <span class="ndt-swatch" style="background:#9e9e9e" />
          <span class="text-caption">No data</span>
        </span>
      </div>
    </v-card-text>
  </v-card>

  <!-- tooltip teleported to body so it overflows card boundaries -->
  <Teleport to="body">
    <div
      v-if="tooltip.visible"
      class="ndt-tooltip"
      :style="{ left: `${tooltip.x}px`, top: `${tooltip.y}px` }"
      data-test="ndt-tooltip"
    >
      <div class="font-weight-bold" style="font-size:11px">
        {{ tooltip.row }} · {{ tooltip.col }}
      </div>
      <div v-if="tooltip.value !== undefined" style="font-size:11px">
        ΔT {{ tooltip.value.toFixed(3) }} °C
      </div>
      <div v-if="tooltip.quality" style="font-size:11px">
        Quality: {{ tooltip.quality }}
      </div>
      <div v-if="!tooltip.hasData" class="text-medium-emphasis" style="font-size:10px">
        No measurement
      </div>
    </div>
  </Teleport>
</template>

<style lang="scss" scoped>
.ndt-grid-wrapper {
  overflow-x: auto;
}

.ndt-grid {
  display: grid;
  grid-template-columns: 44px repeat(var(--ndt-cols), minmax(24px, 1fr));
  grid-auto-rows: 26px;
  gap: 2px;
  min-width: 160px;
  user-select: none;
}

.ndt-corner { /* top-left spacer */ }

.ndt-col-label {
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 9px !important;
  line-height: 1.1;
  text-align: center;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
  cursor: default;
  padding: 0 1px;
}

.ndt-row-label {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  padding-right: 4px;
  font-size: 9px !important;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  cursor: default;
}

.ndt-cell {
  border: 1px solid rgba(0, 0, 0, 0.08);
  border-radius: 2px;
  padding: 0;
  cursor: default;

  &--has-data {
    cursor: pointer;

    &:hover {
      filter: brightness(1.18) saturate(1.1);
      outline: 2px solid rgba(255, 255, 255, 0.6);
      outline-offset: -1px;
      z-index: 1;
      position: relative;
    }

    &:focus-visible {
      outline: 2px solid #1976d2;
      outline-offset: 1px;
    }
  }

  &--empty {
    background: #e8e8e8 !important;
    opacity: 0.55;
  }
}

.ndt-legend {
  .ndt-legend-bar {
    flex: 1;
    max-width: 180px;
    height: 10px;
    border-radius: 4px;
    /* inferno-flavoured gradient (dark → orange → yellow) — visual cue, not exact */
    background: linear-gradient(
      to right,
      rgb(1, 0, 14),
      rgb(87, 16, 110),
      rgb(185, 72, 43),
      rgb(245, 158, 11),
      rgb(252, 255, 164)
    );
  }
}

.ndt-swatch {
  display: inline-block;
  width: 12px;
  height: 12px;
  border-radius: 2px;
  flex-shrink: 0;
}
</style>
