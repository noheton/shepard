package de.dlr.shepard.context.labJournal.services;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.ext.task.list.items.TaskListItemsExtension;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;

/**
 * J1a — stateless CommonMark + GFM markdown renderer for {@code LabJournalEntry.content}.
 *
 * <p>Parser and renderer are initialised once at class-load time (they are thread-safe
 * per the commonmark-java documentation). Supported extensions:
 * <ul>
 *   <li>GFM tables ({@code | col | col |})</li>
 *   <li>GFM strikethrough ({@code ~~text~~})</li>
 *   <li>GFM task list items ({@code - [x] done})</li>
 * </ul>
 *
 * <p>{@code sanitizeUrls(true)} strips {@code javascript:} hrefs so the
 * rendered HTML is safe to embed directly in a response (additional
 * frame-level isolation is the client's responsibility).
 *
 * <p>Plain-text entries (pre-J1a) render as {@code <p>} elements —
 * CommonMark passes plain text through unchanged, so backwards
 * compatibility requires no data migration.
 *
 * @see <a href="https://github.com/commonmark/commonmark-java">commonmark-java</a>
 */
@ApplicationScoped
public class LabJournalRenderService {

  private static final Parser PARSER = Parser.builder()
    .extensions(
      List.of(TablesExtension.create(), StrikethroughExtension.create(), TaskListItemsExtension.create())
    )
    .build();

  private static final HtmlRenderer RENDERER = HtmlRenderer.builder()
    .extensions(
      List.of(TablesExtension.create(), StrikethroughExtension.create(), TaskListItemsExtension.create())
    )
    .sanitizeUrls(true) // strips javascript: hrefs
    .build();

  /**
   * Render {@code markdown} to sanitised HTML.
   *
   * @param markdown the CommonMark source; may be {@code null} or blank.
   * @return sanitised HTML string, never {@code null}. Returns {@code ""} for
   *         null or blank input.
   */
  public String renderToHtml(String markdown) {
    if (markdown == null || markdown.isBlank()) return "";
    return RENDERER.render(PARSER.parse(markdown));
  }
}
