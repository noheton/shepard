package de.dlr.shepard.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

public class HtmlSanitizerTest {

  @Test
  public void testLinkHtmlSanitizing() {
    String unsafeHtml = "<p><a href='http://example.com/' onclick='stealCookies()'>Link</a></p>";
    String safeHtml = "<p><a href=\"http://example.com/\" rel=\"nofollow\">Link</a></p>";

    assertEquals(safeHtml, HtmlSanitizer.cleanHtmlString(unsafeHtml));
  }

  @Test
  public void testSimpleTextSanitizing() {
    String textString = "This is my text that should not be changed by sanitizing.";

    assertEquals(textString, HtmlSanitizer.cleanHtmlString(textString));
  }

  @Test
  public void testScriptHtmlSanitizing() {
    String unsafeHtml = "<p>Useful information, thanks!<script>alert('This is an injection!');</script></p>";
    String safeHtml = "<p>Useful information, thanks!</p>";

    assertEquals(safeHtml, HtmlSanitizer.cleanHtmlString(unsafeHtml));
  }

  @Test
  public void testButtonOnClickHtmlSanitizing() {
    String unsafeHtml = "<button onclick=\"alert('Button')\">Hello</button>";
    String safeHtml = "Hello";

    assertEquals(safeHtml, HtmlSanitizer.cleanHtmlString(unsafeHtml));
  }

  @Test
  public void testImageHtmlSanitizing() {
    // Only 'https://' img sources are allowed
    String unsafeHtmlSrc = "<img src=\"test.jpg\" onerror=\"alert('XSS Hata!');\">";
    String safeHtmlSrc = "<img>";

    assertEquals(safeHtmlSrc, HtmlSanitizer.cleanHtmlString(unsafeHtmlSrc));

    // No 'on-error' is allowed
    String unsafeHtmlOnError = "<img src=\"https://my-website.xyz\" onerror=\"alert('XSS Hata!');\">";
    String safeHtmlOnerror = "<img src=\"https://my-website.xyz\">";

    assertEquals(safeHtmlOnerror, HtmlSanitizer.cleanHtmlString(unsafeHtmlOnError));
  }

  @Test
  public void testTableHtmlSanitizing() {
    // Testing the removal of a secure but unwanted Html tag
    String unwantedHtmlCaptionTag =
      """
      <table>
      <caption>Monthly savings</caption>
      <tbody>
      <tr>
      <th>Month</th>
      <th>Savings</th>
      </tr>
      <tr>
      <td>January</td>
      <td>$100</td>
      </tr>
      <tr>
      <td>February</td>
      <td>$50</td>
      </tr>
      </tbody>
      </table>""";
    String wantedHtmlCaptionTag =
      """
      <table>Monthly savings
       <tbody>
        <tr>
         <th>Month</th>
         <th>Savings</th>
        </tr>
        <tr>
         <td>January</td>
         <td>$100</td>
        </tr>
        <tr>
         <td>February</td>
         <td>$50</td>
        </tr>
       </tbody>
      </table>""";

    assertEquals(wantedHtmlCaptionTag, HtmlSanitizer.cleanHtmlString(unwantedHtmlCaptionTag));
  }

  @Test
  public void testSafeHtmlSanitizing() {
    // have a safe Html string with wanted tags and dont remove anything
    String htmlString =
      """
      <h1>My Heading</h1>
      <p>This is a <b>paragraph</b> with <i>italic</i>, <u>underlined</u>, and <strike>strikethrough</strike> text.</p>
      <p>Here is an <a href="https://example.com" rel="nofollow">example link</a>.</p>
      <p>This line of text includes a <span>span element</span>.</p>
      <p>Here is a line break<br>
        like this.</p>
      <p>You can include some <code>inline code</code> as well.</p>
      <p>You can include some <s>strike format</s> as well.</p>
      <ul>
       <li>Unordered list item 1</li>
       <li>Unordered list item 2</li>
       <li>Unordered list item 3</li>
      </ul>
      <ol>
       <li>Ordered list item 1</li>
       <li>Ordered list item 2</li>
       <li>Ordered list item 3</li>
      </ol>""";

    assertEquals(true, HtmlSanitizer.isSafeHtml(htmlString));

    // This is the valid html string from the LabJournal integration test
    String htmlString2 =
      """
      <h3>This is my heading</h3>
      <p>Here some <strong>bold text</strong>, some <em>italic text</em>, some <u>underline text</u>, some <code>code text</code></p>
      <p>left</p><p style="text-align: center">center</p><p style="text-align: right">right</p><p></p>
      <p><a target="_blank" rel="noopener noreferrer nofollow" href="https://shepard.com">This is a link</a></p>
      <p></p>
      <ul><li><p>List 1</p><ul><li><p>List 2</p></li></ul></li></ul>
      <ol><li><p>List 1.1</p><ol><li><p>List 2.2</p></li></ol></li></ol><p></p>
      <table style="min-width: 75px"><colgroup><col style="min-width: 25px">
      <col style="min-width: 25px">
      <col style="min-width: 25px"></colgroup><tbody><tr><th colspan="1" rowspan="1"><p>1</p></th>
      <th colspan="1" rowspan="1"><p>2</p></th><th colspan="1" rowspan="1"><p>3</p></th></tr><tr>
      <td colspan="1" rowspan="1"><p>3</p></td><td colspan="1" rowspan="1"><p>2</p></td>
      <td colspan="1" rowspan="1"><p>1</p></td></tr><tr><td colspan="1" rowspan="1"><p>c</p></td>
      <td colspan="1" rowspan="1"><p>b</p></td><td colspan="1" rowspan="1"><p>a</p></td></tr></tbody></table>
      """;
    assertEquals(true, HtmlSanitizer.isSafeHtml(htmlString2));
  }

  @Test
  public void testInvalidHtmlUnclosedTagSanitizing() {
    String htmlString = "<h1>My Heading";
    String repairedHtmlString = "<h1>My Heading</h1>";

    assertEquals(false, HtmlSanitizer.isSafeHtml(htmlString));
    assertEquals(repairedHtmlString, HtmlSanitizer.cleanHtmlString(htmlString));
  }

  @Test
  public void testUnSafeHtmlSanitizing() {
    // This is the invalid html string from the LabJournal integration test
    String htmlString1 =
      """
      <h1>This is my heading</h1>
      <p>Here some <strong>bold text</strong>, some <em>italic text</em>, some <u>underline text</u>, some <code>code text</code></p>
      <p>left</p><p style="text-align: center">center</p><p style="text-align: right">right</p><p></p>
      <p><a target="_blank" rel="noopener noreferrer nofollow" href="https://shepard.com">This is a link</a></p>
      <p></p>
      <ul><li><p>List 1</p><ul><li><p>List 2</p></li></ul></li></ul>
      <ol><li><p>List 1.1</p><ol><li><p>List 2.2</p></li></ol></li></ol><p></p>
      <table style="min-width: 75px"><colgroup><col style="min-width: 25px">
      <col style="min-width: 25px">
      <script>alert("dangerous")</script>
      """;
    assertEquals(false, HtmlSanitizer.isSafeHtml(htmlString1));
  }

  @Test
  public void testKeepAlignment() {
    // keep alignment attributes on HTML
    String htmlString =
      """
      <p style="text-align: center">asdasdas</p>
      <p style="text-align: right">my desription</p>
      <p>is here</p>""";

    assertEquals(htmlString, HtmlSanitizer.cleanHtmlString(htmlString));
  }
}
