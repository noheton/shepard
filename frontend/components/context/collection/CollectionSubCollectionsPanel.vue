<!--
PROJ-PANEL-1 — Sub-Collections panel on Collection-detail.

Cross-reference: aidocs/integrations/121-project-and-subcollections.md §4.1.

Mounted on pages/collections/[collectionId]/index.vue. The panel hides itself
entirely when:
  - the prop appId is empty,
  - OR the GET /v2/projects/{appId}/sub-collections returns 404 (Collection
    is not a Project),
  - OR the response carries an empty subCollections list AND no programme strip.

That makes this component zero-cost to mount unconditionally on every
Collection detail page — only Projects ever render anything.
-->
<script setup lang="ts">
import { useProjectSubCollections } from "~/composables/context/useProject";
import { defaultIconForKind } from "~/composables/useTemplateIcon";

interface Props {
  appId: string;
}
const props = defineProps<Props>();

const router = useRouter();

const { subCollections, isLoading, isMissing } = useProjectSubCollections(props.appId);

// TEMPLATE-ICONS-2-FE-RENDER-POINTS-EXPAND: sub-collection tiles use the
// per-kind default for "Collection" (mdi-folder-multiple). The sub-collection
// summary type from GET /v2/projects/{appId}/sub-collections does not carry
// the collection's attached template, so there is no iconKey to override with.
// If a future endpoint adds template metadata to the summary, wire
// useTemplateIconByAppId here.
function tileIcon(): string {
  return defaultIconForKind("Collection");
}

function shouldShow(): boolean {
  if (isLoading.value) return true; // render loading state to avoid flash
  if (isMissing.value) return false;
  if (!subCollections.value) return false;
  const hasProgrammes = (subCollections.value.programmes?.length ?? 0) > 0;
  const hasChildren = (subCollections.value.subCollections?.length ?? 0) > 0;
  return hasProgrammes || hasChildren;
}

function lastActivityLabel(iso: string | null | undefined): string {
  if (!iso) return "";
  return new Date(iso).toLocaleString();
}

function openChild(childAppId: string) {
  void router.push(`/collections/${childAppId}`);
}
</script>

<template>
  <v-card
    v-if="shouldShow()"
    variant="outlined"
    class="mb-6"
    data-testid="collection-sub-collections-panel"
  >
    <v-card-title class="d-flex align-center">
      <v-icon icon="mdi-folder-multiple" class="mr-2" />
      Sub-Collections
      <v-chip
        v-if="subCollections && subCollections.subCollections.length > 0"
        size="x-small"
        variant="tonal"
        color="primary"
        class="ml-2"
      >{{ subCollections.subCollections.length }}</v-chip>
    </v-card-title>

    <!-- Programme strip — labels declared on this Project. -->
    <v-card-text
      v-if="subCollections && subCollections.programmes.length > 0"
      class="pb-2"
      data-testid="collection-programme-strip"
    >
      <v-chip
        v-for="prog in subCollections.programmes"
        :key="prog"
        size="small"
        variant="tonal"
        color="secondary"
        class="mr-2 mb-2"
        prepend-icon="mdi-flag-outline"
      >{{ prog }}</v-chip>
    </v-card-text>

    <!-- Tile grid of child Collections. -->
    <v-card-text v-if="subCollections && subCollections.subCollections.length > 0">
      <v-row dense>
        <v-col
          v-for="child in subCollections.subCollections"
          :key="child.appId"
          cols="12"
          sm="6"
          md="4"
          lg="3"
        >
          <v-card
            variant="tonal"
            link
            class="h-100 d-flex flex-column"
            data-testid="collection-sub-collection-tile"
            @click="openChild(child.appId)"
          >
            <v-card-title class="d-flex align-center text-body-1">
              <v-icon :icon="tileIcon()" class="mr-2" size="small" />
              <span class="text-truncate">{{ child.name }}</span>
            </v-card-title>
            <v-card-text class="text-caption">
              <div>
                <v-icon icon="mdi-file-document-multiple-outline" size="small" class="mr-1" />
                {{ child.doCount.toLocaleString() }} DataObjects
              </div>
              <div v-if="child.ownerGroup" class="mt-1">
                <v-icon icon="mdi-account-group-outline" size="small" class="mr-1" />
                {{ child.ownerGroup }}
              </div>
              <div v-if="child.lastActivity" class="mt-1">
                <v-icon icon="mdi-clock-outline" size="small" class="mr-1" />
                last activity {{ lastActivityLabel(child.lastActivity) }}
              </div>
            </v-card-text>
            <v-card-text
              v-if="child.alsoMemberOf.length > 0"
              class="text-caption pt-0"
            >
              <v-chip
                size="x-small"
                variant="outlined"
                color="info"
                prepend-icon="mdi-link-variant"
              >also in {{ child.alsoMemberOf.length }} other Project{{
                child.alsoMemberOf.length === 1 ? '' : 's'
              }}</v-chip>
            </v-card-text>
          </v-card>
        </v-col>
      </v-row>
    </v-card-text>

    <v-card-text v-else-if="isLoading">
      <v-progress-linear indeterminate color="primary" />
    </v-card-text>
  </v-card>
</template>
