<template>
  <div id="app">
    <Navbar />
    <b-alert
      :show="errorAlert"
      dismissible
      variant="danger"
      @dismissed="errorAlert = false"
    >
      Error while {{ errorSituation }}: <b>{{ errorException }}</b>
      <br />
      <small>
        <i>{{ errorMessage }}</i>
      </small>
    </b-alert>
    <Breadcrumb class="view" />
    <router-view :key="$route.fullPath" class="view" />
  </div>
</template>

<script lang="ts">
import Breadcrumb from "@/components/Breadcrumb.vue";
import Navbar from "@/components/Navbar.vue";
import { emitter } from "@/utils/event-bus";
import { defineComponent } from "vue";

export default defineComponent({
  components: { Breadcrumb, Navbar },
  data() {
    return {
      errorSituation: "",
      errorException: "",
      errorMessage: "",
      errorAlert: false,
    };
  },
  created() {
    emitter.on("error", e => {
      this.errorSituation = e.situation;
      this.errorException = e.error.exception;
      this.errorMessage = e.error.message;
      this.errorAlert = true;
    });
  },
});
</script>
