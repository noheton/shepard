package de.dlr.shepard.common.search.io;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ResultTriple {

  private Long collectionId;
  private Long dataObjectId;
  private Long referenceId;

  public ResultTriple(Long collectionId) {
    this.collectionId = collectionId;
  }

  public ResultTriple(Long collectionId, Long dataObjectId) {
    this.collectionId = collectionId;
    this.dataObjectId = dataObjectId;
  }
}
