<script setup lang="ts">
import {
  useFetchLabJournalHistory,
  type LabJournalRevisionIO,
} from "~/composables/context/useFetchLabJournalHistory";

const props = defineProps<{
  /** Application-level identifier of the lab journal entry. */
  entryAppId: string;
  /** Current HTML content of the entry (used as "after" for diffs). */
  currentContent: string;
  /** Short label shown in the dialog title (e.g. the date or first line). */
  entryLabel: string;
}>();

const showDialog = defineModel<boolean>("showDialog", {
  required: true,
  default: false,
});

const { revisions, isLoading, error, load } = useFetchLabJournalHistory(
  props.entryAppId,
);

watch(showDialog, open => {
  if (open) load();
});

// ── Diff state ────────────────────────────────────────────────────────────────
const selectedRevision = ref<LabJournalRevisionIO | null>(null);

interface DiffLine {
  type: "added" | "removed" | "unchanged";
  text: string;
}

const diffLines = computed<DiffLine[]>(() => {
  if (!selectedRevision.value) return [];
  const beforeText = htmlToPlainLines(selectedRevision.value.content);
  const afterText = htmlToPlainLines(props.currentContent);
  return computeLineDiff(beforeText, afterText);
});

/**
 * Strip HTML tags and decode basic entities, then split into non-empty lines.
 * We diff the textual content, not the raw markup.
 */
function htmlToPlainLines(html: string): string[] {
  if (!html) return [];
  // Replace block-level tags with newlines so paragraphs produce separate lines.
  const withNewlines = html
    .replace(/<\/p>/gi, "\n")
    .replace(/<br\s*\/?>/gi, "\n")
    .replace(/<\/li>/gi, "\n")
    .replace(/<\/h[1-6]>/gi, "\n");
  // Strip remaining tags.
  const stripped = withNewlines.replace(/<[^>]+>/g, "");
  // Decode common HTML entities.
  const decoded = stripped
    .replace(/&amp;/g, "&")
    .replace(/&lt;/g, "<")
    .replace(/&gt;/g, ">")
    .replace(/&quot;/g, '"')
    .replace(/&#39;/g, "'")
    .replace(/&nbsp;/g, " ");
  return decoded.split("\n").map(l => l.trim()).filter(l => l.length > 0);
}

/**
 * LCS-based line diff. Returns an array of DiffLine objects ordered top-down.
 * "removed" = in before, not in after.
 * "added"   = in after, not in before.
 * "unchanged" = in both.
 */
function computeLineDiff(before: string[], after: string[]): DiffLine[] {
  const m = before.length;
  const n = after.length;

  // Build LCS table (dp[i][j] = LCS length of before[0..i-1], after[0..j-1]).
  const dp: number[][] = Array.from({ length: m + 1 }, () =>
    new Array<number>(n + 1).fill(0),
  );
  for (let i = 1; i <= m; i++) {
    for (let j = 1; j <= n; j++) {
      if (before[i - 1] === after[j - 1]) {
        dp[i]![j] = (dp[i - 1]?.[j - 1] ?? 0) + 1;
      } else {
        dp[i]![j] = Math.max(dp[i - 1]?.[j] ?? 0, dp[i]?.[j - 1] ?? 0);
      }
    }
  }

  // Backtrack to produce diff.
  const result: DiffLine[] = [];
  let i = m;
  let j = n;
  while (i > 0 || j > 0) {
    if (
      i > 0 &&
      j > 0 &&
      before[i - 1] === after[j - 1]
    ) {
      result.push({ type: "unchanged", text: before[i - 1]! });
      i--;
      j--;
    } else if (
      j > 0 &&
      (i === 0 || (dp[i]?.[j - 1] ?? 0) >= (dp[i - 1]?.[j] ?? 0))
    ) {
      result.push({ type: "added", text: after[j - 1]! });
      j--;
    } else {
      result.push({ type: "removed", text: before[i - 1]! });
      i--;
    }
  }

  return result.reverse();
}

// ── Table columns ─────────────────────────────────────────────────────────────
const headers = [
  { title: "Rev", key: "revisionNumber", width: "60px", sortable: false },
  { title: "Edited", key: "revisedAt", sortable: false },
  { title: "By", key: "revisedBy", sortable: false },
  { title: "", key: "actions", sortable: false, width: "60px" },
];

function selectRevision(rev: LabJournalRevisionIO) {
  selectedRevision.value = rev;
}

function clearSelection() {
  selectedRevision.value = null;
}

function rowProps(item: LabJournalRevisionIO) {
  return {
    class:
      selectedRevision.value?.appId === item.appId
        ? "bg-primary-lighten-5 cursor-pointer"
        : "cursor-pointer",
    onClick: () => selectRevision(item),
  };
}

const hasDiff = computed(
  () => selectedRevision.value !== null && diffLines.value.length > 0,
);

const addedCount = computed(
  () => diffLines.value.filter(l => l.type === "added").length,
);
const removedCount = computed(
  () => diffLines.value.filter(l => l.type === "removed").length,
);
</script>

<template>
  <InformationDialog
    v-model:show-dialog="showDialog"
    :title="`Edit history — ${entryLabel}`"
    :loading="isLoading"
    :max-width="900"
  >
    <template #text>
      <div v-if="error" class="text-error text-body-2 mb-2">{{ error }}</div>

      <div
        v-else-if="!isLoading && revisions.length === 0"
        class="text-medium-emphasis text-body-2 pa-2"
      >
        No edit history found. Revision records are captured from J1d onwards;
        entries last updated before J1d shipped will not have history entries.
      </div>

      <template v-else>
        <!-- Revision list -->
        <v-data-table
          :headers="headers"
          :items="revisions"
          :items-per-page="-1"
          density="compact"
          hide-default-footer
          :row-props="({ item }) => rowProps(item as LabJournalRevisionIO)"
        >
          <template #[`item.revisionNumber`]="{ item }">
            <v-chip size="x-small" variant="tonal" color="primary">
              r{{ item.revisionNumber }}
            </v-chip>
          </template>

          <template #[`item.revisedAt`]="{ item }">
            {{ item.revisedAt ? toShortDateString(new Date(item.revisedAt)) : "—" }}
          </template>

          <template #[`item.revisedBy`]="{ item }">
            <span class="text-body-2">{{ item.revisedBy || "—" }}</span>
          </template>

          <template #[`item.actions`]="{ item }">
            <v-tooltip text="View diff against current version" location="top">
              <template #activator="{ props: tp }">
                <v-btn
                  v-bind="tp"
                  icon="mdi-compare"
                  variant="plain"
                  size="small"
                  :color="selectedRevision?.appId === item.appId ? 'primary' : undefined"
                  @click.stop="selectRevision(item as LabJournalRevisionIO)"
                />
              </template>
            </v-tooltip>
          </template>
        </v-data-table>

        <!-- Diff panel -->
        <div v-if="selectedRevision" class="mt-4">
          <div class="d-flex align-center mb-2">
            <span class="text-subtitle-2 mr-2">
              Diff: r{{ selectedRevision.revisionNumber }} → current
            </span>
            <v-chip size="x-small" color="success" variant="tonal" class="mr-1">
              +{{ addedCount }}
            </v-chip>
            <v-chip size="x-small" color="error" variant="tonal" class="mr-2">
              -{{ removedCount }}
            </v-chip>
            <v-btn
              icon="mdi-close"
              variant="plain"
              size="x-small"
              aria-label="Clear diff selection"
              @click="clearSelection"
            />
          </div>

          <div
            v-if="!hasDiff"
            class="text-medium-emphasis text-body-2 pa-2 border rounded"
          >
            No textual differences detected between this revision and the
            current version.
          </div>

          <div
            v-else
            class="diff-panel border rounded pa-2 text-mono text-caption"
            style="max-height: 360px; overflow-y: auto"
          >
            <div
              v-for="(line, idx) in diffLines"
              :key="idx"
              :class="{
                'diff-added': line.type === 'added',
                'diff-removed': line.type === 'removed',
                'diff-unchanged': line.type === 'unchanged',
              }"
              class="diff-line px-1"
            >
              <span class="diff-prefix mr-2">{{
                line.type === "added" ? "+" : line.type === "removed" ? "−" : " "
              }}</span>{{ line.text }}
            </div>
          </div>

          <div class="text-caption text-medium-emphasis mt-1">
            Diff shows plain-text content. Formatting and embedded images are
            stripped for readability.
          </div>
        </div>
      </template>
    </template>
  </InformationDialog>
</template>

<style scoped>
.diff-line {
  display: block;
  white-space: pre-wrap;
  word-break: break-all;
  line-height: 1.5;
}

.diff-added {
  background-color: rgba(var(--v-theme-success), 0.12);
  color: rgb(var(--v-theme-success));
}

.diff-removed {
  background-color: rgba(var(--v-theme-error), 0.12);
  color: rgb(var(--v-theme-error));
  text-decoration: line-through;
}

.diff-unchanged {
  color: rgba(var(--v-theme-on-surface), 0.7);
}

.diff-prefix {
  user-select: none;
  font-weight: 600;
}

.cursor-pointer {
  cursor: pointer;
}

.diff-panel {
  border: 1px solid rgb(var(--v-theme-divider1));
}
</style>
