import "@mdi/font/css/materialdesignicons.css";
import { createVuetify, type ThemeDefinition } from "vuetify";
import * as directives from "vuetify/directives";
import { VTreeview } from "vuetify/labs/VTreeview";
import "vuetify/styles";

export default defineNuxtPlugin(app => {
  const vuetify = createVuetify({
    defaults: {
      VBreadcrumbs: {
        color: "blue-500",
        activeColor: "blue-500",
      },
    },
    components: {
      VTreeview,
    },
    directives,
    theme: {
      themes: {
        light: customTheme,
        dark: customTheme,
      },
    },
  });
  app.vueApp.use(vuetify);
});

const customTheme: ThemeDefinition = {
  colors: {
    "black-50": "#E5E5E5",
    "black-100": "#B2B2B2",
    "black-200": "#999999",
    "black-300": "#737373",
    "black-400": "#4D4D4D",
    "black-500": "#262626",
    "black-600": "#000000",
    "black-grey-25": "#F5F7F9",
    "blue-50": "#D9EAF5",
    "blue-100": "#B2D6EB",
    "blue-200": "#8CC1E0",
    "blue-300": "#66ACD6",
    "blue-400": "#4097CC",
    "blue-500": "#0075BB",
    "blue-600": "#00588C",
    "blue-700": "#004670",
    "blue-800": "#003554",
    "blue-900": "#002338",
    "blue-grey-50": "#E9ECEF",
    "blue-grey-100": "#A5AFB7",
    "blue-grey-200": "#7D878E",
    "blue-grey-300": "#5B626A",
    "blue-grey-400": "#3E4347",
    "green-50": "#DFF2E3",
    "green-100": "#BEE5C7",
    "green-200": "#9ED7AB",
    "green-300": "#7ECA8F",
    "green-400": "#5EBD74",
    "green-500": "#28A745",
    "green-600": "#1E7D34",
    "green-700": "#186429",
    "green-800": "#124B1F",
    "green-900": "#0C3215",
    "red-50": "#FAE1E3",
    "red-100": "#F4C2C7",
    "red-200": "#EFA4AB",
    "red-300": "#EA868F",
    "red-400": "#E56874",
    "red-500": "#DC3545",
    "red-600": "#A52834",
    "red-700": "#842029",
    "red-800": "#63181F",
    "red-900": "#421015",
    "yellow-50": "#FFF6DA",
    "yellow-100": "#FFECB5",
    "yellow-200": "#FFE38F",
    "yellow-300": "#FFDA6A",
    "yellow-400": "#FFD145",
    "yellow-500": "#FFC107",
    "yellow-600": "#BF9105",
    "yellow-700": "#997404",
    "yellow-800": "#735703",
    "yellow-900": "#4D3A02",
    "grey-200": "#8c8c8c",
    white: "#FFFFFF",
  },
};
