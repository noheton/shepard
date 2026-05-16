package de.dlr.shepard.context.labJournal.services;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * J1a — unit tests for {@link LabJournalRenderService}.
 *
 * <p>These tests are plain JUnit5 (no CDI container needed) because
 * {@link LabJournalRenderService} is stateless: the parser and renderer
 * are static finals, and {@code renderToHtml} has no side effects.
 */
class LabJournalRenderServiceTest {

  private final LabJournalRenderService service = new LabJournalRenderService();

  @Test
  void renderToHtml_null_returnsEmpty() {
    assertThat(service.renderToHtml(null)).isEqualTo("");
  }

  @Test
  void renderToHtml_blank_returnsEmpty() {
    assertThat(service.renderToHtml("   ")).isEqualTo("");
  }

  @Test
  void renderToHtml_plainText_wrapsInParagraph() {
    String html = service.renderToHtml("Hello world");
    assertThat(html).contains("<p>Hello world</p>");
  }

  @Test
  void renderToHtml_bold_rendersAsStrong() {
    String html = service.renderToHtml("**bold**");
    assertThat(html).contains("<strong>bold</strong>");
  }

  @Test
  void renderToHtml_gfmTable_rendersAsTable() {
    String md = "| A | B |\n|---|---|\n| 1 | 2 |\n";
    String html = service.renderToHtml(md);
    assertThat(html).contains("<table>").contains("<td>1</td>").contains("<td>2</td>");
  }

  @Test
  void renderToHtml_javascriptHref_isSanitized() {
    // sanitizeUrls=true should strip the javascript: href
    String html = service.renderToHtml("[click](javascript:alert(1))");
    assertThat(html).doesNotContain("javascript:");
  }

  @Test
  void renderToHtml_fencedCodeBlock_rendersAsPreCode() {
    String md = "```python\nprint('hi')\n```\n";
    String html = service.renderToHtml(md);
    assertThat(html).contains("<pre>").contains("<code");
  }

  @Test
  void renderToHtml_gfmStrikethrough_rendersAsDel() {
    String html = service.renderToHtml("~~gone~~");
    assertThat(html).contains("<del>gone</del>");
  }

  @Test
  void renderToHtml_taskList_rendersCheckbox() {
    String md = "- [x] done\n- [ ] todo\n";
    String html = service.renderToHtml(md);
    assertThat(html).contains("checked").contains("type=\"checkbox\"");
  }
}
