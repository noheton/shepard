package de.dlr.shepard.v2.scenegraph.io;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.dlr.shepard.v2.scenegraph.services.SceneGraphService.SceneListPage;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * SCENEGRAPH-LIST-1 — paginated envelope for {@code GET /v2/scene-graphs}.
 *
 * <p>Envelope shape: {@code items[]}, {@code total}, {@code page}, {@code size}.
 * This matches the spec in the task brief rather than {@code /v2/collections}'
 * bare-array shape; the envelope makes it cheap to grow with additional
 * sibling fields (next-page cursor, filter echoes, …) without breaking
 * clients.
 */
@Getter
@Setter
@NoArgsConstructor
@ToString
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "SceneGraphList",
  description = "Page envelope for GET /v2/scene-graphs — items + total + page + size.")
public class SceneGraphListIO {

  @Schema(description = "Scenes on this page; may be empty.")
  private List<SceneGraphListItemIO> items;

  @Schema(description = "Total number of scenes across all pages.")
  private long total;

  @Schema(description = "Zero-based page index that produced this response.")
  private int page;

  @Schema(description = "Effective page size after server-side clamping into [1, 200].")
  private int size;

  public SceneGraphListIO(SceneListPage src, int page, int size) {
    this.items = new ArrayList<>(src.rows().size());
    for (var r : src.rows()) this.items.add(new SceneGraphListItemIO(r));
    this.total = src.total();
    this.page = page;
    this.size = size;
  }
}
