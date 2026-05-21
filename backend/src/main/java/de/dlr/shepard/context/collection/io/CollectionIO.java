package de.dlr.shepard.context.collection.io;

import de.dlr.shepard.common.neo4j.io.AbstractDataObjectIO;
import de.dlr.shepard.common.util.HasId;
import de.dlr.shepard.context.collection.entities.Collection;
import java.util.Objects;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Data
@NoArgsConstructor
@Schema(name = "Collection")
public class CollectionIO extends AbstractDataObjectIO {

  @Schema(readOnly = true, required = true)
  private long[] dataObjectIds;

  @Schema(readOnly = true, required = true)
  private long[] incomingIds;

  /**
   * Id of a default file container.
   * This value can be nullable.
   * The default value is null.
   */
  @Schema(nullable = true)
  private Long defaultFileContainerId = null;

  /**
   * Optional hero/banner image URL displayed at the top of the Collection
   * detail page. When null, no banner is shown. URL-only (no server-side
   * upload); the frontend handles 404s gracefully via the {@code <v-img>}
   * error slot. Settable on POST and PATCH (RFC 7396: null clears it,
   * absent leaves it unchanged). Exposed only on the {@code /v2/} surface.
   */
  @Schema(nullable = true)
  private String heroImageUrl;

  public CollectionIO(Collection collection) {
    super(collection);
    this.dataObjectIds = extractShepardIds(collection.getDataObjects());
    this.incomingIds = extractShepardIds(collection.getIncoming());

    if (collection.getFileContainer() == null) {
      this.defaultFileContainerId = null;
    } else {
      this.defaultFileContainerId = collection.getFileContainer().getId();
    }

    this.heroImageUrl = collection.getHeroImageUrl();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!super.equals(o)) return false;
    if (!(o instanceof CollectionIO)) return false;
    CollectionIO other = (CollectionIO) o;
    return (
      HasId.areEqualSets(dataObjectIds, other.dataObjectIds) &&
      HasId.areEqualSets(incomingIds, other.incomingIds) &&
      Objects.equals(defaultFileContainerId, other.defaultFileContainerId) &&
      Objects.equals(heroImageUrl, other.heroImageUrl)
    );
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = super.hashCode();
    result = prime * result + HasId.hashcodeHelper(dataObjectIds);
    result = prime * result + HasId.hashcodeHelper(incomingIds);
    result = prime * result + Objects.hashCode(defaultFileContainerId);
    result = prime * result + Objects.hashCode(heroImageUrl);
    return result;
  }
}
