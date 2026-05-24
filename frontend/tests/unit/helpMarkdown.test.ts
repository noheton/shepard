import { describe, it, expect } from "vitest";
import {
  renderDocMarkdown,
  findDocPage,
  DOC_SECTIONS,
  ALL_DOC_PAGES,
  slugify,
  SlugRegistry,
  wrapSectionsForSearch,
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

  it("rewrites /assets/ paths to /docs/assets/ (UI-007)", () => {
    // The Jekyll-source docs reference site-relative `/assets/img/foo.jpg`,
    // but inside the in-app /help route the docs are mounted at /docs/, so
    // those paths must be rewritten to /docs/assets/img/foo.jpg.
    const raw =
      "Hero: ![Aircraft]({{ '/assets/img/photo-aircraft.jpg' | relative_url }})";
    const html = renderDocMarkdown(raw);
    expect(html).toContain("/docs/assets/img/photo-aircraft.jpg");
    expect(html).not.toContain('src="/assets/img/photo-aircraft.jpg"');
  });

  it("leaves non-asset relative_url paths unchanged", () => {
    const raw =
      "See [reference]({{ '/reference/api' | relative_url }}) and [more]({{ '/getting-started' | relative_url }})";
    const html = renderDocMarkdown(raw);
    // Should NOT have been double-prefixed with /docs/.
    expect(html).not.toContain("/docs/reference/api");
    expect(html).not.toContain("/docs/getting-started");
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

// ── slugify (UI-013) ───────────────────────────────────────────────────────

describe("slugify", () => {
  it("lowercases and dasherises", () => {
    expect(slugify("Hello World")).toBe("hello-world");
  });

  it("strips punctuation", () => {
    expect(slugify("What's new?")).toBe("what-s-new");
  });

  it("collapses repeated separators", () => {
    expect(slugify("foo   bar___baz")).toBe("foo-bar-baz");
  });

  it("trims leading and trailing dashes", () => {
    expect(slugify("--foo bar--")).toBe("foo-bar");
  });

  it("falls back to 'section' on empty input", () => {
    expect(slugify("")).toBe("section");
    expect(slugify("!!!")).toBe("section");
  });

  it("handles common heading shapes from real docs", () => {
    expect(slugify("Collections")).toBe("collections");
    expect(slugify("DataObjects & references")).toBe("dataobjects-references");
    expect(slugify("REST API surface")).toBe("rest-api-surface");
  });
});

// ── SlugRegistry (UI-013) ──────────────────────────────────────────────────

describe("SlugRegistry", () => {
  it("returns the base slug for the first occurrence", () => {
    const r = new SlugRegistry();
    expect(r.next("Setup")).toBe("setup");
  });

  it("disambiguates collisions with numeric suffixes", () => {
    const r = new SlugRegistry();
    expect(r.next("Setup")).toBe("setup");
    expect(r.next("Setup")).toBe("setup-2");
    expect(r.next("Setup")).toBe("setup-3");
  });

  it("treats different texts that slugify identically as collisions", () => {
    const r = new SlugRegistry();
    expect(r.next("File reference")).toBe("file-reference");
    expect(r.next("File Reference")).toBe("file-reference-2");
  });

  it("reset() clears the collision counter", () => {
    const r = new SlugRegistry();
    r.next("Setup");
    r.next("Setup");
    r.reset();
    expect(r.next("Setup")).toBe("setup");
  });
});

// ── Heading anchor rendering (UI-013) ──────────────────────────────────────

describe("renderDocMarkdown — heading anchors", () => {
  it("adds id + anchor link to H2 headings", () => {
    const html = renderDocMarkdown("## Collections");
    expect(html).toContain('id="collections"');
    expect(html).toContain('class="doc-heading"');
    expect(html).toContain('href="#collections"');
    expect(html).toContain("doc-heading-anchor");
  });

  it("adds id + anchor to H3 headings", () => {
    const html = renderDocMarkdown("### REST API surface");
    expect(html).toContain('id="rest-api-surface"');
    expect(html).toContain('href="#rest-api-surface"');
  });

  it("does NOT add an anchor to H1 (page title)", () => {
    const html = renderDocMarkdown("# Page Title");
    expect(html).toContain("<h1>");
    expect(html).not.toContain("doc-heading-anchor");
  });

  it("disambiguates duplicate H2 slugs within one page", () => {
    const html = renderDocMarkdown("## Setup\n\nfoo\n\n## Setup\n\nbar");
    expect(html).toContain('id="setup"');
    expect(html).toContain('id="setup-2"');
  });

  it("resets the slug counter between renderDocMarkdown calls", () => {
    const a = renderDocMarkdown("## Setup");
    const b = renderDocMarkdown("## Setup");
    expect(a).toContain('id="setup"');
    expect(a).not.toContain('id="setup-2"');
    expect(b).toContain('id="setup"');
    expect(b).not.toContain('id="setup-2"');
  });
});

// ── wrapSectionsForSearch (UI-013) ─────────────────────────────────────────

describe("wrapSectionsForSearch", () => {
  it("wraps each H2-introduced run in a <section data-search-text>", () => {
    const html =
      '<p>intro</p><h2 id="a" class="doc-heading">Alpha</h2><p>some content</p>' +
      '<h2 id="b" class="doc-heading">Beta</h2><p>more</p>';
    const out = wrapSectionsForSearch(html);
    expect(out).toContain('<p>intro</p>');
    expect(out).toContain('<section class="doc-section" data-search-text="');
    // Two H2s → two sections
    expect((out.match(/<section class="doc-section"/g) || []).length).toBe(2);
  });

  it("returns input unchanged when no H2 headings are present", () => {
    const html = "<p>just a paragraph</p>";
    expect(wrapSectionsForSearch(html)).toBe(html);
  });

  it("includes lowercased section text in data-search-text", () => {
    const html =
      '<h2 id="x" class="doc-heading">My Heading</h2><p>Some BODY copy</p>';
    const out = wrapSectionsForSearch(html);
    expect(out).toContain('data-search-text="my heading some body copy"');
  });

  it("escapes double-quotes in section text for the attribute", () => {
    const html = '<h2 id="x" class="doc-heading">Foo</h2><p>has "quotes"</p>';
    const out = wrapSectionsForSearch(html);
    // The attribute itself must close cleanly.
    expect(out).toMatch(/data-search-text="foo has &quot;quotes&quot;"/);
  });

  it("integrates end-to-end with renderDocMarkdown", () => {
    const md = "intro paragraph\n\n## First\n\nfoo body\n\n## Second\n\nbar body";
    const html = renderDocMarkdown(md);
    expect(html).toContain("doc-section");
    expect(html).toContain('data-search-text="first foo body"');
    expect(html).toContain('data-search-text="second bar body"');
  });
});
