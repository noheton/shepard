import "@mdi/font/css/materialdesignicons.css";
import { createVuetify, type ThemeDefinition } from "vuetify";
import * as directives from "vuetify/directives";
import { VTreeview } from "vuetify/labs/VTreeview";
import "vuetify/styles";

export default defineNuxtPlugin(app => {
  const vuetify = createVuetify({
    defaults: {
      VBreadcrumbs: {
        color: "primary",
        activeColor: "primary",
      },
    },
    components: {
      VTreeview,
    },
    directives,
    theme: {
      themes: {
        light: lightTheme,
        dark: darkTheme,
      },
    },
  });
  app.vueApp.use(vuetify);
});

const lightTheme: ThemeDefinition = {
  colors: {
    primary: "#0075bb",
    textbody1: "#262626",
    textbody2: "#5b626a",
    "high-emphasis": "#a52834",
    "medium-emphasis": "#3e4347",
    "low-emphasis": "#7d878e",
    divider1: "#a5afb7",
    divider2: "#f5f7f9",
    focus1: "#b2d6eb",
    focus2: "#66acd6",
    treeview: "#e9ecef",
    canvas: "#ffffff",
    error: "#dc3545",
    "error-background": "#f4c2c7",
    warning: "#ffc107",
    success: "#28a745",
  },
};
const darkTheme: ThemeDefinition = {
  colors: {
    primary: "#00c7e3",
    textbody1: "#ffffff",
    textbody2: "#f5f7f9",
    "high-emphasis": "#a52834",
    "medium-emphasis": "#ffffff",
    "low-emphasis": "#a5afb7",
    divider1: "#f5f7f9",
    divider2: "#3e4347",
    focus1: "#e5f9fc",
    focus2: "#99e9f4",
    treeview: "#323d49",
    canvas: "#272d33",
    error: "#dc3545",
    "error-background": "#f4c2c7",
    warning: "#ffc107",
    success: "#28a745",
  },
};
