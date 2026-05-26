<script setup lang="ts">
import { FileReferenceApi } from "@dlr-shepard/backend-client";
import { marked } from "marked";
import InformationDialog from "~/components/common/dialog/InformationDialog.vue";
import { useShepardApi } from "~/composables/common/api/useShepardApi";

const MAX_PREVIEW_BYTES = 512 * 1024; // 512 KB

interface TextViewerDialogProps {
  collectionId: number;
  dataObjectId: number;
  fileReferenceId: number;
  oid: string;
  fileName: string;
}
const props = defineProps<TextViewerDialogProps>();

const showDialog = defineModel<boolean>("showDialog", {
  required: true,
  default: false,
});

type ViewState = "loading" | "too-large" | "error" | "ready";

const viewState = ref<ViewState>("loading");
const renderedMarkdown = ref<string>("");
const plainText = ref<string>("");

const isMarkdown = computed(() =>
  props.fileName.split(".").pop()?.toLowerCase() === "md",
);

/** Escape text so it is safe to embed inside a <pre> rendered via v-html. */
function escapeHtml(raw: string): string {
  return raw
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;")
    .replace(/'/g, "&#39;");
}

function loadTextFile() {
  useShepardApi(FileReferenceApi)
    .value.getFilePayload({
      collectionId: props.collectionId,
      dataObjectId: props.dataObjectId,
      fileReferenceId: props.fileReferenceId,
      oid: props.oid,
    })
    .then(async blob => {
      if (blob.size > MAX_PREVIEW_BYTES) {
        viewState.value = "too-large";
        return;
      }
      try {
        const text = await blob.text();
        if (isMarkdown.value) {
          renderedMarkdown.value = marked.parse(text, {
            gfm: true,
            breaks: true,
            async: false,
          }) as string;
        } else {
          plainText.value = escapeHtml(text);
        }
        viewState.value = "ready";
      } catch (error) {
        handleError(error, "Loading text content");
        viewState.value = "error";
      }
    })
    .catch(e => {
      handleError(e, "loading file data");
      viewState.value = "error";
    });
}

loadTextFile();
</script>

<template>
  <InformationDialog
    v-model:show-dialog="showDialog"
    :max-width="900"
    title="File Preview"
  >
    <template #text>
      <!-- Loading -->
      <div
        v-if="viewState === 'loading'"
        class="d-flex justify-center align-center py-8"
      >
        <v-progress-circular indeterminate color="primary" />
      </div>

      <!-- Too large -->
      <v-alert
        v-else-if="viewState === 'too-large'"
        type="info"
        variant="tonal"
        class="my-2"
      >
        File too large to preview (limit: 512 KB). Use the download button to
        access the full content.
      </v-alert>

      <!-- Error -->
      <v-alert
        v-else-if="viewState === 'error'"
        type="error"
        variant="tonal"
        class="my-2"
      >
        Failed to load file content.
      </v-alert>

      <!-- Markdown rendered -->
      <!-- eslint-disable-next-line vue/no-v-html -->
      <div
        v-else-if="isMarkdown"
        class="file-preview-markdown"
        v-html="renderedMarkdown"
      />

      <!-- Plain / code text in a scrollable pre -->
      <!-- eslint-disable-next-line vue/no-v-html -->
      <pre
        v-else
        class="file-preview-code"
        v-html="plainText"
      />
    </template>
  </InformationDialog>
</template>

<style scoped>
/* ── Markdown preview ────────────────────────────────────────────── */
.file-preview-markdown {
  font-size: 14px;
  line-height: 1.6;
  color: rgb(var(--v-theme-on-background));
  width: 100%;
  overflow-x: auto;
}
.file-preview-markdown :deep(h1),
.file-preview-markdown :deep(h2),
.file-preview-markdown :deep(h3),
.file-preview-markdown :deep(h4) {
  font-weight: 600;
  margin: 0.7em 0 0.3em;
}
.file-preview-markdown :deep(h1) { font-size: 1.4em; }
.file-preview-markdown :deep(h2) { font-size: 1.2em; }
.file-preview-markdown :deep(h3) { font-size: 1.05em; }
.file-preview-markdown :deep(p) { margin: 0.4em 0; }
.file-preview-markdown :deep(ul),
.file-preview-markdown :deep(ol) { padding-left: 1.5em; margin: 0.4em 0; }
.file-preview-markdown :deep(code) {
  background: rgba(var(--v-theme-on-background), 0.07);
  padding: 0.1em 0.35em;
  border-radius: 3px;
  font-size: 0.9em;
  font-family: monospace;
}
.file-preview-markdown :deep(pre) {
  background: rgba(var(--v-theme-on-background), 0.07);
  padding: 0.75em 1em;
  border-radius: 4px;
  overflow-x: auto;
  font-size: 0.9em;
}
.file-preview-markdown :deep(pre code) { background: transparent; padding: 0; }
.file-preview-markdown :deep(blockquote) {
  border-left: 3px solid rgba(var(--v-theme-on-background), 0.2);
  padding: 0.1em 0.8em;
  margin: 0.4em 0;
  opacity: 0.75;
}
.file-preview-markdown :deep(a) {
  color: rgb(var(--v-theme-primary));
  text-decoration: none;
}
.file-preview-markdown :deep(a:hover) { text-decoration: underline; }
.file-preview-markdown :deep(table) {
  border-collapse: collapse;
  margin: 0.6em 0;
  width: 100%;
}
.file-preview-markdown :deep(th),
.file-preview-markdown :deep(td) {
  border: 1px solid rgba(var(--v-theme-on-background), 0.2);
  padding: 4px 10px;
}
.file-preview-markdown :deep(th) {
  font-weight: 600;
  background: rgba(var(--v-theme-on-background), 0.05);
}
.file-preview-markdown :deep(hr) {
  border: none;
  border-top: 1px solid rgba(var(--v-theme-on-background), 0.15);
  margin: 1em 0;
}
.file-preview-markdown :deep(img) { max-width: 100%; }

/* ── Plain / code preview ────────────────────────────────────────── */
.file-preview-code {
  font-family: monospace;
  font-size: 13px;
  line-height: 1.5;
  background: rgba(var(--v-theme-on-background), 0.05);
  border: 1px solid rgba(var(--v-theme-on-background), 0.12);
  border-radius: 4px;
  padding: 12px 16px;
  overflow: auto;
  max-height: 70vh;
  white-space: pre;
  word-break: normal;
  color: rgb(var(--v-theme-on-background));
  margin: 0;
}
</style>
