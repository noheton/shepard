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

// ── Custom marked renderer ─────────────────────────────────────────────────

const renderer = new marked.Renderer();

// Open external links in a new tab; rewrite internal Jekyll doc links.
// marked v9 renderer methods use positional args: link(href, title, text).
// eslint-disable-next-line @typescript-eslint/no-explicit-any
(renderer as any).link = (href: string, title: string | null | undefined, text: string) => {
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
// marked v9 renderer methods use positional args: code(code, infostring, escaped).
// eslint-disable-next-line @typescript-eslint/no-explicit-any
(renderer as any).code = (text: string, lang: string | undefined) => {
  if (lang) {
    try {
      const highlighted = hljs.highlight(text, {
        language: lang,
        ignoreIllegals: true,
      }).value;
      return `<pre><code class="hljs language-${lang}">${highlighted}</code></pre>`;
    } catch {
      // Unknown language — fall through to plain render
    }
  }
  // Escape HTML for unknown languages
  const escaped = text
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
 * Resolve `{{ '/some/path' | relative_url }}` → `/some/path`
 */
function resolveRelativeUrl(text: string): string {
  return text.replace(
    /\{\{\s*['"]([^'"]+)['"]\s*\|\s*relative_url\s*\}\}/g,
    (_match, path: string) => path,
  );
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
 * Render a raw docs/*.md string to HTML ready for v-html.
 */
export function renderDocMarkdown(raw: string): string {
  const preprocessed = preprocessMarkdown(raw);
  return marked.parse(preprocessed) as string;
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
        fetchPath: "/docs/help/publish-to-helmholtz-unhide.md",
      },
      {
        page: "help/monitor-collection-activity",
        title: "Monitor collection activity",
        fetchPath: "/docs/help/monitor-collection-activity.md",
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
        page: "reference/git-references",
        title: "Git references",
        fetchPath: "/docs/reference/git-references.md",
      },
      {
        page: "reference/hdf-container",
        title: "HDF container",
        fetchPath: "/docs/reference/hdf-container.md",
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
        page: "reference/minter-datacite",
        title: "DataCite minter",
        fetchPath: "/docs/reference/minter-datacite.md",
      },
      {
        page: "reference/unhide-publish",
        title: "Unhide publish",
        fetchPath: "/docs/reference/unhide-publish.md",
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
