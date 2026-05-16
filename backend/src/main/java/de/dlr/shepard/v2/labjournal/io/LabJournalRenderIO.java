package de.dlr.shepard.v2.labjournal.io;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * J1a — JSON response shape for {@code GET /v2/lab-journal/{appId}/render}
 * when the caller sends {@code Accept: application/json}.
 *
 * <p>Clients that prefer the rendered HTML as a JSON envelope use this
 * type. Clients that want bare HTML send {@code Accept: text/html} and
 * receive the string directly (Content-Type: text/html; charset=utf-8).
 */
@Data
@AllArgsConstructor
@Schema(name = "LabJournalRender")
public class LabJournalRenderIO {

  /** Sanitised HTML produced by CommonMark + GFM parsing of the journal content. */
  @Schema(readOnly = true, required = true)
  private String html;

  /** Byte length of the original markdown source (useful for caching / diff decisions). */
  @Schema(readOnly = true, required = true)
  private int sourceLength;
}
