<script setup lang="ts">
import Link from "@tiptap/extension-link";
import Table from "@tiptap/extension-table";
import TableCell from "@tiptap/extension-table-cell";
import TableHeader from "@tiptap/extension-table-header";
import TableRow from "@tiptap/extension-table-row";
import TextAlign from "@tiptap/extension-text-align";
import Underline from "@tiptap/extension-underline";
import StarterKit from "@tiptap/starter-kit";
import type { Editor } from "@tiptap/vue-3";
import { EditorContent, useEditor } from "@tiptap/vue-3";
import { defineProps, type ShallowRef } from "vue";

interface TextEditorProps {
  content: string;
}

const props = defineProps<TextEditorProps>();

const editor: ShallowRef<Editor | undefined> = useEditor({
  content: props.content,

  extensions: [
    StarterKit,

    Underline,
    TextAlign.configure({
      types: ["heading", "paragraph"],
    }),
    Link.configure({
      openOnClick: true,
      defaultProtocol: "https",
      autolink: true, // add links when typing
      linkOnPaste: true, // add link on pasted
    }),
    Table.configure({
      resizable: true,
    }),
    TableRow,
    TableHeader,
    TableCell,
  ],
  editable: false,
  autofocus: true,
  injectCSS: true,
});
</script>

<template>
  <editor-content :editor="editor" class="editor-not-editable" />
</template>

<style scoped lang="scss">
.editor-not-editable {
  border: none;
  :deep(.tiptap) {
    padding: 0px;
  }
}

:deep(.tiptap) {
  flex: 1;
  min-height: 6lh;
  border-radius: 4px;

  // We apply the same style for all headlines to support old descriptions with h1/h2.
  h1,
  h2,
  h3 {
    // these are the 'text-subtitle-1' class settings
    font-size: 1.25rem;
    line-height: 2rem;
    font-weight: 600;
  }

  code {
    background-color: rgb(var(--v-theme-divider2));
    border-radius: 0.4rem;
    font-size: 0.85rem;
    padding: 0.25em 0.3em;
  }

  table {
    border-collapse: collapse;
    margin: 0;
    overflow: hidden;
    table-layout: fixed;
    width: 100%;

    td,
    th {
      border: 1px solid rgb(var(--v-theme-textbody2));
      box-sizing: border-box;
      min-width: 1em;
      padding: 6px 8px;
      position: relative;
      vertical-align: top;

      > * {
        margin-bottom: 0;
      }
    }

    th {
      background-color: rgb(var(--v-theme-treeview));
      font-weight: bold;
      text-align: left;
    }

    .selectedCell:after {
      background: rgb(var(--v-theme-divider1));
      content: "";
      left: 0;
      right: 0;
      top: 0;
      bottom: 0;
      pointer-events: none;
      position: absolute;
      z-index: 2;
    }

    .column-resize-handle {
      background-color: rgb(var(--v-theme-primary));
      bottom: -2px;
      pointer-events: none;
      position: absolute;
      right: -2px;
      top: 0;
      width: 4px;
    }
  }

  ul {
    padding: 0 1rem;
    margin: 0.1rem 1rem 0.1rem 0.4rem;
    li p {
      margin-bottom: 0.1rem;
    }
  }

  // handle sub numbers in ordered lists
  ol {
    list-style-type: none;
    counter-reset: item;
    padding: 0;
    margin: 0.1rem 1rem 0.1rem 0.4rem;

    li p {
      margin-bottom: 0.1rem;
    }
  }

  ol > li {
    display: table;
    counter-increment: item;
  }

  ol > li:before {
    content: counters(item, ".") ".";
    display: table-cell;
    padding-right: 0.4em;
  }

  li ol > li {
    margin: 0;
  }

  li ol > li:before {
    content: counters(item, ".") ".";
  }
}
</style>
