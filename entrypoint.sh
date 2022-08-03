#!/bin/sh

# The variables are still named VUE_APP_* to prevent breaking changes affecting the deployment.
# Internally, however, we have to call the variables VITE_*.
JSON_STRING='window.configs = { \
  "VITE_BACKEND":"'"${VUE_APP_BACKEND}"'", \
  "VITE_OIDC_AUTHORITY":"'"${VUE_APP_OIDC_AUTHORITY}"'", \
  "VITE_CLIENT_ID":"'"${VUE_APP_CLIENT_ID}"'" \
}'

sed -i "s@// CONFIGURATIONS_PLACEHOLDER@${JSON_STRING}@" /usr/share/nginx/html/config.js

exec "$@"
