<script lang="ts" setup>
import type { Editor } from "@tiptap/vue-3";
import { ref } from "vue";

interface RichTextEditorToolbarProps {
  editor: Editor;
  isToolbarShown: boolean;
  addImageButton?: boolean;
}

const props = defineProps<RichTextEditorToolbarProps>();
const emit = defineEmits(["add-image"]);

const boldState = ref<number | null>(null);
const italicState = ref<number | null>(null);
const underlineState = ref<number | null>(null);
const codeState = ref<number | null>(null);
const headingState = ref<number | null>(null);
const linkState = ref<number | null>(null);
const bulletListState = ref<number | null>(null);
const orderedListState = ref<number | null>(null);
const alignmentMenuIcon = ref<string>("mdi-format-align-left");

onMounted(() => {
  props.editor.on("selectionUpdate", ({ editor }) => {
    if (editor.isActive("bold")) boldState.value = 0;
    else boldState.value = null;

    if (editor.isActive("italic")) italicState.value = 0;
    else italicState.value = null;

    if (editor.isActive("underline")) underlineState.value = 0;
    else underlineState.value = null;

    if (editor.isActive("code")) codeState.value = 0;
    else codeState.value = null;

    if (editor.isActive("heading", { level: 3 })) headingState.value = 0;
    else headingState.value = null;

    if (editor.isActive("link")) linkState.value = 0;
    else linkState.value = null;

    if (editor.isActive({ textAlign: "left" })) {
      alignmentMenuIcon.value = "mdi-format-align-left";
    } else if (editor.isActive({ textAlign: "right" })) {
      alignmentMenuIcon.value = "mdi-format-align-right";
    } else if (editor.isActive({ textAlign: "center" })) {
      alignmentMenuIcon.value = "mdi-format-align-center";
    } else if (editor.isActive({ textAlign: "justify" })) {
      alignmentMenuIcon.value = "mdi-format-align-justify";
    }

    if (editor.isActive("bulletList")) bulletListState.value = 0;
    else bulletListState.value = null;
    if (editor.isActive("orderedList")) orderedListState.value = 0;
    else orderedListState.value = null;
  });
});

const setLink = (editor: Editor): boolean => {
  if (editor) {
    const previousUrl = editor.getAttributes("link").href;
    const url = window.prompt("URL", previousUrl);
    if (url === null) {
      return true;
    }
    if (url === "") {
      editor.chain().focus().extendMarkRange("link").unsetLink().run();
      return true;
    }
    editor.chain().focus().extendMarkRange("link").setLink({ href: url }).run();
    return true;
  } else {
    return false;
  }
};

const tableMenuEntries = [
  {
    iconName: "mdi-table-plus",
    tooltipText: "Create Table",
    callBackFn: () => {
      return props.editor
        .chain()
        .focus()
        .insertTable({
          rows: 2,
          cols: 2,
          withHeaderRow: true,
        })
        .run();
    },
  },
  {
    iconName: "mdi-table-cancel",
    tooltipText: "Delete Table",
    callBackFn: () => {
      return props.editor.chain().focus().deleteTable().run();
    },
  },
  {
    iconName: "mdi-table-row-plus-after",
    tooltipText: "Add Row After",
    callBackFn: () => {
      return props.editor.chain().focus().addRowAfter().run();
    },
  },
  {
    iconName: "mdi-table-row-plus-before",
    tooltipText: "Add Row Before",
    callBackFn: () => {
      return props.editor.chain().focus().addRowBefore().run();
    },
  },
  {
    iconName: "mdi-table-column-plus-after",
    tooltipText: "Add Col After",
    callBackFn: () => {
      return props.editor.chain().focus().addColumnAfter().run();
    },
  },
  {
    iconName: "mdi-table-column-plus-before",
    tooltipText: "Add Col Before",
    callBackFn: () => {
      return props.editor.chain().focus().addColumnBefore().run();
    },
  },
  {
    iconName: "mdi-table-column-remove",
    tooltipText: "Delete Col",
    callBackFn: () => {
      return props.editor.chain().focus().deleteColumn().run();
    },
  },
  {
    iconName: "mdi-table-row-remove",
    tooltipText: "Delete Row",
    callBackFn: () => {
      return props.editor.chain().focus().deleteRow().run();
    },
  },
];

const alignmentMenuEntries = [
  {
    iconName: "mdi-format-align-left",
    tooltipText: "Left",
    callBackFn: () => {
      return props.editor.chain().focus().setTextAlign("left").run();
    },
  },
  {
    iconName: "mdi-format-align-center",
    tooltipText: "Center",
    callBackFn: () => {
      return props.editor.chain().focus().setTextAlign("center").run();
    },
  },
  {
    iconName: "mdi-format-align-right",
    tooltipText: "Right",
    callBackFn: () => {
      return props.editor.chain().focus().setTextAlign("right").run();
    },
  },
  {
    iconName: "mdi-format-align-justify",
    tooltipText: "Justify",
    callBackFn: () => {
      return props.editor.chain().focus().setTextAlign("justify").run();
    },
  },
];
</script>

<template>
  <v-container
    v-if="props.isToolbarShown"
    class="d-flex py-0 gc-2 justify-end pr-0"
  >
    <!-- Marker Controls (bold, italic, ...)-->
    <RichTextEditorToolbarButton
      v-model="boldState"
      :on-click="
        () => {
          return props.editor.chain().focus().toggleBold().run();
        }
      "
      icon="mdi-format-bold"
      tooltip-text="Bold"
    />
    <RichTextEditorToolbarButton
      v-model="italicState"
      :on-click="
        () => {
          return props.editor.chain().focus().toggleItalic().run();
        }
      "
      icon="mdi-format-italic"
      tooltip-text="Italic"
    />
    <RichTextEditorToolbarButton
      v-model="underlineState"
      :on-click="
        () => {
          return props.editor.chain().focus().toggleUnderline().run();
        }
      "
      icon="mdi-format-underline"
      tooltip-text="Underline"
    />
    <RichTextEditorToolbarButton
      v-model="codeState"
      :on-click="
        () => {
          return props.editor.chain().focus().toggleCode().run();
        }
      "
      icon="mdi-code-tags"
      tooltip-text="Code"
    />
    <RichTextEditorToolbarButton
      v-model="headingState"
      :on-click="
        () => {
          return props.editor
            ?.chain()
            .focus()
            .toggleHeading({ level: 3 })
            .run();
        }
      "
      btn-text="Heading"
      tooltip-text="Heading"
    />

    <v-divider vertical />
    <!-- Text Alignment Menu-->
    <RichTextEditorToolbarMenu
      :base-icon="alignmentMenuIcon"
      :menu-entries="alignmentMenuEntries"
      :num-of-menu-cols="1"
      menu-icon-size="small"
      tooltip-text="Alignment"
    />

    <v-divider vertical />
    <!-- Link Controls (set link, unlink)-->
    <RichTextEditorToolbarButton
      v-model="linkState"
      :on-click="
        () => {
          return setLink(editor);
        }
      "
      icon="mdi-link"
      tooltip-text="Set Link"
    />
    <RichTextEditorToolbarButton
      :disabled="linkState === null"
      :is-toggling-disabled="true"
      :on-click="
        () => {
          return props.editor.chain().focus().unsetLink().run();
        }
      "
      icon="mdi-link-off"
      tooltip-text="Unset Link"
    />

    <v-divider vertical />
    <!-- List Controls-->
    <RichTextEditorToolbarButton
      v-model="bulletListState"
      :on-click="
        () => {
          return props.editor.chain().focus().toggleBulletList().run();
        }
      "
      icon="mdi-format-list-bulleted"
      tooltip-text="Bullet List"
    />
    <RichTextEditorToolbarButton
      v-model="orderedListState"
      :on-click="
        () => {
          return props.editor.chain().focus().toggleOrderedList().run();
        }
      "
      icon="mdi-format-list-numbered"
      tooltip-text="Numbered List"
    />

    <v-divider vertical />
    <!-- Table Menu-->
    <RichTextEditorToolbarMenu
      :menu-entries="tableMenuEntries"
      :num-of-menu-cols="2"
      base-icon="mdi-table"
      menu-icon-size="small"
      tooltip-text="Table"
    />
    <v-divider v-if="addImageButton" vertical />
    <RichTextEditorToolbarButton
      v-if="addImageButton"
      :is-toggling-disabled="true"
      icon="mdi-image-plus-outline"
      tooltip-text="Image"
      @click="emit('add-image')"
    />
  </v-container>
</template>
