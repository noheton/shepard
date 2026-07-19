package de.dlr.shepard.context.references.timeseriesreference.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import de.dlr.shepard.common.identifier.AppIdGenerator;
import de.dlr.shepard.common.identifier.HasAppId;
import de.dlr.shepard.common.util.HasId;
import de.dlr.shepard.data.timeseries.model.Timeseries;
import de.dlr.shepard.data.timeseries.model.TimeseriesUniqueIdBuilder;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.neo4j.ogm.annotation.GeneratedValue;
import org.neo4j.ogm.annotation.Id;
import org.neo4j.ogm.annotation.NodeEntity;
import org.neo4j.ogm.annotation.Property;

@NodeEntity(label = "Timeseries")
@Data
// APPID-CHILD-MINT-REGRESSION: the node's value identity is its 5-tuple (the
// same key `getUniqueId()` returns and `ReferencedTimeseriesNodeEntityDAO.find`
// queries by) — the surrogate `appId` is now minted at construction, so it must
// be excluded from equals/hashCode. This is behaviour-preserving (appId was
// always null before this change, so it never distinguished two nodes) and keeps
// 5-tuple-based equality (relied on by the service dedup + Mockito arg-matching)
// intact now that fresh nodes each carry a distinct appId.
@EqualsAndHashCode(exclude = "appId")
@NoArgsConstructor
public class ReferencedTimeseriesNodeEntity implements HasId, HasAppId {

  @Id
  @GeneratedValue
  @JsonIgnore
  private Long id;

  /**
   * Application-level identifier (UUID v7) — additive in L2a.
   *
   * <p><b>APPID-CHILD-MINT-REGRESSION:</b> this {@code :Timeseries} node is only
   * ever persisted as a CASCADED CHILD of a {@link TimeseriesReference} (via
   * {@code GenericDAO.createOrUpdate(reference)} → {@code session.save(ref, 1)}),
   * so it never transits {@code GenericDAO}'s top-level-only mint branch. Both
   * value constructors below therefore mint the appId at construction time — the
   * single write-only choke point, since OGM hydrates loaded rows through the
   * no-arg constructor + field reflection (never these value ctors) and the read
   * path is {@link #toTimeseries()} (a separate POJO). Minting here guarantees
   * every NEW {@code :Timeseries} carries a v7 appId; legacy NULLs (DB-AP2: 0 of
   * 198 carried one) are backfilled by V122.
   */
  @Property("appId")
  @JsonIgnore
  private String appId;

  @NotBlank
  private String measurement;

  @NotBlank
  private String device;

  @NotBlank
  private String location;

  @NotBlank
  private String symbolicName;

  @NotBlank
  private String field;

  public ReferencedTimeseriesNodeEntity(
    String measurement,
    String device,
    String location,
    String symbolicName,
    String field
  ) {
    this.measurement = measurement;
    this.device = device;
    this.location = location;
    this.symbolicName = symbolicName;
    this.field = field;
    // APPID-CHILD-MINT-REGRESSION — see class note above the no-arg ctor.
    this.appId = AppIdGenerator.next();
  }

  public ReferencedTimeseriesNodeEntity(Timeseries timeseries) {
    this.measurement = timeseries.getMeasurement();
    this.device = timeseries.getDevice();
    this.location = timeseries.getLocation();
    this.symbolicName = timeseries.getSymbolicName();
    this.field = timeseries.getField();
    // APPID-CHILD-MINT-REGRESSION — see class note above the no-arg ctor.
    this.appId = AppIdGenerator.next();
  }

  public Timeseries toTimeseries() {
    return new Timeseries(
      this.getMeasurement(),
      this.getDevice(),
      this.getLocation(),
      this.getSymbolicName(),
      this.getField()
    );
  }

  @Override
  public String getUniqueId() {
    return TimeseriesUniqueIdBuilder.buildUniqueId(measurement, device, location, symbolicName, field);
  }
}
