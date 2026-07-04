<script setup lang="ts">
/**
 * AfpThermoOverlayCanvas — MFFD-RENDER-AFP-THERMO-OVERLAY slice 3.
 *
 * Real synced dual-pane canvas for the AFP Thermo Overlay VIEW envelope.
 * Replaces AfpThermoOverlayPlaceholder.vue (slice 1 stub).
 *
 * Consumes the VIEW envelope produced by AfpThermoOverlayTransformExecutor
 * (slice 2) via {@code POST /v2/mappings/{templateAppId}/materialize}:
 *   - Left pane: AFP robot-head course metadata — tile coordinates, ply/course
 *     IDs, process setpoints, TCP timeseries reference link.
 *   - Right pane: NDT inspection tile metadata — OTvis source file link,
 *     spatial coordinates (section/module/layer/frame).
 *   - Header: tile-match verdict chip (MATCHED / MISMATCHED / UNVERIFIED)
 *     with colour-coding and a DataObject link for each side.
 *
 * Phase 1 (this slice): pure metadata display + DataObject links.
 * Phase 2 (future): embed Trace3DCanvas for AFP trajectory + ThermographyCanvas
 *   for NDT heatmap in the same mount, driven by their respective appIds.
 *   Blocked on: TCP timeseries live-fetch composable (TCP channel → TracePoint[])
 *   and OTvis frame rendering (OTVIS-PARSE-2 / OTVIS-VIEW-1 tier-2).
 *
 * Design: plugins/vis-afp-thermo-overlay/docs/reference.md
 * Backlog: MFFD-RENDER-AFP-THERMO-OVERLAY (aidocs/16-dispatcher-backlog.md)
 */
import {
  tileMatchColor,
  tileMatchLabel,
  tileMatchIcon,
  formatTempSetpoint,
  formatSpeedSetpoint,
  formatTimeWindow,
  lastIriSegment,
  type AfpThermoOverlayEnvelope,
} from "~/utils/afpThermoOverlay";

const props = defineProps<{
  /** VIEW envelope produced by AfpThermoOverlayTransformExecutor. */
  envelope: AfpThermoOverlayEnvelope;
  /**
   * Optional: collectionId for building DataObject deep-link hrefs.
   * When absent, DataObject links show the appId only with no navigation.
   */
  collectionAppId?: string;
}>();

// ── computed helpers ──────────────────────────────────────────────────────

const matchColor = computed(() => tileMatchColor(props.envelope.tileMatch));
const matchLabel = computed(() => tileMatchLabel(props.envelope.tileMatch));
const matchIcon  = computed(() => tileMatchIcon(props.envelope.tileMatch));

const tileCoords = computed(() => {
  const s = props.envelope.section;
  const m = props.envelope.module;
  if (s && m) return `Section ${s} · Module ${m}`;
  if (s) return `Section ${s}`;
  if (m) return `Module ${m}`;
  return "—";
});

const afpDoHref = computed(() => {
  if (!props.collectionAppId) return null;
  return `/collections/${props.collectionAppId}/dataobjects/${props.envelope.afp.dataObjectAppId}`;
});

const ndtDoHref = computed(() => {
  if (!props.collectionAppId) return null;
  return `/collections/${props.collectionAppId}/dataobjects/${props.envelope.ndt.dataObjectAppId}`;
});

const timeWindowText = computed(() =>
  formatTimeWindow(
    props.envelope.afp.timeWindowStartUs,
    props.envelope.afp.timeWindowEndUs,
  ),
);

const materialBatchLabel = computed(() =>
  lastIriSegment(props.envelope.afp.materialBatchIri),
);
</script>

<template>
  <v-card variant="outlined" data-testid="afp-thermo-overlay-canvas">
    <!-- ── Header ──────────────────────────────────────────────────────── -->
    <v-card-title class="d-flex align-center ga-2 flex-wrap py-3">
      <v-icon size="small" color="primary">mdi-layers-triple-outline</v-icon>
      <span>AFP Thermo Overlay</span>

      <!-- tile coordinates -->
      <v-chip
        v-if="envelope.section || envelope.module"
        size="x-small"
        variant="tonal"
        color="secondary"
      >
        {{ tileCoords }}
      </v-chip>

      <v-spacer />

      <!-- tile-match verdict -->
      <v-chip
        :color="matchColor"
        :prepend-icon="matchIcon"
        size="small"
        variant="tonal"
        data-testid="tile-match-chip"
      >
        {{ matchLabel }}
      </v-chip>
    </v-card-title>

    <v-divider />

    <v-card-text class="pa-4">
      <v-row dense>
        <!-- ── LEFT: AFP course pane ───────────────────────────────────── -->
        <v-col cols="12" md="6">
          <v-card variant="tonal" color="blue-grey" class="pa-3 h-100">
            <div class="d-flex align-center ga-2 mb-3">
              <v-icon size="small" color="blue-lighten-1">mdi-robot-industrial</v-icon>
              <span class="text-subtitle-2">AFP Robot Course</span>
              <v-spacer />
              <v-btn
                v-if="afpDoHref"
                :to="afpDoHref"
                size="x-small"
                variant="text"
                :append-icon="'mdi-open-in-new'"
                data-testid="afp-do-link"
              >
                Open DataObject
              </v-btn>
            </div>

            <v-list density="compact" class="bg-transparent pa-0">
              <v-list-item class="pa-0 mb-1">
                <template #title>
                  <span class="text-caption text-medium-emphasis">DataObject</span>
                </template>
                <template #subtitle>
                  <code class="text-caption" data-testid="afp-do-appid">{{ envelope.afp.dataObjectAppId }}</code>
                </template>
              </v-list-item>

              <v-list-item v-if="envelope.afp.plyId" class="pa-0 mb-1">
                <template #title>
                  <span class="text-caption text-medium-emphasis">Ply ID</span>
                </template>
                <template #subtitle>
                  <span class="text-body-2" data-testid="afp-ply-id">{{ envelope.afp.plyId }}</span>
                </template>
              </v-list-item>

              <v-list-item v-if="envelope.afp.courseId" class="pa-0 mb-1">
                <template #title>
                  <span class="text-caption text-medium-emphasis">Course ID</span>
                </template>
                <template #subtitle>
                  <span class="text-body-2" data-testid="afp-course-id">{{ envelope.afp.courseId }}</span>
                </template>
              </v-list-item>

              <v-list-item class="pa-0 mb-1">
                <template #title>
                  <span class="text-caption text-medium-emphasis">Laser temp setpoint</span>
                </template>
                <template #subtitle>
                  <span class="text-body-2" data-testid="afp-laser-temp">{{ formatTempSetpoint(envelope.afp.laserTempSetpointC) }}</span>
                </template>
              </v-list-item>

              <v-list-item class="pa-0 mb-1">
                <template #title>
                  <span class="text-caption text-medium-emphasis">Tape speed setpoint</span>
                </template>
                <template #subtitle>
                  <span class="text-body-2" data-testid="afp-tape-speed">{{ formatSpeedSetpoint(envelope.afp.tapeSpeedSetpointMpm) }}</span>
                </template>
              </v-list-item>

              <v-list-item v-if="materialBatchLabel !== '—'" class="pa-0 mb-1">
                <template #title>
                  <span class="text-caption text-medium-emphasis">Material batch</span>
                </template>
                <template #subtitle>
                  <span class="text-body-2" data-testid="afp-material-batch">{{ materialBatchLabel }}</span>
                </template>
              </v-list-item>

              <v-list-item v-if="timeWindowText" class="pa-0 mb-1">
                <template #title>
                  <span class="text-caption text-medium-emphasis">Time window</span>
                </template>
                <template #subtitle>
                  <span class="text-body-2">{{ timeWindowText }}</span>
                </template>
              </v-list-item>

              <v-list-item v-if="envelope.afp.tcpTimeseriesRefAppId" class="pa-0 mb-1">
                <template #title>
                  <span class="text-caption text-medium-emphasis">TCP channel</span>
                </template>
                <template #subtitle>
                  <span class="text-body-2">
                    {{ envelope.tcpChannel }}
                    <span class="text-medium-emphasis text-caption ml-1">(Trace3D view in phase 2)</span>
                  </span>
                </template>
              </v-list-item>
            </v-list>

            <!-- phase-2 placeholder for Trace3DCanvas -->
            <v-alert
              v-if="envelope.afp.tcpTimeseriesRefAppId"
              class="mt-3"
              type="info"
              variant="tonal"
              density="compact"
              icon="mdi-axis-arrow"
            >
              3D trajectory viewer (Trace3D) arrives in phase 2 when
              TCP timeseries live-fetch composable ships.
            </v-alert>
          </v-card>
        </v-col>

        <!-- ── RIGHT: NDT inspection pane ─────────────────────────────── -->
        <v-col cols="12" md="6">
          <v-card variant="tonal" color="deep-purple-darken-4" class="pa-3 h-100">
            <div class="d-flex align-center ga-2 mb-3">
              <v-icon size="small" color="purple-lighten-2">mdi-thermometer-lines</v-icon>
              <span class="text-subtitle-2">NDT Inspection (OTvis)</span>
              <v-spacer />
              <v-btn
                v-if="ndtDoHref"
                :to="ndtDoHref"
                size="x-small"
                variant="text"
                :append-icon="'mdi-open-in-new'"
                data-testid="ndt-do-link"
              >
                Open DataObject
              </v-btn>
            </div>

            <v-list density="compact" class="bg-transparent pa-0">
              <v-list-item class="pa-0 mb-1">
                <template #title>
                  <span class="text-caption text-medium-emphasis">DataObject</span>
                </template>
                <template #subtitle>
                  <code class="text-caption" data-testid="ndt-do-appid">{{ envelope.ndt.dataObjectAppId }}</code>
                </template>
              </v-list-item>

              <v-list-item v-if="envelope.ndt.section" class="pa-0 mb-1">
                <template #title>
                  <span class="text-caption text-medium-emphasis">Section</span>
                </template>
                <template #subtitle>
                  <span class="text-body-2" data-testid="ndt-section">{{ envelope.ndt.section }}</span>
                </template>
              </v-list-item>

              <v-list-item v-if="envelope.ndt.module" class="pa-0 mb-1">
                <template #title>
                  <span class="text-caption text-medium-emphasis">Module</span>
                </template>
                <template #subtitle>
                  <span class="text-body-2" data-testid="ndt-module">{{ envelope.ndt.module }}</span>
                </template>
              </v-list-item>

              <v-list-item v-if="envelope.ndt.layer" class="pa-0 mb-1">
                <template #title>
                  <span class="text-caption text-medium-emphasis">Layer</span>
                </template>
                <template #subtitle>
                  <span class="text-body-2" data-testid="ndt-layer">{{ envelope.ndt.layer }}</span>
                </template>
              </v-list-item>

              <v-list-item v-if="envelope.ndt.frame" class="pa-0 mb-1">
                <template #title>
                  <span class="text-caption text-medium-emphasis">Frame</span>
                </template>
                <template #subtitle>
                  <span class="text-body-2" data-testid="ndt-frame">{{ envelope.ndt.frame }}</span>
                </template>
              </v-list-item>

              <v-list-item v-if="envelope.ndt.sourceFileRefAppId" class="pa-0 mb-1">
                <template #title>
                  <span class="text-caption text-medium-emphasis">OTvis source</span>
                </template>
                <template #subtitle>
                  <code class="text-caption" data-testid="ndt-source-fileref">{{ envelope.ndt.sourceFileRefAppId }}</code>
                </template>
              </v-list-item>
            </v-list>

            <!-- phase-2 placeholder for ThermographyCanvas heatmap -->
            <v-alert
              v-if="envelope.ndt.sourceFileRefAppId"
              class="mt-3"
              type="info"
              variant="tonal"
              density="compact"
              icon="mdi-image-filter-center-focus"
            >
              OTvis heatmap viewer arrives in phase 2 (OTVIS-PARSE-2 /
              OTVIS-VIEW-1 tier-2).
            </v-alert>
          </v-card>
        </v-col>
      </v-row>

      <!-- ── Sync info row ────────────────────────────────────────────── -->
      <div class="d-flex align-center ga-3 mt-3 text-caption text-medium-emphasis">
        <v-icon size="x-small">mdi-sync</v-icon>
        <span>Sync mode: <strong>{{ envelope.syncMode }}</strong></span>
        <v-icon size="x-small" class="ml-2">mdi-palette-outline</v-icon>
        <span>Colour map: <strong>{{ envelope.colourMap }}</strong></span>
      </div>
    </v-card-text>
  </v-card>
</template>
