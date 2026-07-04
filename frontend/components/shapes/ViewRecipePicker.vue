<script setup lang="ts">
/**
 * VIEWER-AS-VIEW-RECIPE-RULE-2026-06-29 PR-4 — in-context picker that lists
 * the VIEW_RECIPE templates applicable to a file's `fileKind` and hands the
 * user off to `/shapes/render?templateAppId=<tpl>&focusShepardId=<focusDoApp>`.
 *
 * Doctrine: `memory/feedback_file_viewers_as_view_recipe.md`. Every viewer
 * (URDF, video, SVDX channel chart, OTvis heatmap, image bundle, …) is a
 * VIEW_RECIPE shape consumed through POST /v2/shapes/render — never a bespoke
 * page. The file-reference + videostream-reference detail pages mount this
 * picker as the "Open as …" affordance in place of the previous in-place
 * content renderer (cf. aidocs/16:VIEWER-AS-VIEW-RECIPE-RULE-2026-06-29).
 *
 * Filtering: `GET /v2/templates` returns every template (no kind/fileKind
 * filter exists today, per useFetchTemplates). We filter client-side —
 *   templateKind === "VIEW_RECIPE"   AND
 *   body parses as JSON AND its `appliesToFileKinds` array includes this
 *   file's `fileKind`.
 * Templates whose body fails to parse or doesn't declare a file-kind match
 * are treated as non-matching (the sibling PR-1/2/3 recipes set the field
 * explicitly; the fail-closed default keeps the empty-state honest until
 * those land).
 *
 * Emits `select` with the chosen template's appId so the parent owns the
 * navigation (matches the OpenIn3dViewButton emit-up pattern + keeps the
 * component testable without a router stub).
 */
import type { ShepardTemplate } from "@dlr-shepard/backend-client";
import { useFetchTemplates } from "~/composables/context/admin/useFetchTemplates";

const props = defineProps<{
  /** The file's discriminator (krl, urdf, video, svdx, …). Null for bundles
   *  or unrecognised kinds — picker renders the empty state. */
  fileKind: string | null | undefined;
  /** appId of the DataObject (or reference) that the chosen recipe will
   *  bind to. Forwarded to /shapes/render as `focusShepardId`. */
  focusShepardId: string | undefined;
}>();

const emit = defineEmits<{
  (e: "select", payload: { templateAppId: string }): void;
}>();

const { templates, isLoading } = useFetchTemplates();

/** Parse `body` defensively — `body` is documented as opaque JSON DSL and
 *  may be empty / malformed on legacy templates. */
function templateAppliesToFileKind(t: ShepardTemplate, kind: string): boolean {
  if (t.retired) return false;
  if (t.templateKind !== "VIEW_RECIPE") return false;
  if (!t.body) return false;
  try {
    const parsed = JSON.parse(t.body) as { appliesToFileKinds?: unknown };
    const list = parsed.appliesToFileKinds;
    if (!Array.isArray(list)) return false;
    return list.some((entry) => typeof entry === "string" && entry === kind);
  } catch {
    return false;
  }
}

const matchingTemplates = computed<ShepardTemplate[]>(() => {
  const kind = props.fileKind;
  if (!kind) return [];
  return templates.value.filter((t) => templateAppliesToFileKind(t, kind));
});

function onPick(templateAppId: string) {
  emit("select", { templateAppId });
}
</script>

<template>
  <div>
    <div v-if="isLoading" class="text-textbody2 text-medium-emphasis">
      Loading view recipes…
    </div>
    <div v-else>
      <div
        v-if="matchingTemplates.length === 0"
        class="text-textbody2 text-medium-emphasis"
        data-test="view-recipe-picker-empty"
      >
        <span v-if="!fileKind">
          No view recipes apply to this reference (no file-kind discriminator).
        </span>
        <span v-else>
          No view recipes for file kind <code>{{ fileKind }}</code> yet.
        </span>
      </div>
      <div v-else class="d-flex flex-wrap ga-2" data-test="view-recipe-picker">
        <v-btn
          v-for="t in matchingTemplates"
          :key="t.appId"
          :prepend-icon="t.iconKey ?? 'mdi-shape-outline'"
          variant="tonal"
          density="comfortable"
          size="small"
          :disabled="!focusShepardId"
          :data-test-template-app-id="t.appId"
          @click="onPick(t.appId)"
        >
          Open as {{ t.name }}
        </v-btn>
      </div>
    </div>
  </div>
</template>
