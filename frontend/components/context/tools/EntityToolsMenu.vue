<script setup lang="ts">
/**
 * TOOLS-CONTEXT-* — single popover-menu component used on both
 * Collection-detail and DataObject-detail pages.
 *
 * Per the persona-board feedback during dispatch:
 *  - Reluctant Senior: "don't sprawl four standalone buttons across the
 *    header" — fold the actions into one collapsed "Tools" affordance.
 *  - Digital Native: "bookmarkability preserved" — every menu item routes
 *    via `router.push({ path, query })` with the entity's appId as a
 *    query param. The destination page reads it; a bookmarked URL works.
 *  - Operator: "global Tools menu still useful" — yes, this is the
 *    in-context primary; the global `/tools` landing is the fallback.
 *
 * The menu is purely a router-push affordance — it doesn't construct
 * backend URLs (per the "UI never asks for paths/URLs" rule) and it
 * doesn't hit the API. Destination pages own URL resolution.
 */

import { useRouter } from "vue-router";
import {
  getContextTools,
  type ContextToolBuildContext,
  type ContextToolItem,
  type ToolsContextScope,
} from "~/utils/toolsContext";

const props = defineProps<{
  /** Entity appId (UUID v7). When absent, the menu renders disabled. */
  appId: string | null | undefined;
  /** Scope = which inventory to show. */
  scope: ToolsContextScope;
  /** Optional override label. Default: "Tools". */
  label?: string;
  /** Optional override density. Default: "comfortable". */
  density?: "default" | "comfortable" | "compact";
  /**
   * TOOLS-CONTEXT-DO-TEMPLATE-DETECT-1 — appId of a `:ShepardTemplate`
   * attached to the entity (from `DataObjectIO.attachedTemplateAppId`).
   * Drives both the gate predicates on per-item `enabledWhen` (hides
   * DO-SHACL / DO-RENDER when null) and the destination prefill.
   */
  attachedTemplateAppId?: string | null;
}>();

const router = useRouter();

const buildCtx = computed<ContextToolBuildContext>(() => ({
  attachedTemplateAppId: props.attachedTemplateAppId,
}));

const items = computed<ContextToolItem[]>(() =>
  getContextTools(props.scope).filter(
    (item) => !item.enabledWhen || item.enabledWhen(buildCtx.value),
  ),
);

const isDisabled = computed(() => !props.appId);

function onItemClick(item: ContextToolItem) {
  if (!props.appId) return;
  // Bookmarkable: router.push with query params. The destination page
  // reads `route.query.focusAppId` (etc) and pre-fills its forms.
  void router.push({
    path: item.path,
    query: item.buildQuery(props.appId, buildCtx.value),
  });
}
</script>

<template>
  <v-menu offset="4" :close-on-content-click="true">
    <template #activator="{ props: activator }">
      <v-btn
        v-bind="activator"
        :data-testid="`tools-menu-${scope}`"
        variant="tonal"
        color="secondary"
        :density="density ?? 'comfortable'"
        prepend-icon="mdi-tools"
        append-icon="mdi-menu-down"
        :disabled="isDisabled"
        size="small"
      >
        {{ label ?? "Tools" }}
      </v-btn>
    </template>
    <v-list
      density="comfortable"
      min-width="320"
      :data-testid="`tools-menu-list-${scope}`"
    >
      <v-list-item
        v-for="item in items"
        :key="item.id"
        :data-testid="`tools-item-${item.id}`"
        @click="onItemClick(item)"
      >
        <template #prepend>
          <v-icon>{{ item.icon }}</v-icon>
        </template>
        <v-list-item-title>{{ item.title }}</v-list-item-title>
        <v-list-item-subtitle class="text-caption">
          {{ item.subtitle }}
        </v-list-item-subtitle>
      </v-list-item>
    </v-list>
  </v-menu>
</template>
