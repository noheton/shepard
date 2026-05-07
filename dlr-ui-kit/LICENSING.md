# dlr-ui-kit — licensing notes

This folder vendors a faithful recreation of the DLR institute-page
pattern (modeled on `dlr.de` and the ZLP-Augsburg institute page),
extracted from a Design-System-Remix bundle on 2026-05-07. It is
the canonical source for the DLR-styled GitHub Pages site under
`docs/`.

## What is committed here

- `ui_kits/website/README.md` — original kit README.
- `ui_kits/website/index.html` — composed institute page (utility bar
  / header / nav / breadcrumbs / hero / sidebar / facts / cards /
  news / team / footer).
- `ui_kits/website/styles.css` — component styles. **Note**: the
  upstream `@import url('../../colors_and_type.css')` references a
  file that was **not** part of the upload and is **not** present in
  this kit. The site under `docs/` therefore inlines the canonical
  palette tokens (primary blue `#00658b`, utility-grey `#ebebeb`,
  text-grey `#464646`, border-grey `#cfcfcf`) directly into
  `docs/assets/css/main.scss` rather than importing them.
- `assets/bg-title-blue.jpg`, `bg-title-green.jpg`,
  `bg-title-yellow.jpg` — the three hero-background plates the kit
  references.
- `assets/dlr-logo.svg`, `dlr-logo-white.svg` — the DLR logo (dark on
  white, white on dark) for header and footer use.

## What is *not* committed here, and why

1. **Frutiger TTFs** (`Frutiger.ttf`, `Frutiger_bold.ttf`,
   `Frutiger_light.ttf`, `Frutiger_roman.ttf`) — the upload included
   a `Frutiger-Light.otf` from `exfont.com` accompanied by an
   `exFont-License.txt` reading verbatim:

   > License: Free for Personal Use
   > Link: https://exfont.com/frutiger-light.font

   "Free for Personal Use" is **not** compatible with hosting on a
   public-facing institutional site (Frutiger is owned by Linotype /
   Monotype; institutional / commercial use requires a licence
   purchased from Monotype). The fonts are therefore **excluded
   from this repository**. The site CSS uses a system-font stack as
   the default and includes commented-out `@font-face` slots that an
   institutional deployer with a valid Frutiger licence can fill by
   dropping the licensed `.ttf` / `.woff2` files at
   `docs/assets/fonts/` and uncommenting the corresponding `src:`
   lines in `docs/assets/css/main.scss`.

2. **`potx2-image*` PowerPoint extracts** — twelve files in the
   upload that duplicate or are unused next to the canonical
   `bg-title-*.jpg`, `master-bg-*.jpg`, `satellite-bg-*.jpg` files.
   Only the `bg-title-*` triple is referenced by the kit's `styles.css`
   and `index.html`; the rest were dropped to keep the repo lean.

3. **Photographs** (`photo-aircraft.jpg`, `photo-satellite.jpg`,
   `photo-solar.jpg`) — likely DLR press photography. **Added back
   on 2026-05-07** for the docs/ site's splash landing — committed
   here as the canonical source and copied to `docs/assets/img/`
   for the runtime path. `photo-aircraft.jpg` is the default index
   hero; the other two banner the use-case cards. Hosted in this
   repo on the same brand-mark argument as the DLR logo SVGs (this
   is the GitHub mirror of the DLR-owned source repo). If the
   mirror's posture changes, treat them like the logo and remove or
   replace.

## Editorial / brand caveats inherited from the upstream README

- **No real DLR press photography ships in the kit**. Hero plates
  (`bg-title-*.jpg`) are POTX background gradients, not photographs.
- **Search and language toggles in the upstream `index.html` are
  static**. They are visual mocks, not functional widgets.
- **Lucide-style icons** appear as inline SVG in the upstream
  `index.html`. The docs site does not require icons in this round;
  if added later, prefer the official DLR icon set when available.

## Brand-mark caveat (DLR logo)

The DLR logo SVG / PNG files are registered marks of the
**Deutsches Zentrum für Luft- und Raumfahrt e.V.** Hosting them in
this repository (`noheton/shepard`, the GitHub mirror of the
DLR-owned `gitlab.com/dlr-shepard/shepard`) is consistent with the
mirror's institutional context. If the GitHub mirror's posture
changes (e.g. forked / re-licensed), the logo files should be
removed and replaced with a placeholder, mirroring the system the
docs site already uses.

## Where the docs site picks this up

`docs/_layouts/default.html` and `docs/_includes/header.html` /
`footer.html` apply the kit patterns. `docs/assets/css/main.scss`
inlines the palette tokens at the top of the file (see CSS
swap-in protocol noted in `docs/README.md`) and absorbs the kit's
component CSS below. The hero backgrounds and logo SVGs are
referenced relative to `docs/`'s own `assets/` so the site builds
without depending on this `dlr-ui-kit/` folder at runtime — this
folder is the canonical *source*, not the *runtime asset path*.
