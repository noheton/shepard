package de.dlr.shepard.neo4Core.entities;

import static org.junit.jupiter.api.Assertions.assertEquals;

import de.dlr.shepard.BaseTestCase;
import de.dlr.shepard.labJournal.entities.LabJournal;
import org.junit.jupiter.api.Test;

public class LabJournalTest extends BaseTestCase {

  @Test
  public void testJournalLinkHtmlSanitizing() {
    String unsafeHtml = "<p><a href='http://example.com/' onclick='stealCookies()'>Link</a></p>";
    String safeHtml = "<p><a href=\"http://example.com/\" rel=\"nofollow\">Link</a></p>";

    LabJournal journal = new LabJournal();
    journal.setDescription(unsafeHtml);
    assertEquals(safeHtml, journal.getDescription());
  }

  @Test
  public void testJournalSimpleTextSanitizing() {
    String textString = "This is my text that should not be changed by sanitizing.";

    LabJournal journal = new LabJournal();
    journal.setDescription(textString);
    assertEquals(textString, journal.getDescription());
  }

  @Test
  public void testJournalScriptHtmlSanitizing() {
    String unsafeHtml = "<p>Useful information, thanks!<script>alert('This is an injection!');</script></p>";
    String safeHtml = "<p>Useful information, thanks!</p>";

    LabJournal journal = new LabJournal();
    journal.setDescription(unsafeHtml);
    assertEquals(safeHtml, journal.getDescription());
  }

  @Test
  public void testJournalButtonOnClickHtmlSanitizing() {
    String unsafeHtml = "<button onclick=\"alert('Button')\">Hello</button>";
    String safeHtml = "Hello";

    LabJournal journal = new LabJournal();
    journal.setDescription(unsafeHtml);
    assertEquals(safeHtml, journal.getDescription());
  }

  @Test
  public void testJournalImageHtmlSanitizing() {
    // Only 'https://' img sources are allowed
    String unsafeHtmlSrc = "<img src=\"test.jpg\" onerror=\"alert('XSS Hata!');\">";
    String safeHtmlSrc = "<img>";

    LabJournal journal1 = new LabJournal();
    journal1.setDescription(unsafeHtmlSrc);
    assertEquals(safeHtmlSrc, journal1.getDescription());

    // No 'on-error' is allowed
    String unsafeHtmlOnError = "<img src=\"https://my-website.xyz\" onerror=\"alert('XSS Hata!');\">";
    String safeHtmlOnerror = "<img src=\"https://my-website.xyz\">";

    LabJournal journal2 = new LabJournal();
    journal2.setDescription(unsafeHtmlOnError);
    assertEquals(safeHtmlOnerror, journal2.getDescription());
  }

  @Test
  public void testJournalTableHtmlSanitizing() {
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

    LabJournal journal = new LabJournal();
    journal.setDescription(unwantedHtmlCaptionTag);
    assertEquals(wantedHtmlCaptionTag, journal.getDescription());
  }

  @Test
  public void testJournalSafeHtmlSanitizing() {
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

    LabJournal journal = new LabJournal();
    journal.setDescription(htmlString);
    assertEquals(htmlString, journal.getDescription());
  }

  @Test
  public void testJournalKeepAlignment() {
    // keep alignment attributes on HTML
    String htmlString =
      """
      <p style="text-align: center">asdasdas</p>
      <p style="text-align: right">my desription</p>
      <p>is here</p>""";

    LabJournal journal = new LabJournal();
    journal.setDescription(htmlString);
    assertEquals(htmlString, journal.getDescription());
  }
}
