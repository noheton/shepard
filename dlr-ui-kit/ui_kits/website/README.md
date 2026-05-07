# DLR Website UI Kit

A faithful recreation of the DLR institute-page pattern (modeled on `dlr.de` and the **ZLP Augsburg** institute page), built with the canonical color and type tokens from `colors_and_type.css`.

## What's in here

| File | Role |
|---|---|
| `index.html` | Composed institute page — utility bar, header, primary nav, breadcrumbs, hero, sidebar nav, facts strip, card grid, news list, team grid, footer |
| `styles.css` | All component styles, scoped to this kit, importing root tokens |

## Pattern

The page is a typical **DLR institute landing**:
1. Thin utility bar (Karriere / Newsroom / Kontakt + DE/EN)
2. White header with stacked DLR logo, institute identity line, and a subtle search box
3. **Solid blue (`#00658b`) primary nav** — the brand's most recognisable web chrome element
4. Breadcrumbs (`›` separator), then a **photo hero** with bottom-gradient title block
5. Two-column body: left rail with section nav, right column with prose, a **flat-grey facts strip**, content cards, news list, team grid
6. Dark charcoal (`#464646`) footer with linklists, then a thin near-black legal strip

## Components (lifted as patterns)

- `.utility` `.header` `.nav` `.crumbs` — chrome
- `.hero` — full-bleed photo + dark gradient + title overlay
- `.facts` — grey block with big accent-coloured numbers
- `.dlr-card` — hairline border, square corners, eyebrow + title + lede + "Mehr erfahren ›"
- `.news-list` — date column + title + dek
- `.team-grid` — square photo + name/role
- `.footer-ribbon` `.legal`
- `.btn.primary` `.btn.secondary` — square, bold, no radius

## How to use

```html
<link rel="stylesheet" href="ui_kits/website/styles.css">
<!-- Then re-use any pattern markup from index.html -->
```

To switch to a green or yellow chapter context, set `data-variant="b"` or `"c"` on `<html>` — accents inherit from the root tokens.

## Caveats

- **No real photography**. Hero and card images are filled with the POTX background plates (Europe satellite map, blue/green/yellow). Swap in DLR press photography with proper attribution.
- **Search and language toggles are static**. This is a hi-fi mock, not an app.
- **Lucide-style icons inline as raw SVG** for self-containment; production pages should use the official DLR set when available.
