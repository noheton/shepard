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
  isEditable: boolean;
  isPreviewCollapsed?: boolean;
}

const props = defineProps<TextEditorProps>();

const model = defineModel<string>();

const editorClassObject = computed(() => ({
  "editor-is-editable": props.isEditable,
  "editor-not-editable": !props.isEditable,
  "preview-is-collapsed": !!props.isPreviewCollapsed,
}));

const editor: ShallowRef<Editor | undefined> = useEditor({
  content: model.value,
  extensions: [
    StarterKit.configure({
      heading: {
        levels: [3],
      },
    }),
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
  editable: props.isEditable,
  autofocus: true,
  onUpdate: ({ editor }) => {
    model.value = editor.getHTML();
  },
  injectCSS: true,
});

watch(model, newContent => {
  if (editor.value) {
    const isSame = editor.value.getHTML() === newContent;

    if (isSame) {
      return;
    }
    if (newContent) {
      editor.value?.commands.setContent(newContent, false);
    }
  }
});

watch(
  () => props.isEditable,
  newIsEditState => {
    if (editor.value) {
      editor.value.setEditable(newIsEditState);
      if (newIsEditState) {
        editor.value.commands.focus();
      }
    }
  },
);
</script>

<template>
  <div class="h-100 d-flex flex-column flex-nowrap ga-1 mx-4">
    <CommonEditorToolbar
      v-if="editor"
      :editor="editor"
      :is-toolbar-shown="isEditable"
    />
    <editor-content :editor="editor" :class="editorClassObject" />
  </div>
</template>

<style scoped lang="scss">
.editor-is-editable {
  border: 1px solid rgb(var(--v-theme-black-100));
  border-radius: 4px;
  margin: 4px;
  :deep(.tiptap) {
    padding: 8px;
  }
}

.editor-not-editable {
  border: none;
  :deep(.tiptap) {
    padding: 0px;
  }
}

.preview-is-collapsed {
  max-height: 6lh;
  mask-image: linear-gradient(to bottom, white, transparent);
  overflow: hidden;
}

:deep(.tiptap) {
  flex: 1;
  min-height: 6lh;
  border-radius: 4px;

  h3 {
    // These are the typography settings for 'subtitle-1' semi bold
    font-size: 1.25rem;
    line-height: 2rem;
    font-weight: 600;
  }

  code {
    background-color: rgb(var(--v-theme-black-100));
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
      border: 1px solid rgb(var(--v-theme-black-300));
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
      background-color: rgb(var(--v-theme-blue-grey-50));
      font-weight: bold;
      text-align: left;
    }

    .selectedCell:after {
      background: rgb(var(--v-theme-black-100));
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
      background-color: rgb(var(--v-theme-blue-500));
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
