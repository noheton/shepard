package de.dlr.shepard.exceptions;

import lombok.Getter;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * A class to represent an Api response object for invalid/ unsafe Html.
 */
@Getter
@Schema(name = "InvalidHtmlResponse")
public class InvalidHtmlResponse {

  @Schema(readOnly = true)
  private String message =
    "You provided invalid or unsecure Html to the Api. " +
    "Please make sure that your provided Html does not contain any invalid tags or attributes. " +
    "See the 'sanitizedHtml' field to find out which tags and attributes were removed during sanitization.";

  @Schema(readOnly = true)
  private String suppliedHtml;

  @Schema(readOnly = true)
  private String sanitizedHtml;

  public InvalidHtmlResponse(String suppliedHtml, String sanitizedHtml) {
    this.suppliedHtml = suppliedHtml;
    this.sanitizedHtml = sanitizedHtml;
  }
}
