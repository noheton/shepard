package de.dlr.shepard.v2.search.io;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "SearchV2Item")
public class SearchV2ItemIO {

  @Schema(readOnly = true, required = true, description = "UUID v7 application-level identifier.")
  private String appId;

  @Schema(readOnly = true, required = true)
  private String name;

  @Schema(readOnly = true, required = true, enumeration = { "collection", "dataobject" })
  private String kind;
}
