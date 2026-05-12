package de.dlr.shepard.common.util;

import java.util.List;
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;

public class MarkdownRenderer {

  private static final Parser PARSER = Parser
    .builder()
    .extensions(List.of(TablesExtension.create(), StrikethroughExtension.create()))
    .build();

  private static final HtmlRenderer RENDERER = HtmlRenderer.builder()
    .extensions(List.of(TablesExtension.create(), StrikethroughExtension.create()))
    .build();

  private MarkdownRenderer() {}

  public static String renderToSafeHtml(String markdown) {
    if (markdown == null) return "";
    String rawHtml = RENDERER.render(PARSER.parse(markdown));
    return Jsoup.clean(rawHtml, Safelist.relaxed());
  }
}
