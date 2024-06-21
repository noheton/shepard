<script setup lang="ts">
import TextAlign from "@tiptap/extension-text-align";
import Underline from "@tiptap/extension-underline";
import StarterKit from "@tiptap/starter-kit";
import { Editor, EditorContent } from "@tiptap/vue-2";
import { onBeforeUnmount, onMounted, ref, watch } from "vue";

const editor = ref<Editor>();

const props = defineProps({
  value: {
    type: String,
    required: true,
  },
  readOnly: {
    type: Boolean,
    default: false,
  },
});

const emits = defineEmits(["input"]);

watch(
  () => props.value,
  value => {
    const isSame = editor.value?.getHTML() === value;
    if (!isSame) editor.value?.commands.setContent(value, false);
  },
);

onMounted(() => {
  editor.value = new Editor({
    content: props.value,
    editable: !props.readOnly,
    extensions: [
      StarterKit,
      Underline,
      TextAlign.configure({
        types: ["heading", "paragraph"],
        alignments: ["left", "right", "center"],
      }),
    ],
    onUpdate: () => {
      emits("input", editor.value?.getHTML());
    },
  });
});

onBeforeUnmount(() => {
  editor.value?.destroy();
});
</script>

<template>
  <div id="texteditor">
    <b-button-toolbar
      v-if="editor && !props.readOnly"
      class="py-1 bg-dark"
      justify
    >
      <b-button-group size="sm" class="mx-1">
        <b-button
          :pressed="editor.isActive('heading', { level: 1 })"
          @click="editor.chain().focus().toggleHeading({ level: 1 }).run()"
        >
          <TypeH1 />
        </b-button>
        <b-button
          :pressed="editor.isActive('heading', { level: 2 })"
          @click="editor.chain().focus().toggleHeading({ level: 2 }).run()"
        >
          <TypeH2 />
        </b-button>
        <b-button
          :pressed="editor.isActive('heading', { level: 3 })"
          @click="editor.chain().focus().toggleHeading({ level: 3 }).run()"
        >
          <TypeH3 />
        </b-button>
        <b-button
          :pressed="editor.isActive('paragraph')"
          @click="editor.chain().focus().setParagraph().run()"
        >
          <TypeText />
        </b-button>
      </b-button-group>
      <b-button-group size="sm" class="mx-1">
        <b-button
          :pressed="editor.isActive('bold')"
          @click="editor.chain().focus().toggleBold().run()"
        >
          <TypeBold />
        </b-button>
        <b-button
          :pressed="editor.isActive('italic')"
          @click="editor.chain().focus().toggleItalic().run()"
        >
          <TypeItalic />
        </b-button>
        <b-button
          :pressed="editor.isActive('strike')"
          @click="editor.chain().focus().toggleStrike().run()"
        >
          <TypeStrike />
        </b-button>
        <b-button
          :pressed="editor.isActive('underline')"
          @click="editor.chain().focus().toggleUnderline().run()"
        >
          <TypeUnderline />
        </b-button>
        <b-button
          :pressed="editor.isActive('code')"
          @click="editor.chain().focus().toggleCode().run()"
        >
          <TypeCode />
        </b-button>
      </b-button-group>
      <b-button-group size="sm" class="mx-1">
        <b-button
          :pressed="editor.isActive({ textAlign: 'left' })"
          @click="editor.chain().focus().setTextAlign('left').run()"
        >
          <TypeLeft />
        </b-button>
        <b-button
          :pressed="editor.isActive({ textAlign: 'center' })"
          @click="editor.chain().focus().setTextAlign('center').run()"
        >
          <TypeCenter />
        </b-button>
        <b-button
          :pressed="editor.isActive({ textAlign: 'right' })"
          @click="editor.chain().focus().setTextAlign('right').run()"
        >
          <TypeRight />
        </b-button>
      </b-button-group>
    </b-button-toolbar>
    <editor-content :editor="editor" />
  </div>
</template>

<style lang="scss">
/* Basic editor styles */
#texteditor {
  border: solid thin var(--info);
  border-radius: 0.15rem;
  background-color: var(--secondary);

  .tiptap {
    padding-left: 0.5rem;
    padding-right: 0.5rem;
  }

  .ProseMirror:focus {
    outline: none;
  }
}
</style>
