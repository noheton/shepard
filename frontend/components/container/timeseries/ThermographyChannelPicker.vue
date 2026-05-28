<script setup lang="ts">
/**
 * ThermographyChannelPicker — tier-1 metadata-summary panel.
 *
 * Sibling of {@link ./Trace3DChannelPicker.vue} and
 * {@link ./UrdfChannelPicker.vue}. The naming matches the family
 * convention even though tier-1 has no actual channel binding to do
 * yet — frame-extracted IR sequences (the channels) are filed as
 * OTVIS-PARSE-2, and the channel-bound playback UI is filed as
 * THERMO-CHANNELS-1.
 *
 * What this panel does today: project the `urn:shepard:thermography:*`
 * and `urn:shepard:mffd:*` annotations the OTvis parser emitted on the
 * parent DataObject + FileReference into a grouped metadata table —
 * the same shape an operator wants to see at-a-glance when they open
 * the thermography view ("what was this measurement? where on the
 * shell? what excitation?").
 *
 * Annotation-driven preselection (per
 * `project_annotation_preselection_principle.md`): in tier-2 the
 * `channels` prop will appear and channels carrying
 * `urn:shepard:thermography:role` annotations (e.g. `"mean_temp"`,
 * `"amplitude"`, `"phase"`) will auto-bind to their playback slots —
 * exactly the URDF / Trace3D pattern.
 *
 * Task: OTVIS-VIEW-1 (aidocs/16). Companion: aidocs/integrations/114.
 */
import { computed } from "vue";
import type { AnnotationMap, MetadataRow } from "~/utils/thermographyChannelPicker";
import {
  projectMetadataRows,
  groupMetadataRows,
  extractGridPosition,
  formatGridPosition,
} from "~/utils/thermographyChannelPicker";

const props = defineProps<{
  /**
   * Flattened predicate → value map. Build from the parent DataObject's
   * annotations + the FileReference's annotations so all relevant
   * fields land in one summary, regardless of subject anchoring.
   */
  annotations: AnnotationMap;
}>();

const rows = computed<MetadataRow[]>(() => projectMetadataRows(props.annotations));
const grouped = computed(() => groupMetadataRows(rows.value));
const gridLabel = computed(() => formatGridPosition(extractGridPosition(props.annotations)));
const isEmpty = computed(() => rows.value.length === 0);

const sections: { key: MetadataRow["group"]; title: string; icon: string }[] = [
  { key: "grid",        title: "MFFD grid position", icon: "mdi-grid" },
  { key: "acquisition", title: "Acquisition",        icon: "mdi-camera-iris" },
  { key: "excitation",  title: "Excitation",         icon: "mdi-flash" },
  { key: "provenance",  title: "Provenance",         icon: "mdi-information-outline" },
];
</script>

<template>
  <v-card variant="outlined" class="thermography-channel-picker">
    <v-card-title class="text-subtitle-1 d-flex align-center ga-2 py-2">
      <v-icon size="small" color="primary">mdi-thermometer-lines</v-icon>
      Thermography metadata
      <v-chip
        v-if="gridLabel !== '—'"
        size="x-small"
        color="primary"
        variant="tonal"
        class="ml-1"
      >
        {{ gridLabel }}
      </v-chip>
      <v-spacer />
      <v-chip size="x-small" color="warning" variant="tonal" title="Tier-1 only">
        tier-1
      </v-chip>
    </v-card-title>

    <v-card-text class="py-2">
      <v-alert
        v-if="isEmpty"
        type="info"
        variant="tonal"
        density="compact"
        class="mb-0"
      >
        No thermography annotations on this DataObject yet — upload an
        <code>.OTvis</code> file to enrich it.
      </v-alert>

      <template v-else>
        <div v-for="sec in sections" :key="sec.key">
          <div v-if="grouped[sec.key].length > 0" class="mb-3">
            <div class="text-overline d-flex align-center ga-1 mb-1">
              <v-icon size="x-small">{{ sec.icon }}</v-icon>
              {{ sec.title }}
            </div>
            <v-table density="compact" class="thermography-channel-picker__table">
              <tbody>
                <tr v-for="row in grouped[sec.key]" :key="row.predicate">
                  <td class="text-medium-emphasis" style="width: 45%">
                    {{ row.label }}
                  </td>
                  <td><code>{{ row.value }}</code></td>
                </tr>
              </tbody>
            </v-table>
          </div>
        </div>

        <div class="text-caption text-medium-emphasis mt-2">
          Channel-bound playback ships with tier-2 (THERMO-CHANNELS-1).
        </div>
      </template>
    </v-card-text>
  </v-card>
</template>

<style scoped>
.thermography-channel-picker__table :deep(td) {
  font-size: 0.85rem;
}
</style>
