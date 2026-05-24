<script lang="ts" setup>
/**
 * RDM-005 — Metadata Completeness Score widget.
 *
 * Renders a 0–100 score chip + per-check list on the Collection
 * landing, between the "Cite this dataset" card and the Data Objects
 * panel.
 *
 * Closes top-5 finding #5 in
 * `aidocs/agent-findings/rdm-scrutinizer-2026-05-24.md` — creates
 * operator pressure to fill the FAIR R1.1 / R1.3 gaps the prior 13
 * UI improvements left untouched.
 *
 * Architecture: the scoring math is in
 * `frontend/utils/metadataCompleteness.ts` (pure, no I/O — unit-
 * tested with 36 Vitest cases). This component is the
 * composable-wiring + rendering layer.
 *
 * Data sources:
 *   - Collection wire shape (passed in via prop) — name, description,
 *     license, accessRights, heroImageUrl, dataObjectIds
 *   - SemanticAnnotation count — `AnnotatedCollection.fetchAnnotations`
 *   - Lab journal entry count — `useFetchCollectionLabJournalEntries`
 *   - Creator ORCID — `UserApi.getUser({username: createdBy})`
 *
 * All three fetches are best-effort; failures resolve to `0` /
 * `null` and the score is conservatively biased toward "incomplete"
 * during loading. The widget never blocks the page render.
 */
import { UserApi, type Collection } from "@dlr-shepard/backend-client";
import { AnnotatedCollection } from "~/composables/annotated";
import { useFetchCollectionLabJournalEntries } from "~/composables/context/useFetchCollectionLabJournalEntries";
import { useShepardApi } from "~/composables/common/api/useShepardApi";
import {
  computeMetadataCompleteness,
  type MetadataCheck,
} from "~/utils/metadataCompleteness";

const props = defineProps<{
  collection: Collection;
}>();

const router = useRouter();

// ── Collection appId for the lab-journal listing endpoint ────────────────
//
// Defensive read — mirrors the parent page's pattern. Older backend
// builds may not surface the field; the widget degrades to a
// `null` labJournalCount in that case and the check renders as
// failing.
const collectionAppId = computed<string | null>(() => {
  const raw = (props.collection as unknown as { appId?: string | null }).appId;
  return raw ?? null;
});

// ── Fetch: semantic annotation count ─────────────────────────────────────
//
// Instantiate the `Annotated` wrapper inline in `<script setup>` so its
// constructor's `useShepardApi(...)` call runs inside the Nuxt
// composable context. Wrapping the constructor inside `onMounted`
// breaks the API key injection (the call happens after the setup
// scope has unwound).
//
// The widget faces a real race: `useShepardApi` builds the API
// instance from `useAuth().data?.accessToken`, which is initially
// `undefined` on a fresh navigation. The first call therefore hits
// the backend with no Authorization header → 401 → we'd settle on
// `count=0` even though there ARE annotations. The same race exists
// for the ORCID lookup. We work around it by watching `data` (the
// auth session) and re-firing the fetches once the token is
// present. The other path (existing `SemanticAnnotationList.vue`)
// hides this because it's fired in render scope where the auth
// data is typically already settled — our card lives slightly
// earlier in the page composition.
const annotatedCollection = new AnnotatedCollection(props.collection.id);
const semanticAnnotationCount = ref<number | null>(null);
async function fetchAnnotationCount() {
  try {
    const annotations = await annotatedCollection.fetchAnnotations();
    semanticAnnotationCount.value = annotations.length;
  } catch {
    // Best-effort — treat as zero so the check renders as failing
    // rather than perpetually loading. A real-world 403 / 500 here
    // means the user can't reach the annotations regardless, so the
    // check status is still actionable. The auth-settle re-fire
    // below covers the legitimate 401-on-first-load case.
    semanticAnnotationCount.value = 0;
  }
}

// ── Fetch: lab journal entry count ───────────────────────────────────────
const { entries: labJournalEntries } =
  useFetchCollectionLabJournalEntries(collectionAppId);
const labJournalCount = computed<number | null>(() =>
  labJournalEntries.value === undefined
    ? null
    : labJournalEntries.value.length,
);

// ── Fetch: creator ORCID ─────────────────────────────────────────────────
//
// Same Nuxt-context constraint as the annotation fetch — capture the
// API binding in setup scope, invoke from `onMounted`.
const userApi = useShepardApi(UserApi);
const creatorOrcid = ref<string | null>(null);
async function fetchCreatorOrcid() {
  const username = props.collection.createdBy?.trim();
  if (!username) return;
  try {
    const user = await userApi.value.getUser({ username });
    const orcid = (user as unknown as { orcid?: string | null }).orcid ?? null;
    creatorOrcid.value = orcid ?? "";
  } catch {
    // 404 on user is recoverable — treat as "no ORCID set".
    creatorOrcid.value = "";
  }
}

// Initial best-effort fetch on mount + re-fire on auth-settle. The
// `useAuth().data` ref starts `null` and populates once the
// NextAuth session has been resolved. Without the watcher the card
// permanently shows `null`/`0` for both fetches.
const { data: authData } = useAuth();
onMounted(() => {
  void fetchAnnotationCount();
  void fetchCreatorOrcid();
});
watch(
  () => authData.value?.accessToken,
  newToken => {
    if (newToken) {
      void fetchAnnotationCount();
      void fetchCreatorOrcid();
    }
  },
);

// ── Compute completeness ─────────────────────────────────────────────────
const result = computed(() =>
  computeMetadataCompleteness({
    collection: props.collection,
    semanticAnnotationCount: semanticAnnotationCount.value,
    labJournalCount: labJournalCount.value,
    creatorOrcid: creatorOrcid.value,
  }),
);

const scoreLabel = computed(() => `${result.value.score} / 100`);

const bandIcon = computed(() => {
  switch (result.value.band) {
    case "success":
      return "mdi-check-circle";
    case "warning":
      return "mdi-alert";
    case "error":
    default:
      return "mdi-alert-octagon";
  }
});

const bandTitle = computed(() => {
  switch (result.value.band) {
    case "success":
      return "DMP-grade — ready for publication";
    case "warning":
      return "Missing key FAIR fields";
    case "error":
    default:
      return "Not publication-ready";
  }
});

// ── Per-check expansion (default collapsed below the score chip) ─────────
const showChecks = ref(false);

// ── Deep-link handler — scroll the page to the relevant anchor ───────────
function jumpToCheck(check: MetadataCheck) {
  if (check.id === "creatorOrcid") {
    // ORCID lives off-page on /me — route push, not scroll.
    void router.push("/me");
    return;
  }
  if (!check.deepLink) return;
  if (typeof document === "undefined") return;
  const el = document.getElementById(check.deepLink);
  if (el) {
    el.scrollIntoView({ behavior: "smooth", block: "center" });
    // A short highlight pulse to draw the eye — uses the same
    // primary glow as the inline-edit affordances elsewhere.
    el.classList.add("metadata-deeplink-flash");
    setTimeout(() => el.classList.remove("metadata-deeplink-flash"), 1600);
  }
}
</script>

<template>
  <v-card
    class="metadata-completeness-card"
    variant="outlined"
    data-testid="metadata-completeness-card"
  >
    <v-card-title class="d-flex align-center ga-2 flex-wrap">
      <v-icon size="small" color="primary">mdi-clipboard-check-outline</v-icon>
      <span>Metadata completeness</span>
      <v-spacer />
      <v-chip
        :color="result.band"
        variant="flat"
        size="default"
        data-testid="metadata-completeness-score"
        :title="bandTitle"
      >
        <v-icon start :icon="bandIcon" />
        {{ scoreLabel }}
      </v-chip>
      <v-btn
        variant="text"
        size="small"
        density="comfortable"
        :prepend-icon="showChecks ? 'mdi-chevron-up' : 'mdi-chevron-down'"
        data-testid="metadata-completeness-toggle"
        @click="showChecks = !showChecks"
      >
        {{ showChecks ? "Hide checks" : "Show checks" }}
      </v-btn>
    </v-card-title>
    <v-card-text v-if="!showChecks" class="pt-0 text-caption text-medium-emphasis">
      <span data-testid="metadata-completeness-summary">
        {{
          result.checks.filter((c) => c.passed).length
        }} / {{ result.checks.length }} checks pass — open the list for
        per-check guidance.
      </span>
    </v-card-text>
    <v-expand-transition>
      <v-list
        v-if="showChecks"
        density="compact"
        class="pt-0"
        data-testid="metadata-completeness-list"
      >
        <v-list-item
          v-for="check in result.checks"
          :key="check.id"
          :data-testid="`metadata-check-${check.id}`"
        >
          <template #prepend>
            <v-icon
              :color="check.passed ? 'success' : 'error'"
              :icon="check.passed ? 'mdi-check-circle' : 'mdi-close-circle'"
            />
          </template>
          <v-list-item-title>
            {{ check.label }}
            <span class="text-caption text-medium-emphasis">
              ({{ check.points }} pts)
            </span>
          </v-list-item-title>
          <v-list-item-subtitle class="text-caption">
            <v-tooltip
              location="bottom"
              :text="check.why"
              :open-delay="250"
            >
              <template #activator="{ props: actProps }">
                <span
                  v-bind="actProps"
                  class="metadata-check-why"
                  tabindex="0"
                  >Why this matters</span
                >
              </template>
            </v-tooltip>
          </v-list-item-subtitle>
          <template #append>
            <v-btn
              v-if="!check.passed"
              variant="tonal"
              size="x-small"
              color="primary"
              :data-testid="`metadata-check-${check.id}-action`"
              append-icon="mdi-arrow-right"
              @click="jumpToCheck(check)"
            >
              {{ check.actionLabel }}
            </v-btn>
          </template>
        </v-list-item>
      </v-list>
    </v-expand-transition>
  </v-card>
</template>

<style lang="scss" scoped>
.metadata-completeness-card {
  margin-bottom: 24px;
}
.metadata-check-why {
  cursor: help;
  text-decoration: underline dotted;
  text-underline-offset: 2px;
}
</style>

<style lang="scss">
/* Global — the flash class is added to elements outside the card's
 * scoped style scope (the LIC1 chips, the description section).
 * Keep it global so any anchor declared in `DEEP_LINK_IDS` lights up
 * on jump without needing scoped overrides per consumer. */
.metadata-deeplink-flash {
  animation: metadata-deeplink-pulse 1.6s ease-out;
}
@keyframes metadata-deeplink-pulse {
  0% {
    box-shadow: 0 0 0 0 rgba(var(--v-theme-primary), 0.6);
  }
  70% {
    box-shadow: 0 0 0 12px rgba(var(--v-theme-primary), 0);
  }
  100% {
    box-shadow: 0 0 0 0 rgba(var(--v-theme-primary), 0);
  }
}
</style>
