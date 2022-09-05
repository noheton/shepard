import log from "loglevel";
log.setDefaultLevel("info");

import Vue from "vue";
import App from "./App.vue";
import "./plugins/bootstrap-vue";
import router from "./utils/router";
import store from "./utils/vuex-store";

new Vue({
  router,
  store,
  render: h => h(App),
}).$mount("#app");
