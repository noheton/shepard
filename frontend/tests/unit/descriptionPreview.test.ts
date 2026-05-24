import { describe, it, expect } from "vitest";
import { descriptionPreview } from "../../utils/helpers";

describe("descriptionPreview", () => {
  it("returns empty string for null / undefined / empty", () => {
    expect(descriptionPreview(null)).toBe("");
    expect(descriptionPreview(undefined)).toBe("");
    expect(descriptionPreview("")).toBe("");
  });

  it("returns the text verbatim when shorter than maxChars and free of markdown", () => {
    expect(descriptionPreview("A short note")).toBe("A short note");
  });

  it("strips bold/italic/strike markers", () => {
    expect(descriptionPreview("This is **bold** and _italic_ and ~~stricken~~"))
      .toBe("This is bold and italic and stricken");
  });

  it("does not retain literal asterisks from bold markers", () => {
    const out = descriptionPreview("**Important** notice");
    expect(out).not.toContain("**");
    expect(out).toBe("Important notice");
  });

  it("strips inline code backticks", () => {
    expect(descriptionPreview("call `foo()` to invoke")).toBe("call foo() to invoke");
  });

  it("drops fenced code blocks entirely", () => {
    const out = descriptionPreview("Intro\n\n```js\nconst x = 1;\n```\n\nOutro");
    expect(out).toBe("Intro Outro");
  });

  it("converts markdown links to text only", () => {
    expect(descriptionPreview("see [the docs](https://example.org) here"))
      .toBe("see the docs here");
  });

  it("converts markdown images to alt text only", () => {
    expect(descriptionPreview("hero: ![banner image](http://x/y.png) below"))
      .toBe("hero: banner image below");
  });

  it("strips heading hash markers", () => {
    expect(descriptionPreview("# Title\n\nBody text")).toBe("Title Body text");
  });

  it("strips blockquote markers", () => {
    expect(descriptionPreview("> quoted line\nfollowed by prose"))
      .toBe("quoted line followed by prose");
  });

  it("strips list bullets and ordered markers", () => {
    expect(descriptionPreview("- one\n- two\n- three"))
      .toBe("one two three");
    expect(descriptionPreview("1. first\n2. second"))
      .toBe("first second");
  });

  it("strips HTML tags", () => {
    expect(descriptionPreview("hello <b>world</b>!")).toBe("hello world !");
  });

  it("collapses internal whitespace", () => {
    expect(descriptionPreview("a\n\n\n   b\t\tc")).toBe("a b c");
  });

  it("clamps to maxChars and appends an ellipsis", () => {
    const long = "lorem ipsum dolor sit amet ".repeat(20);
    const out = descriptionPreview(long, 40);
    // Truncation produces ≤ maxChars of content + ellipsis (single char).
    expect(out.length).toBeLessThanOrEqual(41);
    expect(out.endsWith("…")).toBe(true);
  });

  it("truncates at a word boundary when a late space is available", () => {
    const out = descriptionPreview(
      "this is a sentence that exceeds the limit cleanly",
      30,
    );
    // The cut should not split a word — last char before ellipsis is a letter.
    expect(out).toMatch(/\w…$/);
    expect(out.length).toBeLessThanOrEqual(31);
  });

  it("does not truncate when text is shorter than maxChars", () => {
    const out = descriptionPreview("just under", 120);
    expect(out).toBe("just under");
    expect(out.endsWith("…")).toBe(false);
  });

  it("removes trailing punctuation before the ellipsis", () => {
    const out = descriptionPreview("hello world, more, more, more", 18);
    expect(out).toMatch(/…$/);
    expect(out).not.toMatch(/[.,;:!?-]…$/);
  });
});
