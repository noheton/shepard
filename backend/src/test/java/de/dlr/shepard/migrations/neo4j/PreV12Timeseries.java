package de.dlr.shepard.migrations.neo4j;

import de.dlr.shepard.common.util.HasId;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.neo4j.ogm.annotation.GeneratedValue;
import org.neo4j.ogm.annotation.Id;
import org.neo4j.ogm.annotation.NodeEntity;

@NodeEntity
@Data
@EqualsAndHashCode
@NoArgsConstructor(force = true)
@RequiredArgsConstructor
public class PreV12Timeseries implements HasId {

  @Id
  @GeneratedValue
  private Long id;

  @NotBlank
  private final String measurement;

  @NotBlank
  private final String device;

  @NotBlank
  private final String location;

  @NotBlank
  private final String symbolicName;

  @NotBlank
  private final String field;

  @Override
  public String getUniqueId() {
    return this.getId().toString();
  }
}
