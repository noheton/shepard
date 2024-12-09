package de.dlr.shepard.exceptions;

import jakarta.ws.rs.core.Response.Status;
import lombok.Getter;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * A class to represent an Api response object for invalid/ unsafe Html.
 */
@Getter
@Schema(name = "InvalidHtmlResponse")
public class InvalidHtmlResponse {

  @Schema(readOnly = true)
  private final String message =
    "You provided invalid or unsecure Html to the Api. " +
    "Please make sure that your provided Html does not contain any invalid tags or attributes. " +
    "See the 'sanitizedHtml' field to find out which tags and attributes were removed during sanitization.";

  @Schema(readOnly = true)
  private final String exception = "BadRequestException";

  @Schema(readOnly = true)
  private final Status status = Status.BAD_REQUEST;

  @Schema(readOnly = true)
  private final String suppliedHtml;

  @Schema(readOnly = true)
  private final String sanitizedHtml;

  public InvalidHtmlResponse(String suppliedHtml, String sanitizedHtml) {
    this.suppliedHtml = suppliedHtml;
    this.sanitizedHtml = sanitizedHtml;
  }
}
