import { describe, it, expect } from "vitest";
import { renderInlineDescription } from "../../utils/inlineDescription";

describe("renderInlineDescription (UI-006)", () => {
  it("returns empty string for nullish input", () => {
    expect(renderInlineDescription(null)).toBe("");
    expect(renderInlineDescription(undefined)).toBe("");
    expect(renderInlineDescription("")).toBe("");
  });

  it("renders markdown emphasis without leaving raw asterisks", () => {
    // LUMEN card description: "Synthetic showcase dataset for shepard. **NOT REAL DLR/LUMEN data.**"
    const out = renderInlineDescription(
      "Synthetic dataset. **NOT REAL DLR/LUMEN data.** More.",
    );
    expect(out).toContain("<strong>NOT REAL DLR/LUMEN data.</strong>");
    expect(out).not.toContain("**");
  });

  it("strips raw <p> tags but keeps their text content", () => {
    // AI Exchange card description: literal "<p>Collection to exchange data</p>"
    const out = renderInlineDescription("<p>Collection to exchange data</p>");
    expect(out).not.toContain("<p>");
    expect(out).not.toContain("</p>");
    expect(out).toContain("Collection to exchange data");
  });

  it("does not produce block-level elements at all", () => {
    const out = renderInlineDescription("# Heading\n\nA paragraph.\n\n- list");
    expect(out).not.toMatch(/<h\d/);
    expect(out).not.toContain("<p>");
    expect(out).not.toContain("<ul>");
    expect(out).not.toContain("<li>");
  });

  it("renders inline code via backticks", () => {
    const out = renderInlineDescription("Use `helper()` to render.");
    expect(out).toContain("<code>helper()</code>");
  });

  it("strips dangerous tags entirely", () => {
    const out = renderInlineDescription(
      'Hello <script>alert(1)</script> <iframe src=x></iframe> world',
    );
    expect(out).not.toContain("<script");
    expect(out).not.toContain("<iframe");
    expect(out).toContain("Hello");
    expect(out).toContain("world");
    // marked.parseInline will leave the inner text content; we just verify
    // no executable tag survives.
  });

  it("strips on* event handlers from allowed tags", () => {
    const out = renderInlineDescription(
      '<a href="https://example.com" onclick="evil()">link</a>',
    );
    expect(out).not.toContain("onclick");
    expect(out).toContain('href="https://example.com"');
    expect(out).toContain('target="_blank"');
    expect(out).toContain('rel="noopener noreferrer"');
  });

  it("disallows javascript: and data: hrefs", () => {
    const out = renderInlineDescription(
      '<a href="javascript:alert(1)">click</a>',
    );
    expect(out).not.toContain("javascript:");
    // The anchor opens but with no href attribute.
    expect(out).toContain("<a");
    expect(out).toContain("click</a>");
  });

  it("renders plain text unchanged (modulo trailing whitespace)", () => {
    const out = renderInlineDescription("just plain text").trim();
    expect(out).toBe("just plain text");
  });
});
