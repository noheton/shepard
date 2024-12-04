package de.dlr.shepard.neo4Core.entities;

import java.util.Map;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import org.neo4j.ogm.annotation.Properties;

@Data
@ToString(callSuper = true)
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public abstract class AbstractDataObject extends VersionableEntity {

  private String description;

  /**
   * Sets description string and applies Html sanitizing.
   * @param description
   */
  public void setDescription(String description) {
    String newDescription = description;
    if (newDescription != null) {
      newDescription = Jsoup.clean(
        description,
        // Basic safelist allows the following tags:
        //  a, b, blockquote, br, cite, code, dd, dl, dt, em, i, li, ol, p, pre, q, small, span, strike, s, strong, sub, sup, u, ul, and appropriate attributes.
        Safelist.basicWithImages()
          // Tags that are allowed in the 'basic' Safelist, but are unwanted
          .removeTags("blockquote", "cite", "dl", "dt", "dd", "h4", "h5", "h6", "small", "sub", "sup", "tfoot", "q")
          // Allow specific tags
          .addTags("th", "thead", "tbody", "tr", "table", "td", "h1", "h2", "h3", "colgroup", "col", "strike", "s")
          // Allow specific attributes
          .addAttributes(":all", "style", "text-align", "align", "colspan", "rowspan", "target", "rel")
      );
    }
    this.description = newDescription;
  }

  @ToString.Exclude
  @Properties
  private Map<String, String> attributes;

  /**
   * For testing purposes only
   *
   * @param id identifies the entity
   */
  protected AbstractDataObject(long id) {
    super(id);
  }
}
