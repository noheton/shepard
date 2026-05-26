<script setup lang="ts">
/**
 * TPL11 — IndependenceBadge
 *
 * Displays a green "Independent ✓" or red "Not independent ✗" badge for two
 * DataObject sets after calling POST /v2/quality/independence-proof.
 *
 * States:
 *   - Loading:   spinner while the endpoint is in-flight
 *   - Independent:   green "Independent ✓" chip with tooltip "No shared provenance or annotations"
 *   - Not independent: red "Not independent ✗" chip with tooltip listing shared ancestor appIds
 *     and shared annotation key/values
 *   - Error:     grey "?" chip with tooltip showing the error message
 *
 * Props:
 *   - setA: string[]  — DataObject appIds of set A
 *   - setB: string[]  — DataObject appIds of set B
 *
 * Emits nothing — this is a read-only display component.
 */
import { IndependenceProofApi } from "@dlr-shepard/backend-client";
import { useV2ShepardApi } from "~/composables/common/api/useV2ShepardApi";

const props = defineProps<{
  /** AppIds of DataObjects in set A. */
  setA: string[];
  /** AppIds of DataObjects in set B. */
  setB: string[];
}>();

type State = "loading" | "independent" | "not-independent" | "error";

const state = ref<State>("loading");
const errorMessage = ref<string | null>(null);

interface SharedAncestor {
  ancestorAppId: string;
  reachableFromA: string[];
  reachableFromB: string[];
}
interface SharedAnnotation {
  key: string;
  value: string;
  fromA: string[];
  fromB: string[];
}

const sharedAncestors = ref<SharedAncestor[]>([]);
const sharedAnnotations = ref<SharedAnnotation[]>([]);

const api = useV2ShepardApi(IndependenceProofApi);

async function runCheck() {
  state.value = "loading";
  errorMessage.value = null;
  sharedAncestors.value = [];
  sharedAnnotations.value = [];

  try {
    const result = await api.value.checkIndependence({
      setA: props.setA,
      setB: props.setB,
    });
    if (result.independent) {
      state.value = "independent";
    } else {
      state.value = "not-independent";
      sharedAncestors.value = result.sharedAncestors ?? [];
      sharedAnnotations.value = result.sharedAnnotations ?? [];
    }
  } catch (err: unknown) {
    state.value = "error";
    errorMessage.value =
      err instanceof Error ? err.message : "Unknown error calling independence-proof endpoint";
  }
}

// Run on mount and whenever the sets change
onMounted(runCheck);
watch([() => props.setA, () => props.setB], runCheck, { deep: true });

// ── computed tooltip text ──────────────────────────────────────────────────

const tooltipText = computed<string>(() => {
  if (state.value === "loading") return "Checking independence…";
  if (state.value === "error") return `Error: ${errorMessage.value}`;
  if (state.value === "independent") return "No shared provenance ancestors or annotation key-value pairs found.";

  // Not independent: list the first few overlapping items
  const lines: string[] = [];
  if (sharedAncestors.value.length > 0) {
    lines.push(`Shared ancestors (${sharedAncestors.value.length}):`);
    sharedAncestors.value.slice(0, 3).forEach(a => lines.push(`  • ${a.ancestorAppId}`));
    if (sharedAncestors.value.length > 3) lines.push(`  … and ${sharedAncestors.value.length - 3} more`);
  }
  if (sharedAnnotations.value.length > 0) {
    lines.push(`Shared annotations (${sharedAnnotations.value.length}):`);
    sharedAnnotations.value.slice(0, 3).forEach(a => lines.push(`  • ${a.key}=${a.value}`));
    if (sharedAnnotations.value.length > 3) lines.push(`  … and ${sharedAnnotations.value.length - 3} more`);
  }
  return lines.join("\n");
});
</script>

<template>
  <v-tooltip :text="tooltipText" location="bottom" max-width="400">
    <template #activator="{ props: tooltipProps }">
      <!-- Loading state -->
      <v-chip
        v-if="state === 'loading'"
        v-bind="tooltipProps"
        size="small"
        variant="tonal"
        color="grey"
      >
        <v-progress-circular indeterminate size="14" width="2" class="mr-1" />
        Checking…
      </v-chip>

      <!-- Independent -->
      <v-chip
        v-else-if="state === 'independent'"
        v-bind="tooltipProps"
        size="small"
        variant="tonal"
        color="success"
        prepend-icon="mdi-check-circle-outline"
        label
      >
        Independent
      </v-chip>

      <!-- Not independent -->
      <v-chip
        v-else-if="state === 'not-independent'"
        v-bind="tooltipProps"
        size="small"
        variant="tonal"
        color="error"
        prepend-icon="mdi-alert-circle-outline"
        label
      >
        Not independent
      </v-chip>

      <!-- Error -->
      <v-chip
        v-else
        v-bind="tooltipProps"
        size="small"
        variant="tonal"
        color="grey"
        prepend-icon="mdi-help-circle-outline"
        label
      >
        ?
      </v-chip>
    </template>
  </v-tooltip>
</template>
