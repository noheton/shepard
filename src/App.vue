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
    <Breadcrumb class="view" />
    <router-view :key="$route.fullPath" class="view" />
  </div>
</template>

<script lang="ts">
import Breadcrumb from "@/components/Breadcrumb.vue";
import Navbar from "@/components/Navbar.vue";
import { emitter } from "@/utils/event-bus";
import Vue from "vue";

export default Vue.extend({
  components: { Breadcrumb, Navbar },
  data() {
    return {
      errorString: "",
      errorAlert: false,
    };
  },
  created() {
    emitter.on("error", e => {
      this.errorString = e;
      this.errorAlert = true;
    });
  },
});
</script>

<style>
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

h3 {
  font-weight: bold;
}

h4 {
  margin-top: 30px;
  margin-bottom: 10px;
}

.small-button {
  width: 40px;
  height: 40px;
  padding: 0px;
}
</style>
