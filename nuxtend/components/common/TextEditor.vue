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
import { defineProps, mergeProps, ref } from "vue";

interface TextEditorProps {
  placeholderContent?: string;
  isEditable: boolean;
  isPreviewCollapsed?: boolean;
}

const props = defineProps<TextEditorProps>();

const model = defineModel<string>();

const formatListSelection = ref<number | null>(null);
const formatLink = ref<number | null>(null);
const formatMarkerSelection = ref<string[]>([]);

const editorClassObject = computed(() => ({
  "editor-is-editable": props.isEditable,
  "editor-not-editable": !props.isEditable,
  "preview-is-collapsed": !!props.isPreviewCollapsed,
}));

const editor = useEditor({
  content: model.value,
  extensions: [
    StarterKit.configure({
      heading: {
        levels: [1, 2, 3],
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
  onSelectionUpdate({ editor }) {
    // handle editor format list states
    if (editor.isActive("bulletList")) formatListSelection.value = 0;
    else if (editor.isActive("orderedList")) formatListSelection.value = 1;
    else formatListSelection.value = null;

    // handle editor marker states
    const newFormatMarkerSelection = [];
    if (editor.isActive("bold")) newFormatMarkerSelection.push("bold");
    if (editor.isActive("italic")) newFormatMarkerSelection.push("italic");
    if (editor.isActive("underline"))
      newFormatMarkerSelection.push("underline");
    if (editor.isActive("code")) newFormatMarkerSelection.push("code");
    if (editor.isActive("heading", { level: 1 }))
      newFormatMarkerSelection.push("heading1");
    formatMarkerSelection.value = newFormatMarkerSelection;

    if (editor.isActive("link")) formatLink.value = 0;
    else formatLink.value = null;
  },
  injectCSS: true,
});

watch(model, newContent => {
  if (editor.value) {
    const isSame = editor.value.getHTML() === newContent;

    if (isSame) {
      return;
    }
    editor.value.commands.setContent(newContent, false);
  }
});

// Watch for changes to the prop and update the local state
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

const getTextAlignmentIcon = (editor?: Editor): string => {
  if (editor) {
    if (editor.isActive({ textAlign: "left" })) {
      return "mdi-format-align-left";
    }
    if (editor.isActive({ textAlign: "right" })) {
      return "mdi-format-align-right";
    }
    if (editor.isActive({ textAlign: "center" })) {
      return "mdi-format-align-center";
    }
    if (editor.isActive({ textAlign: "justify" })) {
      return "mdi-format-align-justify";
    }
  }
  return "mdi-format-align-left";
};

const setLink = (editor?: Editor) => {
  if (editor) {
    const previousUrl = editor.getAttributes("link").href;
    const url = window.prompt("URL", previousUrl);
    // cancelled
    if (url === null) {
      return;
    }
    // empty
    if (url === "") {
      editor.chain().focus().extendMarkRange("link").unsetLink().run();

      return;
    }
    // update link
    editor.chain().focus().extendMarkRange("link").setLink({ href: url }).run();
  }
};
</script>

<template>
  <div id="editor-container">
    <div v-show="props.isEditable" id="editor-toolbar">
      <!-- Marker Buttons (bold, italic, ...)-->
      <v-btn-toggle
        v-model="formatMarkerSelection"
        variant="plain"
        multiple
        density="comfortable"
        color="primary"
      >
        <v-tooltip text="Bold (Ctrl + b)">
          <template #activator="{ props }">
            <v-btn
              v-bind="props"
              icon="mdi-format-bold"
              class="toolbar-button"
              value="bold"
              @click="editor?.chain().focus().toggleBold().run()"
            />
          </template>
        </v-tooltip>
        <v-tooltip text="Italic (Ctrl + i)">
          <template #activator="{ props }">
            <v-btn
              v-bind="props"
              icon="mdi-format-italic"
              class="toolbar-button"
              value="italic"
              @click="editor?.chain().focus().toggleItalic().run()"
            />
          </template>
        </v-tooltip>
        <v-tooltip text="Underline (Ctrl + u)">
          <template #activator="{ props }">
            <v-btn
              v-bind="props"
              icon="mdi-format-underline"
              class="toolbar-button"
              value="underline"
              @click="editor?.chain().focus().toggleUnderline().run()"
            />
          </template>
        </v-tooltip>
        <v-tooltip text="Code (Ctrl + u)">
          <template #activator="{ props }">
            <v-btn
              v-bind="props"
              icon="mdi-code-tags"
              class="toolbar-button"
              value="code"
              @click="editor?.chain().focus().toggleCode().run()"
            />
          </template>
        </v-tooltip>
        <v-tooltip text="Toggle Heading">
          <template #activator="{ props }">
            <v-btn
              v-bind="props"
              text="Heading"
              class="toolbar-button"
              value="heading1"
              @click="editor?.chain().focus().toggleHeading({ level: 1 }).run()"
            />
          </template>
        </v-tooltip>
      </v-btn-toggle>

      <v-divider vertical />

      <!-- Text Alignment Selection -->
      <v-menu open-on-hover class="toolbar-selection-menu">
        <template #activator="{ isActive, props: menu }">
          <v-tooltip location="top" text="Text Alignment">
            <template #activator="{ props: tooltip }">
              <v-btn
                variant="plain"
                rounded="0"
                v-bind="mergeProps(menu, tooltip)"
              >
                <v-icon>{{ getTextAlignmentIcon(editor) }}</v-icon>
                <v-icon size="small">
                  {{ isActive ? "mdi-chevron-up" : "mdi-chevron-down" }}
                </v-icon>
              </v-btn>
            </template>
          </v-tooltip>
        </template>

        <v-list color="primary" density="compact" class="menu-list">
          <v-tooltip location="top" text="Left Align">
            <template #activator="{ props: tooltip }">
              <v-list-item
                color="primary"
                v-bind="tooltip"
                @click="editor?.chain().focus().setTextAlign('left').run()"
              >
                <v-icon size="small">mdi-format-align-left</v-icon>
              </v-list-item>
            </template>
          </v-tooltip>

          <v-tooltip location="top" text="Center Align">
            <template #activator="{ props: tooltip }">
              <v-list-item
                color="primary"
                v-bind="tooltip"
                @click="editor?.chain().focus().setTextAlign('center').run()"
              >
                <v-icon size="small">mdi-format-align-center</v-icon>
              </v-list-item>
            </template>
          </v-tooltip>

          <v-tooltip location="top" text="Right Align">
            <template #activator="{ props: tooltip }">
              <v-list-item
                color="primary"
                v-bind="tooltip"
                @click="editor?.chain().focus().setTextAlign('right').run()"
              >
                <v-icon size="small">mdi-format-align-right</v-icon>
              </v-list-item>
            </template>
          </v-tooltip>

          <v-tooltip location="top" text="Justify Align">
            <template #activator="{ props: tooltip }">
              <v-list-item
                color="primary"
                v-bind="tooltip"
                @click="editor?.chain().focus().setTextAlign('justify').run()"
              >
                <v-icon size="small">mdi-format-align-justify</v-icon>
              </v-list-item>
            </template>
          </v-tooltip>
        </v-list>
      </v-menu>

      <v-divider vertical />

      <!-- Linking/ Unlinking-->
      <v-btn-toggle
        v-model="formatLink"
        variant="plain"
        color="primary"
        density="comfortable"
      >
        <v-tooltip text="Set Link">
          <template #activator="{ props }">
            <v-btn
              v-bind="props"
              icon="mdi-link"
              class="toolbar-button"
              @click="setLink(editor)"
            />
          </template>
        </v-tooltip>
      </v-btn-toggle>
      <v-tooltip text="Unset Link">
        <template #activator="{ props }">
          <v-btn
            v-bind="props"
            icon="mdi-link-off"
            class="toolbar-button"
            variant="plain"
            :disabled="!editor?.isActive('link')"
            @click="editor?.chain().focus().unsetLink().run()"
          />
        </template>
      </v-tooltip>

      <v-divider vertical />

      <!-- List Selection -->
      <v-btn-toggle
        v-model="formatListSelection"
        variant="plain"
        color="primary"
        density="comfortable"
      >
        <v-tooltip text="Bullet List (Control + Shift + 8)">
          <template #activator="{ props }">
            <v-btn
              v-bind="props"
              value="bullet-list"
              icon="mdi-format-list-bulleted"
              class="toolbar-button"
              @click="editor?.chain().focus().toggleBulletList().run()"
            />
          </template>
        </v-tooltip>
        <v-tooltip text="Numbered List (Control + Shift + 7)">
          <template #activator="{ props }">
            <v-btn
              v-bind="props"
              value="numbered-list"
              icon="mdi-format-list-numbered"
              class="toolbar-button"
              @click="editor?.chain().focus().toggleOrderedList().run()"
            />
          </template>
        </v-tooltip>
      </v-btn-toggle>

      <v-divider vertical />

      <!-- Table Selection -->
      <v-menu open-on-hover class="toolbar-selection-menu">
        <template #activator="{ isActive, props: menu }">
          <v-tooltip location="top" text="Table">
            <template #activator="{ props: tooltip }">
              <v-btn
                variant="plain"
                rounded="0"
                v-bind="mergeProps(menu, tooltip)"
              >
                <v-icon>mdi-table</v-icon>
                <v-icon size="small">
                  {{ isActive ? "mdi-chevron-up" : "mdi-chevron-down" }}
                </v-icon>
              </v-btn>
            </template>
          </v-tooltip>
        </template>

        <v-list color="primary" density="compact" class="menu-list">
          <v-row style="padding: 0; margin: 0">
            <v-col style="padding: 0; margin: 0">
              <v-tooltip location="top" text="Add Table">
                <template #activator="{ props: tooltip }">
                  <v-list-item
                    color="primary"
                    v-bind="tooltip"
                    @click="
                      editor?.commands.insertTable({
                        rows: 2,
                        cols: 2,
                        withHeaderRow: true,
                      })
                    "
                  >
                    <v-icon size="small">mdi-table-plus</v-icon>
                  </v-list-item>
                </template>
              </v-tooltip>
            </v-col>
            <v-col style="padding: 0; margin: 0">
              <v-tooltip location="top" text="Delete Table">
                <template #activator="{ props: tooltip }">
                  <v-list-item
                    color="primary"
                    v-bind="tooltip"
                    @click="editor?.commands.deleteTable()"
                  >
                    <v-icon size="small">mdi-table-cancel</v-icon>
                  </v-list-item>
                </template>
              </v-tooltip>
            </v-col>
          </v-row>
          <v-row style="padding: 0; margin: 0">
            <v-col style="padding: 0; margin: 0">
              <v-tooltip location="top" text="Add Row After">
                <template #activator="{ props: tooltip }">
                  <v-list-item
                    color="primary"
                    v-bind="tooltip"
                    @click="editor?.commands.addRowAfter()"
                  >
                    <v-icon size="small">mdi-table-row-plus-after</v-icon>
                  </v-list-item>
                </template>
              </v-tooltip>
            </v-col>
            <v-col style="padding: 0; margin: 0">
              <v-tooltip location="top" text="Add Row Before">
                <template #activator="{ props: tooltip }">
                  <v-list-item
                    color="primary"
                    v-bind="tooltip"
                    @click="editor?.commands.addRowBefore()"
                  >
                    <v-icon size="small">mdi-table-row-plus-before</v-icon>
                  </v-list-item>
                </template>
              </v-tooltip>
            </v-col>
          </v-row>
          <v-row style="padding: 0; margin: 0">
            <v-col style="padding: 0; margin: 0">
              <v-tooltip location="top" text="Add Column After">
                <template #activator="{ props: tooltip }">
                  <v-list-item
                    color="primary"
                    v-bind="tooltip"
                    @click="editor?.commands.addColumnAfter()"
                  >
                    <v-icon size="small">mdi-table-column-plus-after</v-icon>
                  </v-list-item>
                </template>
              </v-tooltip>
            </v-col>
            <v-col style="padding: 0; margin: 0">
              <v-tooltip location="top" text="Add Column Before">
                <template #activator="{ props: tooltip }">
                  <v-list-item
                    color="primary"
                    v-bind="tooltip"
                    @click="editor?.commands.addColumnBefore()"
                  >
                    <v-icon size="small">mdi-table-column-plus-before</v-icon>
                  </v-list-item>
                </template>
              </v-tooltip>
            </v-col>
          </v-row>
          <v-row style="padding: 0; margin: 0">
            <v-col style="padding: 0; margin: 0">
              <v-tooltip location="top" text="Delete Column">
                <template #activator="{ props: tooltip }">
                  <v-list-item
                    color="primary"
                    v-bind="tooltip"
                    @click="editor?.commands.deleteColumn()"
                  >
                    <v-icon size="small">mdi-table-column-remove</v-icon>
                  </v-list-item>
                </template>
              </v-tooltip>
            </v-col>
            <v-col style="padding: 0; margin: 0">
              <v-tooltip location="top" text="Delete Row">
                <template #activator="{ props: tooltip }">
                  <v-list-item
                    color="primary"
                    v-bind="tooltip"
                    @click="editor?.commands.deleteRow()"
                  >
                    <v-icon size="small">mdi-table-row-remove</v-icon>
                  </v-list-item>
                </template>
              </v-tooltip>
            </v-col>
          </v-row>
        </v-list>
      </v-menu>
    </div>
    <editor-content :editor="editor" :class="editorClassObject" />
  </div>
</template>

<style lang="scss">
#editor-container {
  height: 100%;
  display: flex;
  flex-flow: column nowrap;
  gap: 0.25em;
  margin-left: 1em;
  margin-right: 1em;
}

#editor-toolbar {
  display: flex;
  flex-flow: row wrap;
  width: 100%;
  justify-content: end;
  align-items: center;
}

.editor-is-editable {
  border: 1px solid rgb(var(--v-theme-black-100));
  border-radius: 4px;
}

.editor-not-editable {
  border: none;
}

.preview-is-collapsed {
  max-height: 5lh;
  mask-image: linear-gradient(to bottom, white, transparent);
  overflow: hidden;
}

.menu-list {
  padding: 0;
  margin: 0;
  color: rgb(var(--v-theme-black-400));
}

.menu-list .v-list-item:hover {
  color: rgb(var(--v-theme-blue-400));
}

.tiptap {
  flex: 1;
  min-height: 100px;
  border-radius: 4px;
  padding: 0.25em;

  :first-child {
    margin-top: 0;
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
