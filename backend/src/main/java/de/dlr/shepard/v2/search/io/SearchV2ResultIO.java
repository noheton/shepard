package de.dlr.shepard.v2.search.io;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "SearchV2Result")
public class SearchV2ResultIO {

  @Schema(readOnly = true, required = true)
  private List<SearchV2ItemIO> items;

  @Schema(readOnly = true, required = true, description = "Total matched entities across all kinds.")
  private long total;

  @Schema(readOnly = true, required = true, description = "Zero-based page index for collection results.")
  private int page;

  @Schema(readOnly = true, required = true, description = "Page size for collection results.")
  private int pageSize;

  @Schema(readOnly = true, required = true, description = "Total DataObject matches (before doPage/doPageSize slicing).")
  private long doTotal;

  @Schema(readOnly = true, required = true, description = "Zero-based page index for DataObject results.")
  private int doPage;

  @Schema(readOnly = true, required = true, description = "Page size for DataObject results.")
  private int doPageSize;

  @Schema(readOnly = true, required = true)
  private String query;
}
