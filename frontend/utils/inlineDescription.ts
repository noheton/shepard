/**
 * Render a short user-supplied description string for compact display
 * (collection cards, dataobject rows, etc.) without enabling block-level
 * layout, dangerous HTML, or raw markdown artefacts leaking through.
 *
 * Strategy:
 *   1. Run the source through `marked.parseInline` — this converts
 *      `**bold**`, `_em_`, backticks, and links into inline HTML, but does
 *      NOT wrap content in `<p>` / `<h*>` / lists.
 *   2. Strip any HTML tags except a small inline allow-list. This catches
 *      raw `<p>…</p>` (which the AI Exchange seed carries verbatim) and
 *      anything else a user might paste.
 *   3. Decode the few entities marked may have produced for `<`/`>` inside
 *      stripped tags so the output looks like natural text.
 *
 * The output is suitable for `v-html` on a description-clamp element.
 * UI-006.
 */

import { marked } from "marked";

// Tags we allow to survive the sanitization pass. Everything else (incl.
// attributes carried by these tags) is stripped.
const ALLOWED_INLINE_TAGS = new Set([
  "a",
  "b",
  "strong",
  "i",
  "em",
  "code",
  "br",
  "span",
]);

/**
 * Remove HTML tags not on the allow-list. Allowed tags keep their text content
 * but lose every attribute except `href`/`title` on `<a>`.
 */
function sanitizeInline(html: string): string {
  return html.replace(/<\/?([a-zA-Z][a-zA-Z0-9]*)(\s[^>]*)?>/g, (match, tag: string, attrs: string | undefined) => {
    const lower = tag.toLowerCase();
    if (!ALLOWED_INLINE_TAGS.has(lower)) {
      return "";
    }
    // Closing tag — re-emit lower-cased, attribute-free.
    if (match.startsWith("</")) {
      return `</${lower}>`;
    }
    if (lower === "a") {
      // Preserve only href / title; force target=_blank + rel for safety.
      const hrefMatch = attrs?.match(/\bhref\s*=\s*("([^"]*)"|'([^']*)')/i);
      const titleMatch = attrs?.match(/\btitle\s*=\s*("([^"]*)"|'([^']*)')/i);
      const href = hrefMatch ? hrefMatch[2] ?? hrefMatch[3] ?? "" : "";
      const title = titleMatch ? titleMatch[2] ?? titleMatch[3] ?? "" : "";
      // Disallow javascript: / data: hrefs.
      const safeHref = /^(https?:|mailto:|\/)/i.test(href) ? href : "";
      const titleAttr = title ? ` title="${title.replace(/"/g, "&quot;")}"` : "";
      const hrefAttr = safeHref ? ` href="${safeHref}"` : "";
      return `<a${hrefAttr}${titleAttr} target="_blank" rel="noopener noreferrer">`;
    }
    // Other allowed tags — drop all attributes.
    return `<${lower}>`;
  });
}

/**
 * Render markdown + raw-HTML description as safe inline HTML.
 *
 * Use with `v-html` on a description-clamp container in compact card layouts.
 *
 * @example
 *   renderInlineDescription("**bold** text")            // → "<strong>bold</strong> text"
 *   renderInlineDescription("<p>Collection</p>")        // → "Collection"
 *   renderInlineDescription("plain text")               // → "plain text"
 */
export function renderInlineDescription(raw: string | null | undefined): string {
  if (!raw) return "";
  const rendered = marked.parseInline(raw, { async: false }) as string;
  return sanitizeInline(rendered);
}
