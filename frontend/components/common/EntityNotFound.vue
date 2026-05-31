<script setup lang="ts">
/**
 * UU1 — UI-404-NICE-EMPTY-STATE (2026-05-31). Honest empty-state shown by
 * detail pages when the entity fetch returned a 404. Replaces the red
 * "Error while getXxx" toast that was previously the only signal.
 *
 * Operator-surfaced 2026-05-31 via the canonical repro
 * `/collections/1787/dataobjects/1792`.
 */

type EntityKind =
  | "Collection"
  | "DataObject"
  | "FileReference"
  | "TimeseriesReference"
  | "StructuredDataReference"
  | "Container";

const props = withDefaults(
  defineProps<{
    entityKind: EntityKind;
    /** The id literal the user typed/bookmarked (UUID v7 or numeric). */
    requestedId: string;
    /** Where the "Browse …" CTA navigates to. Defaults to `/collections`. */
    parentRoute?: string;
  }>(),
  {
    parentRoute: "/collections",
  },
);

const route = useRoute();

const KIND_LABEL: Record<EntityKind, string> = {
  Collection: "collection",
  DataObject: "data object",
  FileReference: "file reference",
  TimeseriesReference: "timeseries reference",
  StructuredDataReference: "structured-data reference",
  Container: "container",
};
const KIND_CTA: Record<EntityKind, string> = {
  Collection: "Browse Collections",
  DataObject: "Back to Collection",
  FileReference: "Back to Data Object",
  TimeseriesReference: "Back to Data Object",
  StructuredDataReference: "Back to Data Object",
  Container: "Browse Containers",
};

const heading = computed(
  () => `This ${KIND_LABEL[props.entityKind]} doesn't exist.`,
);
const ctaText = computed(() => KIND_CTA[props.entityKind] + " →");
const currentUrl = computed(() => route.fullPath);
</script>

<template>
  <v-empty-state
    icon="mdi-link-variant-off"
    :title="heading"
    text="It may have been deleted, or you may be following a stale link."
  >
    <template #actions>
      <v-btn :to="parentRoute" color="primary" variant="flat">
        {{ ctaText }}
      </v-btn>
    </template>
    <template #default>
      <div class="text-medium-emphasis text-body-2 mt-4">
        Requested URL:
        <code class="text-caption d-inline-block ml-1">{{ currentUrl }}</code>
      </div>
    </template>
  </v-empty-state>
</template>
