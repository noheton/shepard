<script setup lang="ts">
/**
 * KIP1k — publication-status badge for Collection / DataObject panes.
 *
 * Shows a small chip indicating whether the entity has been published
 * (i.e. has at least one active non-retired :Publication row):
 *
 * - Green "Published" chip when at least one active Publication exists.
 *   Clicking opens a tooltip / details panel showing the current PID.
 * - Gray "Unpublished" chip when the entity has never been published
 *   (or all Publications are retired). The chip is hidden by default
 *   for unpublished entities; pass `:show-unpublished="true"` to show
 *   the gray chip in all cases.
 * - Nothing (no chip) while the fetch is in-flight (`isFetching`).
 *
 * The badge is **purely informational** — it contains no publish or
 * unpublish action. Use the sibling {@link PublishButton} component for
 * the publishing action.
 *
 * Props:
 * - `entityKind`     — "collections" | "data-objects"
 * - `entityAppId`    — appId of the entity; badge hides itself when null
 * - `showUnpublished` — show the gray "Unpublished" chip (default false)
 *
 * The composable is reactive: when `entityAppId` changes (navigation)
 * the badge automatically re-fetches.
 *
 * @see usePublicationStatus — composable that drives this component
 * @see PublishButton — action component (publish / re-mint)
 */
import { useClipboard } from "@vueuse/core";
import { usePublicationStatus } from "~/composables/context/usePublicationStatus";
import type { PublishableKind } from "~/composables/context/usePublicationStatus";

interface Props {
  entityKind: PublishableKind;
  entityAppId: string | null | undefined;
  showUnpublished?: boolean;
}

const props = withDefaults(defineProps<Props>(), {
  showUnpublished: false,
});

const entityAppIdRef = toRef(props, "entityAppId");

const { isPublished, currentPublication, isFetching } = usePublicationStatus(
  props.entityKind,
  entityAppIdRef,
);

const tooltipText = computed<string>(() => {
  if (!isPublished.value) return "This entity has not been published yet.";
  const pub = currentPublication.value;
  if (!pub) return "Published.";
  const parts: string[] = [`PID: ${pub.pid}`];
  if (pub.publishedBy) parts.push(`by ${pub.publishedBy}`);
  if (pub.mintedAt) {
    const date = new Date(pub.mintedAt).toLocaleDateString("en-GB", {
      year: "numeric",
      month: "short",
      day: "numeric",
    });
    parts.push(`on ${date}`);
  }
  return parts.join(" · ");
});

/**
 * Copy the current PID to clipboard for easy citation.
 * Only fired when the chip is clicked and the entity is published.
 */
const { copy: copyToClipboard } = useClipboard();

function onChipClick() {
  if (!isPublished.value || !currentPublication.value?.pid) return;
  copyToClipboard(currentPublication.value.pid);
  emitSuccess("PID copied to clipboard");
}
</script>

<template>
  <!-- Hide badge entirely while fetching or when appId is absent. -->
  <template v-if="!isFetching && entityAppId">
    <v-tooltip :text="tooltipText" location="bottom">
      <template #activator="{ props: tooltipProps }">
        <!-- Published state: green chip; click copies PID to clipboard. -->
        <v-chip
          v-if="isPublished"
          v-bind="tooltipProps"
          color="success"
          size="small"
          variant="tonal"
          prepend-icon="mdi-certificate-outline"
          label
          data-testid="publication-status-badge-published"
          style="cursor: pointer"
          @click="onChipClick"
        >
          Published
        </v-chip>
        <!-- Unpublished state: only rendered when showUnpublished=true. -->
        <v-chip
          v-else-if="showUnpublished"
          v-bind="tooltipProps"
          color="default"
          size="small"
          variant="tonal"
          prepend-icon="mdi-certificate-outline"
          label
          data-testid="publication-status-badge-unpublished"
        >
          Unpublished
        </v-chip>
      </template>
    </v-tooltip>
  </template>
</template>

<style scoped lang="scss"></style>
