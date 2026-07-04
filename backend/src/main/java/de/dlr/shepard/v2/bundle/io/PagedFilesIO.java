package de.dlr.shepard.v2.bundle.io;

import de.dlr.shepard.data.file.entities.ShepardFile;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * MFFD-IMAGEBUNDLE-PAGINATE-1 — paged envelope for
 * {@code GET /v2/bundles/{bundleAppId}/groups/{groupAppId}/files}.
 *
 * <p>An ImageBundle for the MFFD TPS raw-data shape can hold up to 38
 * PNG frames per group today, but the upper bound is operator-controlled
 * and there's no architectural reason a group couldn't carry more (the
 * thermography series goes higher already; future point-cloud bundles
 * higher still). Loading the entire {@code files[]} list into the UI
 * stalls past ~200 entries on a 4K viewport.
 *
 * <p>The envelope is the small generic shape — `items[]` + pagination
 * metadata — rather than a bare array, so a client knows how to fetch
 * the next page without re-reading the docs.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "PagedFiles")
public class PagedFilesIO {

  @Schema(description = "The files on this page, ordered by their position in the group's files[] list.")
  private List<ShepardFile> items;

  @Schema(description = "0-based page index that was returned.")
  private int page;

  @Schema(description = "Maximum items per page that was honoured by the server.")
  private int size;

  @Schema(description = "Total number of files in the group (across all pages).")
  private long totalElements;

  @Schema(description = "Total number of pages at the current `size`.")
  private int totalPages;
}
