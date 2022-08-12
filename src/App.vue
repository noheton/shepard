<template>
  <div id="app">
    <Navbar />
    <b-alert
      :show="errorAlert"
      dismissible
      variant="danger"
      @dismissed="errorAlert = false"
    >
      {{ errorString }}
    </b-alert>
    <b-alert
      :show="extendedErrorAlert"
      dismissible
      variant="danger"
      @dismissed="extendedErrorAlert = false"
    >
      Error while {{ errorSituation }}: <b>{{ errorExeption }}</b>
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
import { defineComponent } from "vue";
import { emitter } from "./utils/event-bus";

export default defineComponent({
  components: { Breadcrumb, Navbar },
  data() {
    return {
      errorString: "",
      errorSituation: "",
      errorExeption: "",
      errorMessage: "",
      errorAlert: false,
      extendedErrorAlert: false,
    };
  },
  created() {
    emitter.on("extendedError", e => {
      this.errorSituation = e.situation;
      this.errorExeption = e.error.exception;
      this.errorMessage = e.error.message;
      this.extendedErrorAlert = true;
    });
    emitter.on("error", e => {
      this.errorString = e;
      this.errorAlert = true;
    });
  },
});
</script>

<style>
body {
  overflow-wrap: break-word;
  word-break: break-word;
}

h3 {
  font-weight: bold;
}

h4 {
  margin-top: 30px;
  margin-bottom: 10px;
}

#app {
  margin-bottom: 20%;
}

.view {
  margin: auto;
  max-width: 850px;
}

.component {
  margin-bottom: 20px;
  margin-top: 20px;
}

.validationField {
  border: solid;
  border-color: red;
  opacity: 0.6;
}
</style>
