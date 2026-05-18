/**
 * Post-build patch: Nitro's file trace incompletely copies the vue package when
 * vite.ssr.external includes packages that share vue as a dependency.
 * Specifically, index.mjs and index.js are not traced but are required by Node.js
 * ESM resolution (the exports map's "node" condition points to index.mjs).
 * Copy them from the installed source package.
 */
import fs from "node:fs";
import path from "node:path";
import { createRequire } from "node:module";
import { fileURLToPath } from "node:url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));

const outputVueDir = path.resolve(__dirname, "../.output/server/node_modules/vue");

if (!fs.existsSync(outputVueDir)) {
  console.info("[patch-output] .output/server/node_modules/vue not found — skipping");
  process.exit(0);
}

const require = createRequire(import.meta.url);
const vuePkgPath = require.resolve("vue/package.json");
const vueSourceDir = path.dirname(vuePkgPath);

for (const file of ["index.js", "index.mjs"]) {
  const src = path.join(vueSourceDir, file);
  const dest = path.join(outputVueDir, file);
  if (!fs.existsSync(dest) && fs.existsSync(src)) {
    fs.copyFileSync(src, dest);
    console.info(`[patch-output] copied vue/${file}`);
  }
}

// Also ensure dist/vue.cjs.js (dev) exists — index.js requires it in non-prod mode
const distSrc = path.join(vueSourceDir, "dist", "vue.cjs.js");
const distDest = path.join(outputVueDir, "dist", "vue.cjs.js");
if (!fs.existsSync(distDest) && fs.existsSync(distSrc)) {
  fs.copyFileSync(distSrc, distDest);
  console.info("[patch-output] copied vue/dist/vue.cjs.js");
}

console.info("[patch-output] done");
