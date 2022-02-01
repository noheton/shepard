<template>
  <b-modal
    id="created-apikey-modal"
    ref="modal"
    title="Created Api Key"
    ok-only
    @ok="$emit('ok')"
  >
    <div v-if="createdApiKey">
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
import { ApiKeyWithJWT } from "@dlr-shepard/shepard-client";
import Vue, { PropType } from "vue";

export default Vue.extend({
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
