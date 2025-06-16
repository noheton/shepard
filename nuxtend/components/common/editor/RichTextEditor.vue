<script lang="ts" setup>
import CodeBlockLowlight from "@tiptap/extension-code-block-lowlight";
import Link from "@tiptap/extension-link";
import Table from "@tiptap/extension-table";
import TableCell from "@tiptap/extension-table-cell";
import TableHeader from "@tiptap/extension-table-header";
import TableRow from "@tiptap/extension-table-row";
import TextAlign from "@tiptap/extension-text-align";
import Underline from "@tiptap/extension-underline";
import StarterKit from "@tiptap/starter-kit";
import Image from "@tiptap/extension-image";
import type { Editor } from "@tiptap/vue-3";
import { EditorContent, useEditor } from "@tiptap/vue-3";
import "assets/styles/highlightjs.scss";
import { all, createLowlight } from "lowlight";
import { defineProps, type ShallowRef } from "vue";
import { useTheme } from "vuetify";
import type { CodeType } from "./editorTypes";

interface RichTextEditorProps {
  isEditable: boolean;
  isPreviewCollapsed?: boolean;
  autofocus?: boolean;
  codeType?: CodeType;
  canAddImage?: boolean;
}

const props = defineProps<RichTextEditorProps>();
const emit = defineEmits<{
  (e: "add-image"): void;
  (e: "editor-created", value: Editor): void;
}>();

const model = defineModel<string>();

const theme = useTheme();

const editorClassObject = computed(() => ({
  "editor-is-editable": props.isEditable,
  "editor-not-editable": !props.isEditable,
  "preview-is-collapsed": !!props.isPreviewCollapsed,
  "highlightjs-dark-mode": theme.global.current.value.dark,
  "highlightjs-light-mode": !theme.global.current.value.dark,
}));

const editor: ShallowRef<Editor | undefined> = useEditor({
  content: model.value,
  extensions: [
    StarterKit.configure({
      codeBlock: false,
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
    CodeBlockLowlight.configure({
      lowlight: createLowlight(all),
    }),
    TableRow,
    TableHeader,
    TableCell,
    Image,
  ],
  editable: props.isEditable,
  autofocus: props.autofocus ?? false,
  onUpdate: ({ editor }) => {
    model.value = editor.getHTML();
  },
  injectCSS: true,
});

onMounted(() => {
  if (editor.value) {
    emit("editor-created", editor.value);
  }
  if (props.codeType && editor.value) {
    editor.value.commands.setContent(
      `<pre><code class="language-${props.codeType}">${model.value}</code></pre>`,
      false,
    );
  }
});

watch(model, newContent => {
  if (editor.value) {
    const isSame = editor.value.getHTML() === newContent;

    if (isSame) {
      return;
    }
    if (newContent) {
      if (props.codeType) {
        editor.value?.commands.setContent(
          `<pre><code class="language-${props.codeType}">${newContent}</code></pre>`,
          false,
        );
      } else {
        editor.value?.commands.setContent(newContent, false);
      }
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
  <div class="d-flex flex-column flex-nowrap ga-1 w-100">
    <RichTextEditorToolbar
      v-if="editor"
      :add-image-button="canAddImage"
      :editor="editor"
      :is-toolbar-shown="isEditable"
      @add-image="emit('add-image')"
    />
    <editor-content :class="editorClassObject" :editor="editor" />
  </div>
</template>

<style lang="scss" scoped>
.editor-is-editable {
  border: 1px solid rgb(var(--v-theme-divider1));
  border-radius: 4px;

  :deep(.tiptap) {
    min-height: 6lh;
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

// code style for file previews
:deep(.highlightjs-light-mode) > div > pre > code {
  background-color: unset;
  padding: unset;
  border-radius: unset;
}

:deep(.highlightjs-dark-mode) > div > pre > code {
  background-color: unset;
  padding: unset;
  border-radius: unset;
}

:deep(.tiptap) {
  flex: 1;
  border-radius: 4px;

  // these are the 'text body 1' class settings
  p {
    font-size: 1rem;
    font-weight: 400;
    line-height: 1.5rem;
  }

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
