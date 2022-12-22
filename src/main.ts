import log from "loglevel";
log.setDefaultLevel("info");

import Vue from "vue";
import App from "./App.vue";
import "./plugins/bootstrap-vue";
import router from "./router";
import store from "./store";

new Vue({
  router,
  store,
  render: h => h(App),
}).$mount("#app");
