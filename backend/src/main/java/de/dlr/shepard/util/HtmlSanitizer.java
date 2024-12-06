package de.dlr.shepard.util;

import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;

public class HtmlSanitizer {

  /**
   * Checks if the given Html string contains unwanted/ unsecure Html tags or attributes specified by the safelist.
   * @param html
   * @return boolean - true, if Html string does not contain any unwanted tags or attributes
   */
  public static boolean isSafeHtml(String html) {
    return Jsoup.isValid(html, getSafeList());
  }

  /**
   * Returns a sanitized version of the string passed into this function. The string is sanitized string only contains tags and attributes that are specified in safelist.
   * The sanitized string still contains all values inside the tags. Only the tags and attributes are removed.
   * @param html
   * @return String - sanitized Html string
   */
  public static String cleanHtmlString(String html) {
    return Jsoup.clean(html, getSafeList());
  }

  private static Safelist getSafeList() {
    return Safelist.basicWithImages()
      // Tags that are allowed in the 'basic' Safelist, but are unwanted
      .removeTags("blockquote", "cite", "dl", "dt", "dd", "h4", "h5", "h6", "small", "sub", "sup", "tfoot", "q")
      // Allow specific tags
      .addTags("th", "thead", "tbody", "tr", "table", "td", "h1", "h2", "h3", "colgroup", "col", "strike", "s")
      // Allow specific attributes
      .addAttributes(":all", "style", "colspan", "rowspan")
      .addAttributes("a", "target", "rel");
  }
}
