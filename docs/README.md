# shepard docs site

This is the source for the GitHub Pages site under `dlr-shepard/shepard`
(GitHub mirror: `noheton/shepard`). It is a thin Jekyll site with
**no theme and no JS bundler**, dressed in the canonical DLR institute-page
chrome from `dlr-ui-kit/`.

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
    default.html       Single layout — utility / header / nav / breadcrumbs
                       / optional hero / page grid (sidebar opt-in) / footer
  _includes/
    header.html        Utility bar + white header (real DLR logo + search box)
                       + solid-blue primary nav with active-state Liquid
    footer.html        Footer ribbon (4 columns) + thin legal strip
    hero.html          Parameterised hero block (eyebrow / title / lede / bg)
  assets/
    css/main.scss      Adapted from dlr-ui-kit/ui_kits/website/styles.css;
                       :root tokens at the top form the swap-in surface
    img/dlr-logo.svg           Real DLR logo (dark on white, header)
    img/dlr-logo-white.svg     Real DLR logo (white on dark, footer)
    img/bg-title-blue.jpg      Hero background plate (blue)
    img/bg-title-green.jpg     Hero background plate (green)
    img/bg-title-yellow.jpg    Hero background plate (yellow)
  index.md             Landing — hero + use-case .dlr-card grid + .facts strip
  architecture.md      Architecture deep-dive (Mermaid diagram, hero)
  getting-started.md   Python quickstart from input_raw.md:1-90
  user-guide.md        End-user concepts
  admin.md             Operations guide (docker-compose era)
  system-requirements.md  Sizing + supported platforms
  README.md            (this file)
```

## DLR design system applied

The chrome on this site is the canonical DLR institute-page pattern. The
canonical source lives in this repo at `dlr-ui-kit/` (vendored at commit
`8b3245a`); see `dlr-ui-kit/LICENSING.md` for what was committed and what
was deliberately excluded.

### Where the tokens live

`docs/assets/css/main.scss` opens with a `:root` block exposing the
five-value swap-in surface plus a small set of derived tokens:

```scss
:root {
  --color-primary: #00658b;     /* DLR blue */
  --color-utility: #ebebeb;     /* utility bar + facts strip */
  --color-text:    #000000;
  --color-muted:   #464646;     /* footer ribbon */
  --color-border:  #cfcfcf;
  --color-bg:      #ffffff;
  --font-stack:    -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto,
                   "Helvetica Neue", Arial, sans-serif;
}
```

Nothing further down hard-codes a colour — every component reads through
these custom properties, so re-theming for a sister-institute landing is
still a one-block diff. The upstream UI kit's
`@import url('../../colors_and_type.css')` points at a file that was not
part of the design-system upload (see `dlr-ui-kit/LICENSING.md`); the
palette is therefore inlined directly into `main.scss` rather than
imported.

### Components

The kit's components are present as the documented selectors:

- `.utility .header .nav .crumbs` — chrome (utility bar / white header /
  solid-blue primary nav / breadcrumb row).
- `.hero` — full-bleed hero with `bg-title-{blue,green,yellow}.jpg` plates
  and a bottom-gradient title overlay; rendered by `_includes/hero.html`.
- `.page` — 280 px sidebar + 1fr main grid; pages opt in to the sidebar
  via front-matter `sidebar: true`. `.page.no-sidebar` is the default
  single-column layout for short pages.
- `.facts` — grey four-up strip with big accent-coloured numbers (used on
  `index.md`).
- `.dlr-card` `.card-grid` — hairline-bordered card with eyebrow + title +
  lede + "Learn more ›" (used on `index.md`).
- `.footer-ribbon .legal` — four-column dark footer + thin near-black
  legal strip.
- `.btn.primary .btn.secondary` — square, bold, no border-radius.

### Deployer note: Frutiger fonts

The proprietary **Frutiger** TTFs are deliberately not shipped — the
upload included `Frutiger-Light.otf` from `exfont.com` under a "Free for
Personal Use" licence that is not compatible with hosting on a
public-facing institutional site. See `dlr-ui-kit/LICENSING.md` for the
full context. The default font stack on this site is therefore the
system-stack fallback.

If the institutional deployer has a valid Frutiger licence from Linotype
/ Monotype:

1. Drop the licensed `.woff2` and/or `.ttf` files at
   `docs/assets/fonts/Frutiger_light.{woff2,ttf}`,
   `Frutiger_roman.{woff2,ttf}`, `Frutiger_bold.{woff2,ttf}`.
2. Open `docs/assets/css/main.scss` and uncomment the three
   `@font-face` blocks immediately under the `:root` declaration. They
   already point at the paths above.
3. Edit the `--font-stack` line in `:root` to put `"Frutiger"` first.
4. Do not load fonts from third-party CDNs (the on-prem deployment
   posture forbids internet egress at runtime, and the CD office's
   licensing usually requires self-hosting).

### Search box

The search input in the header is **visual-only**. The form submits
to `?search=...` on the same page; nothing is wired up. Adding real
search would mean either Lunr.js (build-time, ships a JSON index) or
Algolia (hosted) — both out of scope for v1.

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
- No external font loaded — the system stack is used until a licensed
  Frutiger family is dropped in.
- No JS framework. Mermaid is loaded only on the architecture page, via a
  conditional `<script>` in the layout.
- No working search. The header form is a visual mock; wiring it requires
  Lunr.js or Algolia (out of scope for v1).
- No theme inheritance. `theme: null` in `_config.yml` makes that explicit.
