<script setup lang="ts">
/**
 * FORM-UX-ACTIONBUTTON — the unified in-context shapes entry point
 * (doc 125 §5.3 / D4 + the 2026-06-12 UX audit's applicable contract).
 *
 * ONE button on entity detail pages carrying both directions of the
 * shapes UX, fed by `GET /v2/shapes/applicable?focusAppId=…`:
 *
 *  - "View as …"   (mode=VIEW)  → the existing render flow
 *    (`/shapes/render`, prefilled — the UX612-M1-fixed path). This
 *    ABSORBS the former standalone "Render view" entry in the
 *    DataObject Tools menu (`do-render` in toolsContext.ts, removed
 *    in the same commit).
 *  - "Record a …"  (mode=FORM)  → the form surface
 *    (`/tools/form-preview?template=…&focusAppId=…` until the full
 *    form pane ships).
 *
 * Hidden entirely when nothing is applicable — an empty menu is dead
 * UI. Per the tools-in-context-first rule the entity detail page is
 * the canonical entry; the appId comes from context, never typed.
 */

import { useRouter } from "vue-router";
import { useApplicableShapes } from "~/composables/useApplicableShapes";
import {
  actionMenuVisible,
  buildActionTarget,
  groupApplicableItems,
} from "~/utils/actionMenu";
import type { ApplicableShapeItem } from "~/composables/useApplicableShapes";

const props = defineProps<{
  /** Focus entity appId (UUID v7). Discovery refetches when it changes. */
  focusAppId: string | null | undefined;
  /** Optional override label. Default: "Actions". */
  label?: string;
}>();

const router = useRouter();

const focusRef = computed(() => props.focusAppId ?? null);
const { items } = useApplicableShapes(focusRef);

const groups = computed(() => groupApplicableItems(items.value));
const visible = computed(() => actionMenuVisible(items.value));

function onItemClick(item: ApplicableShapeItem) {
  if (!props.focusAppId) return;
  const target = buildActionTarget(item, props.focusAppId);
  // Bookmarkable: router.push with query params; destination pages read
  // them and prefill (same convention as EntityToolsMenu).
  void router.push({ path: target.path, query: target.query });
}
</script>

<template>
  <v-menu v-if="visible" offset="4" :close-on-content-click="true">
    <template #activator="{ props: activator }">
      <v-btn
        v-bind="activator"
        data-testid="action-menu-button"
        variant="tonal"
        color="primary"
        density="comfortable"
        prepend-icon="mdi-shape-outline"
        append-icon="mdi-menu-down"
        size="small"
      >
        {{ label ?? "Actions" }}
      </v-btn>
    </template>
    <v-list density="comfortable" min-width="320" data-testid="action-menu-list">
      <template v-if="groups.views.length > 0">
        <v-list-subheader data-testid="action-menu-views-header">
          View as …
        </v-list-subheader>
        <v-list-item
          v-for="item in groups.views"
          :key="`view-${item.templateAppId}`"
          :data-testid="`action-item-view-${item.templateAppId}`"
          @click="onItemClick(item)"
        >
          <template #prepend>
            <v-icon>mdi-cube-scan</v-icon>
          </template>
          <v-list-item-title>{{ item.title }}</v-list-item-title>
          <v-list-item-subtitle v-if="item.reason" class="text-caption">
            {{ item.reason }}
          </v-list-item-subtitle>
        </v-list-item>
      </template>
      <template v-if="groups.forms.length > 0">
        <v-list-subheader data-testid="action-menu-forms-header">
          Record a …
        </v-list-subheader>
        <v-list-item
          v-for="item in groups.forms"
          :key="`form-${item.templateAppId}`"
          :data-testid="`action-item-form-${item.templateAppId}`"
          @click="onItemClick(item)"
        >
          <template #prepend>
            <v-icon>mdi-form-select</v-icon>
          </template>
          <v-list-item-title>{{ item.title }}</v-list-item-title>
          <v-list-item-subtitle v-if="item.reason" class="text-caption">
            {{ item.reason }}
          </v-list-item-subtitle>
        </v-list-item>
      </template>
    </v-list>
  </v-menu>
</template>
