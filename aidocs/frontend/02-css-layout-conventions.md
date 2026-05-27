---
stage: concept
last-stage-change: 2026-05-27
purpose: Frontend coding convention — CSS layout utilities vs useDisplay() reactivity
---

# Frontend CSS layout conventions

## Prefer CSS `d-*` utilities over `useDisplay()` for layout

Vuetify's `d-*` utility classes (`d-flex`, `d-sm-none`, `d-md-block`, etc.) are evaluated
at render time by the browser without any reactive JavaScript. `useDisplay()` returns a
reactive ref that triggers a full Vue re-render cycle on every breakpoint change.

**Prefer:** `<div class="d-none d-sm-flex">` (zero runtime overhead, SSR-compatible)  
**Avoid for layout:** `const { mobile } = useDisplay(); v-if="!mobile"`

### When `useDisplay()` IS appropriate

- Single-prop on a closed component that only re-renders when the dialog opens (e.g. `v-dialog :fullscreen="mobile"` — safe, confirmed by BUG #139 analysis; the dialog is closed when the prop changes, so no mid-render reflow occurs).
- Logic branching that cannot be expressed in CSS (e.g. adjusting a chart's height or an API call parameter based on viewport).
- Any place where the reactive value is consumed in `<script setup>` logic (not just `:class`/`:style` bindings).

### Current known usages to watch

| File | Usage | Status |
|---|---|---|
| `frontend/components/context/collection/CollectionSidebar.vue` | `:style="mobile ? ... : ..."` | Low-risk; align to CSS on next touch |
| `frontend/components/*/dialogs/*.vue` (×6) | `v-dialog :fullscreen="mobile"` | Safe — BUG #139 confirmed |

### Rule of thumb

> If you can write it as a Tailwind/Vuetify breakpoint class, do that.  
> Reach for `useDisplay()` when the breakpoint value drives logic, not just visual styling.

## See also

- `aidocs/agent-findings/ux-auditor.md` §Performance
- Vuetify Display composable docs: https://vuetifyjs.com/en/composables/display/
