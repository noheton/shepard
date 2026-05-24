/**
 * Utilities for rendering docs/*.md files inside the in-app /help route.
 *
 * Docs are written as Jekyll-source markdown, so we need a light pre-pass
 * before handing the text to marked:
 *   1. Strip YAML frontmatter (between the opening --- pair).
 *   2. Resolve `{{ '/path' | relative_url }}` Liquid filters → `/path`.
 *   3. Drop `{% include … %}` and `{% if … %}…{% endif %}` blocks.
 *   4. Drop `{{ site.* }}` variables (replace with empty string).
 *   5. Rewrite internal Jekyll doc links to /help?page=… so navigation
 *      stays in-app and doesn't 404.
 */

import { marked } from "marked";
import hljs from "highlight.js";

// ── Internal link helper ───────────────────────────────────────────────────

/**
 * Convert a Jekyll-style absolute path (e.g. `/reference/file-reference/`)
 * to a docs page key (e.g. `reference/file-reference`) that can be passed
 * as the `?page=` query param.
 *
 * Returns null for anchors, externals, or paths that don't map to a doc page.
 */
function jekyllPathToPage(href: string): string | null {
  if (!href.startsWith("/") || href.startsWith("//")) return null;
  // Strip leading slash and trailing slash
  const stripped = href.replace(/^\/+/, "").replace(/\/+$/, "");
  if (!stripped) return null;
  // Only rewrite paths that look like known docs paths
  if (
    stripped.startsWith("reference/") ||
    stripped.startsWith("help/") ||
    stripped.startsWith("plugins/") ||
    stripped === "admin" ||
    stripped === "architecture" ||
    stripped === "deploy" ||
    stripped === "getting-started" ||
    stripped === "user-guide" ||
    stripped === "system-requirements" ||
    stripped === "showcase"
  ) {
    return stripped;
  }
  return null;
}

// ── Heading slug helper (UI-013) ───────────────────────────────────────────

/**
 * Convert a heading's text into a URL-fragment-safe slug.
 * - lowercase
 * - non-alphanumeric → "-"
 * - collapse repeated "-"
 * - trim leading/trailing "-"
 *
 * Empty result falls back to "section".
 */
export function slugify(text: string): string {
  const slug = (text || "")
    .toLowerCase()
    .normalize("NFKD")
    // strip combining marks (accents) — U+0300..U+036F
    .replace(/[̀-ͯ]/g, "")
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "");
  return slug || "section";
}

/**
 * Collision-aware slug generator. Tracks used slugs and disambiguates
 * duplicates with a numeric suffix.
 */
export class SlugRegistry {
  private used = new Map<string, number>();
  reset() {
    this.used.clear();
  }
  next(text: string): string {
    const base = slugify(text);
    const count = this.used.get(base) ?? 0;
    this.used.set(base, count + 1);
    return count === 0 ? base : `${base}-${count + 1}`;
  }
}

// Module-level registry — reset before each renderDocMarkdown call so slugs
// don't bleed between pages.
const slugRegistry = new SlugRegistry();

// ── Custom marked renderer ─────────────────────────────────────────────────

const renderer = new marked.Renderer();

// Anchored headings (UI-013). Each H2/H3/H4 gets an id="<slug>" and a
// hoverable `#` link. H1 stays unlinked (it's the page title).
// Note: marked v9 passes (text, level, raw).
renderer.heading = (text: string, level: number, raw: string) => {
  if (level <= 1) {
    return `<h${level}>${text}</h${level}>`;
  }
  // `text` is already HTML-rendered (e.g. inline code, emphasis). Strip tags
  // for the slug source — raw is the original markdown, which is cleaner.
  const slugSource = (raw || text).replace(/<[^>]+>/g, "");
  const slug = slugRegistry.next(slugSource);
  // The anchor link itself sits before the heading text; CSS reveals it on
  // heading hover. aria-hidden so screen readers don't read "hash".
  return (
    `<h${level} id="${slug}" class="doc-heading">` +
    `<a class="doc-heading-anchor" href="#${slug}" aria-hidden="true" tabindex="-1">#</a>` +
    `${text}` +
    `</h${level}>`
  );
};

// Open external links in a new tab; rewrite internal Jekyll doc links.
// Note: marked v9 passes individual arguments (href, title, text).
renderer.link = (href: string, title: string | null | undefined, text: string) => {
  if (!href) return text;
  const isExternal = /^https?:\/\//.test(href);
  const titleAttr = title ? ` title="${title}"` : "";
  if (isExternal) {
    return `<a href="${href}"${titleAttr} target="_blank" rel="noopener noreferrer">${text}</a>`;
  }
  const page = jekyllPathToPage(href);
  if (page) {
    return `<a href="/help?page=${encodeURIComponent(page)}"${titleAttr}>${text}</a>`;
  }
  return `<a href="${href}"${titleAttr}>${text}</a>`;
};

// Syntax-highlight fenced code blocks using highlight.js.
// Note: marked v9 passes individual arguments (code, language, escaped).
renderer.code = (code: string, language: string | undefined) => {
  if (language) {
    try {
      const highlighted = hljs.highlight(code, {
        language,
        ignoreIllegals: true,
      }).value;
      return `<pre><code class="hljs language-${language}">${highlighted}</code></pre>`;
    } catch {
      // Unknown language — fall through to plain render
    }
  }
  const escaped = code
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;");
  return `<pre><code>${escaped}</code></pre>`;
};

marked.use({ renderer, gfm: true });

// ── Liquid / frontmatter pre-processor ─────────────────────────────────────

/**
 * Strip Jekyll YAML frontmatter (--- … ---) from the beginning of the file.
 */
function stripFrontmatter(raw: string): string {
  return raw.replace(/^---[\s\S]*?---\r?\n/, "");
}

/**
 * Resolve `{{ '/some/path' | relative_url }}` → `/some/path`.
 *
 * Jekyll-source docs reference site-relative paths (e.g. `/assets/img/photo-aircraft.jpg`),
 * but when the in-app /help route serves them, the docs live mounted at `/docs/...`
 * (see `frontend/public/docs/assets/img/`). Rewrite a leading `/assets/...` to
 * `/docs/assets/...` so the hero photos and bg-title images resolve. UI-007.
 */
function resolveRelativeUrl(text: string): string {
  return text.replace(
    /\{\{\s*['"]([^'"]+)['"]\s*\|\s*relative_url\s*\}\}/g,
    (_match, path: string) => rewriteDocsAssetPath(path),
  );
}

/**
 * Rewrite Jekyll-root asset paths to the in-app /docs/ mount.
 * Idempotent: a path already starting with `/docs/` is returned unchanged.
 */
function rewriteDocsAssetPath(path: string): string {
  if (path.startsWith("/assets/")) return `/docs${path}`;
  return path;
}

/**
 * Drop `{{ site.* }}` variables.
 */
function dropSiteVars(text: string): string {
  return text.replace(/\{\{\s*site\.\w+\s*\}\}/g, "");
}

/**
 * Drop `{% include … %}` and `{% if … %}…{% endif %}` blocks.
 */
function dropLiquidTags(text: string): string {
  return text
    .replace(/\{%-?\s*include\b[^%]*%-?\}/g, "")
    .replace(/\{%-?\s*if\b[\s\S]*?\{%-?\s*endif\s*-?%\}/g, "")
    .replace(/\{%-?\s*\w+[^%]*-?%\}/g, ""); // catch-all for remaining block tags
}

/**
 * Full pre-processing pipeline.
 */
function preprocessMarkdown(raw: string): string {
  let text = stripFrontmatter(raw);
  text = resolveRelativeUrl(text);
  text = dropSiteVars(text);
  text = dropLiquidTags(text);
  return text;
}

// ── Public API ──────────────────────────────────────────────────────────────

/**
 * Wrap each H2-introduced run of content in `<section class="doc-section"
 * data-search-text="…">` so HelpFrame's in-page search can hide non-matching
 * sections by toggling a CSS class without re-rendering the markdown.
 *
 * Content before the first H2 stays unwrapped (intro block, always visible).
 * H1 stays out of any section (it's the page title).
 */
export function wrapSectionsForSearch(html: string): string {
  // Split on H2 boundaries. The opening <h2 id="..." class="doc-heading"> from
  // our renderer is the anchor. We split conservatively: any <h2 …>.
  // First chunk = pre-H2 intro; subsequent chunks each start with an <h2>.
  // No H2 anywhere → nothing to wrap.
  if (!/<h2\b/i.test(html)) return html;
  const parts = html.split(/(?=<h2\b)/i);
  if (parts.length === 0) return html;
  const first = parts[0] ?? "";
  // Normalise: if the first part starts with <h2>, there's no intro block.
  const startsWithH2 = /^<h2\b/i.test(first);
  const intro = startsWithH2 ? "" : first;
  const sections = startsWithH2 ? parts : parts.slice(1);
  const wrapped = sections
    .map(section => {
      // Build the search-text payload from the section's plain text.
      // First, strip the heading-anchor `#` link entirely so it doesn't
      // pollute the search corpus. Then strip remaining tags and
      // collapse whitespace.
      const plain = section
        .replace(/<a\b[^>]*class="doc-heading-anchor"[^>]*>[\s\S]*?<\/a>/g, "")
        .replace(/<[^>]+>/g, " ")
        .replace(/\s+/g, " ")
        .trim()
        .toLowerCase();
      // Escape double-quotes for the data attribute.
      const safe = plain.replace(/"/g, "&quot;");
      return `<section class="doc-section" data-search-text="${safe}">${section}</section>`;
    })
    .join("");
  return intro + wrapped;
}

/**
 * Render a raw docs/*.md string to HTML ready for v-html.
 */
export function renderDocMarkdown(raw: string): string {
  // Reset the slug registry so anchors don't collide across page renders.
  slugRegistry.reset();
  const preprocessed = preprocessMarkdown(raw);
  const html = marked.parse(preprocessed) as string;
  return wrapSectionsForSearch(html);
}

// ── Doc page catalogue ──────────────────────────────────────────────────────

export interface DocPage {
  /** URL-safe key used as `?page=` param, e.g. "reference/file-reference" */
  page: string;
  /** Human-readable title shown in the sidebar */
  title: string;
  /** Path to fetch: `/docs/<page>.md` */
  fetchPath: string;
}

export const DOC_SECTIONS: { header: string; pages: DocPage[] }[] = [
  {
    header: "Getting started",
    pages: [
      { page: "index", title: "Overview", fetchPath: "/docs/index.md" },
      {
        page: "comparison",
        title: "What's new in this fork",
        fetchPath: "/docs/comparison.md",
      },
      {
        page: "getting-started",
        title: "Getting started",
        fetchPath: "/docs/getting-started.md",
      },
      {
        page: "user-guide",
        title: "User guide",
        fetchPath: "/docs/user-guide.md",
      },
      {
        page: "architecture",
        title: "Architecture",
        fetchPath: "/docs/architecture.md",
      },
      {
        page: "system-requirements",
        title: "System requirements",
        fetchPath: "/docs/system-requirements.md",
      },
    ],
  },
  {
    header: "How-to",
    pages: [
      {
        page: "help/upload-data",
        title: "Upload data",
        fetchPath: "/docs/help/upload-data.md",
      },
      {
        page: "help/create-from-template",
        title: "Create from template",
        fetchPath: "/docs/help/create-from-template.md",
      },
      {
        page: "help/publish-data-object",
        title: "Publish a data object",
        fetchPath: "/docs/help/publish-data-object.md",
      },
      {
        page: "help/publish-to-helmholtz-unhide",
        title: "Publish to Helmholtz Unhide",
        fetchPath: "/docs/plugins/unhide/quickstart.md",
      },
      {
        page: "help/monitor-collection-activity",
        title: "Monitor collection activity",
        fetchPath: "/docs/help/monitor-collection-activity.md",
      },
      {
        page: "help/timeseries-plotting",
        title: "Plot timeseries data",
        fetchPath: "/docs/help/timeseries-plotting.md",
      },
      {
        page: "help/collection-lineage",
        title: "Explore collection lineage",
        fetchPath: "/docs/help/collection-lineage.md",
      },
      {
        page: "help/provenance-tracing",
        title: "Trace dataset provenance",
        fetchPath: "/docs/help/provenance-tracing.md",
      },
      {
        page: "help/annotate-container",
        title: "Annotate a timeseries container",
        fetchPath: "/docs/help/annotate-container.md",
      },
      {
        page: "help/delete-container-with-references",
        title: "Delete a container with references",
        fetchPath: "/docs/help/delete-container-with-references.md",
      },
      {
        page: "help/minter-epic-quickstart",
        title: "Mint an ePIC PID",
        fetchPath: "/docs/plugins/minter-epic/quickstart.md",
      },
    ],
  },
  {
    header: "Reference",
    pages: [
      {
        page: "reference/api",
        title: "API",
        fetchPath: "/docs/reference/api.md",
      },
      {
        page: "reference/file-reference",
        title: "File reference",
        fetchPath: "/docs/reference/file-reference.md",
      },
      {
        page: "reference/file-bundle",
        title: "File bundle",
        fetchPath: "/docs/reference/file-bundle.md",
      },
      {
        page: "reference/file-storage",
        title: "File storage",
        fetchPath: "/docs/reference/file-storage.md",
      },
      {
        page: "reference/lab-journal",
        title: "Lab journal",
        fetchPath: "/docs/reference/lab-journal.md",
      },
      {
        page: "reference/snapshots",
        title: "Snapshots",
        fetchPath: "/docs/reference/snapshots.md",
      },
      {
        page: "reference/provenance",
        title: "Provenance",
        fetchPath: "/docs/reference/provenance.md",
      },
      {
        page: "reference/semantic-repositories",
        title: "Semantic repositories",
        fetchPath: "/docs/reference/semantic-repositories.md",
      },
      {
        page: "reference/publish-and-pids",
        title: "Publishing & PIDs",
        fetchPath: "/docs/reference/publish-and-pids.md",
      },
      {
        page: "reference/payload-versioning",
        title: "Payload versioning",
        fetchPath: "/docs/reference/payload-versioning.md",
      },
      {
        page: "reference/user-profile",
        title: "User profile",
        fetchPath: "/docs/reference/user-profile.md",
      },
      {
        page: "reference/video-stream-references",
        title: "Video stream references",
        fetchPath: "/docs/reference/video-stream-references.md",
      },
      {
        page: "reference/admin-cli",
        title: "Admin CLI",
        fetchPath: "/docs/reference/admin-cli.md",
      },
      {
        page: "reference/plugins",
        title: "Plugins",
        fetchPath: "/docs/reference/plugins.md",
      },
    ],
  },
  {
    header: "Plugins",
    pages: [
      {
        page: "plugins/aas/reference",
        title: "AAS (IDTA)",
        fetchPath: "/docs/plugins/aas/reference.md",
      },
      {
        page: "plugins/aas/quickstart",
        title: "AAS — quickstart",
        fetchPath: "/docs/plugins/aas/quickstart.md",
      },
      {
        page: "plugins/aas/install",
        title: "AAS — install",
        fetchPath: "/docs/plugins/aas/install.md",
      },
      {
        page: "plugins/git/reference",
        title: "Git references",
        fetchPath: "/docs/plugins/git/reference.md",
      },
      {
        page: "plugins/hdf5/reference",
        title: "HDF container",
        fetchPath: "/docs/plugins/hdf5/reference.md",
      },
      {
        page: "plugins/unhide/reference",
        title: "Unhide publish",
        fetchPath: "/docs/plugins/unhide/reference.md",
      },
      {
        page: "plugins/unhide/quickstart",
        title: "Unhide — quickstart",
        fetchPath: "/docs/plugins/unhide/quickstart.md",
      },
      {
        page: "plugins/minter-datacite/reference",
        title: "DataCite minter",
        fetchPath: "/docs/plugins/minter-datacite/reference.md",
      },
      {
        page: "plugins/minter-epic/reference",
        title: "ePIC minter",
        fetchPath: "/docs/plugins/minter-epic/reference.md",
      },
      {
        page: "plugins/minter-epic/quickstart",
        title: "ePIC minter — quickstart",
        fetchPath: "/docs/plugins/minter-epic/quickstart.md",
      },
      {
        page: "plugins/minter-epic/install",
        title: "ePIC minter — install",
        fetchPath: "/docs/plugins/minter-epic/install.md",
      },
      {
        page: "plugins/minter-local/reference",
        title: "Local minter",
        fetchPath: "/docs/plugins/minter-local/reference.md",
      },
      {
        page: "plugins/kip/reference",
        title: "KIP resolver",
        fetchPath: "/docs/plugins/kip/reference.md",
      },
      {
        page: "plugins/spatial/reference",
        title: "Spatial data (PostGIS)",
        fetchPath: "/docs/plugins/spatial/reference.md",
      },
      {
        page: "plugins/file-s3/reference",
        title: "S3 file storage",
        fetchPath: "/docs/plugins/file-s3/reference.md",
      },
    ],
  },
  {
    header: "Operations",
    pages: [
      { page: "admin", title: "Admin guide", fetchPath: "/docs/admin.md" },
      { page: "deploy", title: "Deploy", fetchPath: "/docs/deploy.md" },
    ],
  },
];

/** Flat list of all doc pages for lookup by `page` key. */
export const ALL_DOC_PAGES: DocPage[] = DOC_SECTIONS.flatMap(s => s.pages);

/** Find a DocPage by its `page` key. Returns undefined if not found. */
export function findDocPage(pageKey: string): DocPage | undefined {
  return ALL_DOC_PAGES.find(p => p.page === pageKey);
}
