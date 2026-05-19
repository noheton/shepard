<script setup lang="ts">
/**
 * DescriptionDisplay — read-only render of an entity's `description`.
 *
 * The description is authored as markdown (CommonMark + GFM). Previously
 * we passed it through `RichTextEditor` (TipTap) in read-only mode, but
 * TipTap interprets the input as HTML — so `# heading` rendered as the
 * literal characters instead of an `<h1>`. User feedback 2026-05-19.
 *
 * Fix: render through the `marked` lib (already a dep, used by /help)
 * and inject the resulting HTML. `marked` v9 enables GFM by default
 * (tables, strikethrough, task-lists, fenced code). We don't sanitize —
 * descriptions are written by authenticated users with Write permission
 * on the entity, same trust boundary as before.
 */
import { computed } from "vue";
import { marked } from "marked";

interface DescriptionDisplayProps {
  entity: { description?: string | null };
}

const props = defineProps<DescriptionDisplayProps>();

const rendered = computed(() => {
  const src = props.entity.description ?? "";
  if (!src) return "";
  try {
    return marked.parse(src, { gfm: true, breaks: true, async: false }) as string;
  } catch {
    return src;
  }
});
</script>

<template>
  <v-container fluid class="pa-0">
    <v-row v-if="!!entity.description" no-gutters>
      <!-- v-html intentionally — see component-level comment above. -->
      <div class="description-rendered" v-html="rendered" />
    </v-row>
  </v-container>
</template>

<style scoped>
.description-rendered {
  font-size: 14px;
  line-height: 1.55;
  color: rgb(var(--v-theme-on-background));
  width: 100%;
}
.description-rendered :deep(h1),
.description-rendered :deep(h2),
.description-rendered :deep(h3),
.description-rendered :deep(h4) {
  font-weight: 600;
  margin: 0.6em 0 0.3em 0;
}
.description-rendered :deep(h1) { font-size: 1.25em; }
.description-rendered :deep(h2) { font-size: 1.15em; }
.description-rendered :deep(h3) { font-size: 1.05em; }
.description-rendered :deep(p) { margin: 0.4em 0; }
.description-rendered :deep(ul),
.description-rendered :deep(ol) { padding-left: 1.5em; margin: 0.4em 0; }
.description-rendered :deep(code) {
  background: rgba(0, 0, 0, 0.06);
  padding: 0.1em 0.35em;
  border-radius: 3px;
  font-size: 0.9em;
}
.description-rendered :deep(pre) {
  background: rgba(0, 0, 0, 0.06);
  padding: 0.6em 0.8em;
  border-radius: 4px;
  overflow-x: auto;
}
.description-rendered :deep(pre code) { background: transparent; padding: 0; }
.description-rendered :deep(blockquote) {
  border-left: 3px solid rgba(0, 0, 0, 0.15);
  padding: 0.1em 0.8em;
  margin: 0.4em 0;
  color: rgba(0, 0, 0, 0.7);
}
.description-rendered :deep(a) {
  color: rgb(var(--v-theme-primary));
  text-decoration: none;
}
.description-rendered :deep(a:hover) { text-decoration: underline; }
.description-rendered :deep(table) {
  border-collapse: collapse;
  margin: 0.6em 0;
}
.description-rendered :deep(th),
.description-rendered :deep(td) {
  border: 1px solid rgba(0, 0, 0, 0.15);
  padding: 4px 8px;
}
.description-rendered :deep(th) {
  font-weight: 600;
  background: rgba(0, 0, 0, 0.04);
}
</style>
