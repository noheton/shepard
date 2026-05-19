import { describe, it, expect } from "vitest";
import {
  renderDocMarkdown,
  findDocPage,
  DOC_SECTIONS,
  ALL_DOC_PAGES,
} from "../../utils/helpMarkdown";

// ── renderDocMarkdown ──────────────────────────────────────────────────────

describe("renderDocMarkdown", () => {
  it("renders plain markdown to HTML", () => {
    const html = renderDocMarkdown("# Hello\n\nSome **bold** text.");
    expect(html).toContain("<h1");
    expect(html).toContain("Hello");
    expect(html).toContain("<strong>bold</strong>");
  });

  it("strips YAML frontmatter", () => {
    const raw = "---\ntitle: My Doc\nlayout: page\n---\n# Content";
    const html = renderDocMarkdown(raw);
    expect(html).not.toContain("title: My Doc");
    expect(html).not.toContain("layout:");
    expect(html).toContain("Content");
  });

  it("resolves {{ '/path' | relative_url }} liquid filter", () => {
    const raw = "See [link]({{ '/reference/api' | relative_url }})";
    const html = renderDocMarkdown(raw);
    // The filter resolves to /reference/api, which the renderer rewrites as an
    // internal docs link. Verify the liquid syntax is gone and the link is valid.
    expect(html).not.toContain("relative_url");
    expect(html).not.toContain("{{");
    expect(html).toContain("/help?page=reference%2Fapi");
  });

  it("drops {{ site.* }} variables", () => {
    const raw = "Built by {{ site.author }} using {{ site.title }}";
    const html = renderDocMarkdown(raw);
    expect(html).not.toContain("site.author");
    expect(html).not.toContain("site.title");
    expect(html).not.toContain("{{");
  });

  it("drops {% include %} tags", () => {
    const raw = "{% include toc.html %}\n\n# Real content";
    const html = renderDocMarkdown(raw);
    expect(html).not.toContain("include");
    expect(html).toContain("Real content");
  });

  it("opens external links in a new tab", () => {
    const raw = "[Docs](https://example.com/docs)";
    const html = renderDocMarkdown(raw);
    expect(html).toContain('target="_blank"');
    expect(html).toContain('rel="noopener noreferrer"');
  });

  it("rewrites internal Jekyll doc links to /help?page=...", () => {
    const raw = "[Reference](/reference/api/)";
    const html = renderDocMarkdown(raw);
    expect(html).toContain("/help?page=reference%2Fapi");
  });

  it("does not rewrite external links as internal", () => {
    const raw = "[External](https://github.com)";
    const html = renderDocMarkdown(raw);
    expect(html).not.toContain("/help?page=");
    expect(html).toContain("https://github.com");
  });

  it("syntax-highlights fenced code blocks", () => {
    const raw = "```json\n{\"key\": \"value\"}\n```";
    const html = renderDocMarkdown(raw);
    expect(html).toContain("hljs");
    expect(html).toContain("language-json");
  });

  it("renders plain code block when language is unknown", () => {
    const raw = "```unknownlang\nsome code\n```";
    const html = renderDocMarkdown(raw);
    expect(html).toContain("<pre><code>");
    expect(html).toContain("some code");
  });

  it("escapes HTML in plain code blocks", () => {
    const raw = "```\n<script>alert('xss')</script>\n```";
    const html = renderDocMarkdown(raw);
    expect(html).not.toContain("<script>");
    expect(html).toContain("&lt;script&gt;");
  });
});

// ── findDocPage ────────────────────────────────────────────────────────────

describe("findDocPage", () => {
  it("finds a page by exact key", () => {
    const page = findDocPage("reference/api");
    expect(page).toBeDefined();
    expect(page!.title).toBe("API");
    expect(page!.fetchPath).toBe("/docs/reference/api.md");
  });

  it("finds pages in Getting started section", () => {
    const page = findDocPage("getting-started");
    expect(page).toBeDefined();
    expect(page!.title).toBe("Getting started");
  });

  it("finds the index page", () => {
    const page = findDocPage("index");
    expect(page).toBeDefined();
    expect(page!.fetchPath).toBe("/docs/index.md");
  });

  it("returns undefined for unknown key", () => {
    expect(findDocPage("does-not-exist")).toBeUndefined();
  });

  it("returns undefined for empty string", () => {
    expect(findDocPage("")).toBeUndefined();
  });
});

// ── DOC_SECTIONS / ALL_DOC_PAGES ───────────────────────────────────────────

describe("DOC_SECTIONS", () => {
  it("has at least 4 sections", () => {
    expect(DOC_SECTIONS.length).toBeGreaterThanOrEqual(4);
  });

  it("every section has a non-empty header and pages array", () => {
    for (const section of DOC_SECTIONS) {
      expect(section.header).toBeTruthy();
      expect(Array.isArray(section.pages)).toBe(true);
      expect(section.pages.length).toBeGreaterThan(0);
    }
  });

  it("every page has a page key, title, and fetchPath", () => {
    for (const section of DOC_SECTIONS) {
      for (const page of section.pages) {
        expect(page.page).toBeTruthy();
        expect(page.title).toBeTruthy();
        expect(page.fetchPath).toMatch(/^\/docs\//);
      }
    }
  });
});

describe("ALL_DOC_PAGES", () => {
  it("is the flat union of all section pages", () => {
    const total = DOC_SECTIONS.reduce((sum, s) => sum + s.pages.length, 0);
    expect(ALL_DOC_PAGES.length).toBe(total);
  });

  it("has no duplicate page keys", () => {
    const keys = ALL_DOC_PAGES.map(p => p.page);
    const unique = new Set(keys);
    expect(unique.size).toBe(keys.length);
  });

  it("includes the three previously-missing how-to pages", () => {
    expect(findDocPage("help/annotate-container")).toBeDefined();
    expect(findDocPage("help/delete-container-with-references")).toBeDefined();
    expect(findDocPage("help/minter-epic-quickstart")).toBeDefined();
  });
});
