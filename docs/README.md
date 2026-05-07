# shepard docs site

This is the source for the GitHub Pages site under `dlr-shepard/shepard`
(GitHub mirror: `noheton/shepard`). It is intentionally a thin Jekyll site —
no theme, no Sass framework, no JS bundler — so the canonical DLR
Corporate-Design swap-in stays a one-file diff.

## Preview locally

Requires Ruby (3.x) and Bundler. If you already have those installed:

```bash
gem install bundler jekyll
cd docs
bundle init
bundle add jekyll
bundle exec jekyll serve --livereload
# open http://127.0.0.1:4000
```

If you do **not** have Ruby and Bundler on your machine, the above will not
work without an extra system-level install (e.g. `apt install ruby-full
build-essential` on Debian/Ubuntu, or `brew install ruby` on macOS).

The site also builds without local Ruby — `.github/workflows/pages.yml`
runs the same `actions/jekyll-build-pages` action that GitHub Pages uses
in production, so push-to-preview is reliable even without local tooling.

## File layout

```
docs/
  _config.yml          Jekyll config; nav list; snapshot date
  _layouts/
    default.html       Single layout (~25 lines) — header, main, footer
  _includes/
    header.html        Top nav with placeholder logo slot
    footer.html        CD-pending disclaimer + links
  assets/
    css/main.scss      One CSS file with :root custom properties at the top
    img/logo-placeholder.svg  Neutral generic mark — NOT the DLR logo
  index.md             Landing — overview + use cases + nav-by-role
  architecture.md      Architecture deep-dive (Mermaid diagram)
  getting-started.md   Python quickstart from input_raw.md:1-90
  user-guide.md        End-user concepts
  admin.md             Operations guide (docker-compose era)
  system-requirements.md  Sizing + supported platforms
  README.md            (this file)
```

## Corporate-Design swap protocol

The site ships a placeholder visual treatment because the canonical DLR
Corporate-Design assets (logo SVG, official palette, fonts, Motion-CI rules)
are **not** in this repository. When the assets are obtained from DLR
Corporate Communications / Marken-Portal:

1. **Replace the four colour custom properties + the font stack** at the top
   of `docs/assets/css/main.scss`:

   ```scss
   :root {
     --color-primary: …;   /* canonical primary */
     --color-accent:  …;   /* canonical accent */
     --color-text:    …;
     --color-bg:      …;
     --font-stack:    "<canonical font>", system-ui, sans-serif;
   }
   ```

   Nothing below that block hard-codes a colour or font; the swap is a
   one-file diff.

2. **Replace the placeholder logo** at
   `docs/assets/img/logo-placeholder.svg` with the canonical SVG. The
   `<header>` slot in `_includes/header.html` already references the file by
   relative URL — no template change needed if you keep the filename. If you
   prefer a PNG, update the `<img src>` in `_includes/header.html`.

3. **Remove the disclaimer paragraph** in `_includes/footer.html` and replace
   the placeholder Standorte / Impressum slots with the canonical copy.

4. **Optional fonts** — if the canonical font requires a self-hosted
   `@font-face`, drop the woff2 files under `docs/assets/fonts/` and add a
   single `@font-face` block at the top of `main.scss`. Do not load fonts
   from third-party CDNs (the on-prem deployment posture forbids internet
   egress at runtime, and the CD office's licensing usually requires
   self-hosting).

The full CD-compliance discipline is documented in
`aidocs/33-frontend-workflow-analysis.md §7` (forthcoming at this snapshot
date — link will resolve once that doc is committed).

## Branch posture for GitHub Pages

The workflow at `.github/workflows/pages.yml` builds on push to the
**dispatcher branch** (the long-running `worktree-agent-*` branch this PR
ships from) so a maintainer can preview the rendered site before merging.
Once the PR is merged into the repository's primary branch, edit the `on:
push: branches:` list in the workflow to match (typically `main` or
`develop`) and **enable GitHub Pages** in repo Settings → Pages, choosing
"GitHub Actions" as the source.

## Screenshot capture plan

Pages that benefit from screenshots include placeholder figures
(`<figure class="screenshot-placeholder">`) sized 16:9 with dashed borders so
the layout reflows correctly without an image. Replace these in a follow-up
once the Playwright capture workflow lands — the recommended approach is to
script captures of the staging frontend and check the resulting PNGs into
`docs/assets/img/screenshots/`. This is the Playwright-recommendation path
described in `aidocs/33 §6` (forthcoming).

## What this site deliberately does not do

- No emojis, anywhere.
- No animations beyond the browser default link-hover.
- No external font loaded — the system stack is used until canonical fonts
  arrive.
- No JS framework. Mermaid is loaded only on the architecture page, via a
  conditional `<script>` in the layout.
- No theme inheritance. `theme: null` in `_config.yml` makes that explicit.
