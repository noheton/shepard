<script setup lang="ts">
import getEnv from "@/utils/env";
import type { ApiKeyWithJWT } from "@dlr-shepard/shepard-client";
import { useClipboard } from "@vueuse/core";
import { getCurrentInstance, ref, watch, type PropType } from "vue";

const props = defineProps({
  createdApiKey: {
    type: Object as PropType<ApiKeyWithJWT | undefined>,
    default: undefined,
  },
});

const vm = getCurrentInstance();

const backendUrl = ref<string>(getEnv("VITE_BACKEND"));

const { isSupported, copy } = useClipboard();

watch(
  () => props.createdApiKey,
  () => {
    if (props.createdApiKey) vm?.proxy.$bvModal.show("created-apikey-modal");
  },
);
</script>

<template>
  <b-modal
    id="created-apikey-modal"
    ref="modal"
    title="Created Api Key"
    ok-only
    @ok="$emit('ok')"
  >
    <div v-if="createdApiKey?.jwt">
      Successfully created Api Key named <em> {{ createdApiKey.name }} </em> !
      <div v-if="isSupported">
        <code>
          {{ createdApiKey.jwt.substring(0, 50) + "... " }}
        </code>
        <b-link
          title="Copy Api Key"
          class="float-right"
          @click="copy(createdApiKey.jwt)"
        >
          <CopyIcon />
        </b-link>
      </div>
      <div v-else>
        <code>
          {{ createdApiKey.jwt }}
        </code>
      </div>
    </div>
    <div>
      The shepard backend can be reached via the following URL
      <code>{{ backendUrl }}</code>
      <b-link
        v-if="isSupported"
        title="Copy Backend URL"
        class="float-right"
        @click="copy(backendUrl)"
      >
        <CopyIcon />
      </b-link>
    </div>
  </b-modal>
</template>
