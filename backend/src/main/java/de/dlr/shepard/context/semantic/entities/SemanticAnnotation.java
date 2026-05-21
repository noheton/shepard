package de.dlr.shepard.context.semantic.entities;

import de.dlr.shepard.common.identifier.HasAppId;
import de.dlr.shepard.common.neo4j.entities.Named;
import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.common.util.HasId;
import java.util.Objects;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.neo4j.ogm.annotation.GeneratedValue;
import org.neo4j.ogm.annotation.Id;
import org.neo4j.ogm.annotation.NodeEntity;
import org.neo4j.ogm.annotation.Property;
import org.neo4j.ogm.annotation.Relationship;

@NodeEntity
@Data
@NoArgsConstructor
public class SemanticAnnotation implements HasId, HasAppId, Named {

  @Id
  @GeneratedValue
  private Long id;

  /**
   * Application-level identifier (UUID v7) — additive in L2a.
   */
  @Property("appId")
  private String appId;

  @Deprecated
  public String getName() {
    return "%s::%s".formatted(this.getPropertyName(), this.getValueName());
  }

  private String propertyName;

  private String propertyIRI;

  private String valueName;

  private String valueIRI;

  /**
   * QA-1 — optional numeric rendering of the annotation value.
   * When set, clients can use this for range queries and unit-aware
   * comparison without parsing {@link #valueName}. Nullable; null means
   * "text-only annotation".
   */
  private Double numericValue;

  /**
   * QA-1 — IRI of the unit of measurement (e.g. a QUDT unit IRI such as
   * {@code http://qudt.org/vocab/unit/M-PER-SEC}). Only meaningful when
   * {@link #numericValue} is non-null. Nullable.
   */
  private String unitIRI;

  @ToString.Exclude
  @Relationship(type = Constants.PROPERTY_REPOSITORY)
  private SemanticRepository propertyRepository;

  @ToString.Exclude
  @Relationship(type = Constants.VALUE_REPOSITORY)
  private SemanticRepository valueRepository;

  /**
   * For testing purposes only
   *
   * @param id identifies the entity
   */
  public SemanticAnnotation(long id) {
    this.id = id;
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    var result = Objects.hash(id, propertyName, valueName, propertyIRI, valueIRI, numericValue, unitIRI);
    result = prime * result + HasId.hashcodeHelper(propertyRepository);
    result = prime * result + HasId.hashcodeHelper(valueRepository);
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null) return false;
    if (!(obj instanceof SemanticAnnotation other)) return false;
    return (
      Objects.equals(id, other.id) &&
      Objects.equals(propertyName, other.propertyName) &&
      Objects.equals(valueName, other.valueName) &&
      Objects.equals(propertyIRI, other.propertyIRI) &&
      Objects.equals(valueIRI, other.valueIRI) &&
      Objects.equals(numericValue, other.numericValue) &&
      Objects.equals(unitIRI, other.unitIRI) &&
      HasId.equalsHelper(propertyRepository, other.propertyRepository) &&
      HasId.equalsHelper(valueRepository, other.valueRepository)
    );
  }

  @Override
  public String getUniqueId() {
    return id.toString();
  }
}
