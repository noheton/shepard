<template>
  <b-modal
    id="created-apikey-modal"
    ref="modal"
    title="Created Api Key"
    ok-only
    @ok="$emit('ok')"
  >
    <div v-if="createdApiKey && createdApiKey.jwt">
      Successfully created Api Key named <em> {{ createdApiKey.name }} </em> !
      <code>
        {{ createdApiKey.jwt.substring(0, 50) + "... " }}
      </code>
      <b-link title="Copy Api Key" class="float-right" @click="copyApiKey()">
        <CopyIcon :size="15" />
      </b-link>
    </div>
  </b-modal>
</template>

<script lang="ts">
import type { ApiKeyWithJWT } from "@dlr-shepard/shepard-client";
import { defineComponent, type PropType } from "vue";

export default defineComponent({
  props: {
    createdApiKey: {
      type: Object as PropType<ApiKeyWithJWT>,
      default: undefined,
    },
  },
  watch: {
    createdApiKey() {
      if (this.createdApiKey) this.$bvModal.show("created-apikey-modal");
    },
  },
  methods: {
    copyApiKey() {
      if (!this.createdApiKey || !this.createdApiKey.jwt) return;
      navigator.clipboard.writeText(this.createdApiKey.jwt);
    },
  },
});
</script>
