<script setup lang="ts">
/**
 * UU1 — UI-404-NICE-EMPTY-STATE (2026-05-31). Honest empty-state shown by
 * detail pages when the entity fetch returned a 404. Replaces the red
 * "Error while getXxx" toast that was previously the only signal.
 *
 * UU2 — UI-STALE-URL-HINT: when `requestedId` is all-digits (legacy Neo4j
 * long), surface a hint card explaining that the current instance addresses
 * entities by UUID v7 appIds and that a bookmarked pre-wipe URL no longer
 * resolves. Operator-surfaced 2026-05-31 via the canonical repro
 * `/collections/1787/dataobjects/1792`.
 */
import { isNumericLegacyId } from "~/utils/idShape";

type EntityKind =
  | "Collection"
  | "DataObject"
  | "FileReference"
  | "TimeseriesReference"
  | "StructuredDataReference"
  | "VideoStreamReference"
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
const showStaleUrlHint = computed(() => isNumericLegacyId(props.requestedId));

const KIND_LABEL: Record<EntityKind, string> = {
  Collection: "collection",
  DataObject: "data object",
  FileReference: "file reference",
  TimeseriesReference: "timeseries reference",
  StructuredDataReference: "structured-data reference",
  VideoStreamReference: "video reference",
  Container: "container",
};
const KIND_CTA: Record<EntityKind, string> = {
  Collection: "Browse Collections",
  DataObject: "Back to Collection",
  FileReference: "Back to Data Object",
  TimeseriesReference: "Back to Data Object",
  StructuredDataReference: "Back to Data Object",
  VideoStreamReference: "Back to Data Object",
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
      <v-alert
        v-if="showStaleUrlHint"
        type="info"
        variant="tonal"
        class="mt-6 text-left mx-auto"
        max-width="640"
        icon="mdi-history"
      >
        <div class="text-body-2">
          This URL uses a <strong>numeric id</strong>. The current Shepard
          instance addresses entities by UUID v7 appIds
          (e.g. <code>019e6ffc-89a4-76b5-8dbb-15888646a904</code>). If you
          bookmarked this URL before the last data reset, the underlying
          entity may no longer exist or may have a different appId.
          <router-link :to="parentRoute" class="text-primary">
            Browse to find current data.
          </router-link>
        </div>
      </v-alert>
    </template>
  </v-empty-state>
</template>
