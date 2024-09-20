import "@mdi/font/css/materialdesignicons.css";
import { createVuetify } from "vuetify";
import * as directives from "vuetify/directives";
import { VTreeview } from "vuetify/labs/VTreeview";
import "vuetify/styles";

export default defineNuxtPlugin(app => {
  const vuetify = createVuetify({
    components: {
      VTreeview,
    },
    directives,
  });
  app.vueApp.use(vuetify);
});
