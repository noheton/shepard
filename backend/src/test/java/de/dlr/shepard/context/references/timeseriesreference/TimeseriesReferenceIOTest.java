package de.dlr.shepard.context.references.timeseriesreference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.context.collection.entities.DataObject;
import de.dlr.shepard.context.references.timeseriesreference.io.TimeseriesReferenceIO;
import de.dlr.shepard.context.references.timeseriesreference.model.ReferencedTimeseriesNodeEntity;
import de.dlr.shepard.context.references.timeseriesreference.model.TimeseriesReference;
import de.dlr.shepard.data.timeseries.model.Timeseries;
import de.dlr.shepard.data.timeseries.model.TimeseriesContainer;
import java.util.Date;
import java.util.List;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.jupiter.api.Test;

public class TimeseriesReferenceIOTest {

  @Test
  public void equalsContract() {
    EqualsVerifier.simple().forClass(TimeseriesReferenceIO.class).withIgnoredFields("revision").verify();
  }

  @Test
  public void testConversion() {
    var date = new Date();
    var user = new User("bob");
    var update = new Date();
    var updateUser = new User("claus");
    var dataObject = new DataObject(2L);
    dataObject.setShepardId(500L);
    var container = new TimeseriesContainer(3L);
    var ts = new Timeseries("meas", "dev", "loc", "name", "field");

    var timeseriesReference = new TimeseriesReference(1L);
    timeseriesReference.setShepardId(341L);
    timeseriesReference.setCreatedAt(date);
    timeseriesReference.setCreatedBy(user);
    timeseriesReference.setName("MyName");
    timeseriesReference.setUpdatedAt(update);
    timeseriesReference.setUpdatedBy(updateUser);
    timeseriesReference.setDataObject(dataObject);
    timeseriesReference.setEnd(213);
    timeseriesReference.setStart(123);
    timeseriesReference.setReferencedTimeseriesList(List.of(new ReferencedTimeseriesNodeEntity(ts)));
    timeseriesReference.setTimeseriesContainer(container);

    var converted = new TimeseriesReferenceIO(timeseriesReference);
    assertEquals(timeseriesReference.getShepardId(), converted.getId());
    assertEquals(timeseriesReference.getCreatedAt(), converted.getCreatedAt());
    assertEquals("bob", converted.getCreatedBy());
    assertEquals(timeseriesReference.getName(), converted.getName());
    assertEquals(timeseriesReference.getUpdatedAt(), converted.getUpdatedAt());
    assertEquals("claus", converted.getUpdatedBy());
    assertEquals(dataObject.getShepardId(), converted.getDataObjectId());
    assertEquals(timeseriesReference.getEnd(), converted.getEnd());
    assertEquals(timeseriesReference.getStart(), converted.getStart());
    assertEquals(List.of(ts), converted.getTimeseries());
    assertEquals(3L, converted.getTimeseriesContainerId());
  }

  @Test
  public void testConversion_timeReferenceFieldsMapped() {
    var ref = new TimeseriesReference(1L);
    ref.setShepardId(1L);
    ref.setDataObject(new DataObject(2L));
    ref.setReferencedTimeseriesList(List.of());
    ref.setTimeReference("EXPERIMENT_RELATIVE");
    ref.setWallClockOffset(1_000_000_000L);
    ref.setWallClockOffsetSource("manual");

    var io = new TimeseriesReferenceIO(ref);
    assertEquals("EXPERIMENT_RELATIVE", io.getTimeReference());
    assertEquals(1_000_000_000L, io.getWallClockOffset());
    assertEquals("manual", io.getWallClockOffsetSource());
  }

  @Test
  public void testConversion_timeReferenceFieldsNullWhenNotSet() {
    var ref = new TimeseriesReference(1L);
    ref.setShepardId(1L);
    ref.setDataObject(new DataObject(2L));
    ref.setReferencedTimeseriesList(List.of());

    var io = new TimeseriesReferenceIO(ref);
    assertEquals(null, io.getTimeReference());
    assertEquals(null, io.getWallClockOffset());
    assertEquals(null, io.getWallClockOffsetSource());
  }

  /**
   * Regression for task #131 (v5 wire-fidelity audit). The AI1c
   * ({@code qualityScore}, {@code lastScoredAt}) and TM1
   * ({@code timeReference}, {@code wallClockOffset},
   * {@code wallClockOffsetSource}) fields are fork additions on
   * {@link TimeseriesReferenceIO}, which is exposed on the v1
   * {@code /shepard/api/...} surface (via {@code TimeseriesReferenceRest}).
   * Per {@code CLAUDE.md §"API-version policy"} the fork must keep the
   * v1 wire byte-compatible with upstream 5.2.0 — which means these
   * keys MUST be omitted when null, not serialised as JSON {@code null}.
   * Pinned at the serialisation boundary so future refactors that drop
   * the {@code @JsonInclude(NON_NULL)} annotations fail fast.
   */
  @Test
  public void aiAndTmFields_areOmittedFromJson_whenNull() throws Exception {
    var ref = new TimeseriesReference(1L);
    ref.setShepardId(1L);
    ref.setDataObject(new DataObject(2L));
    ref.setReferencedTimeseriesList(List.of());
    // qualityScore, lastScoredAt, timeReference, wallClockOffset,
    // wallClockOffsetSource all left null (the default).

    var io = new TimeseriesReferenceIO(ref);
    String json = new ObjectMapper().writeValueAsString(io);
    assertThat(json).doesNotContain("qualityScore");
    assertThat(json).doesNotContain("lastScoredAt");
    assertThat(json).doesNotContain("timeReference");
    assertThat(json).doesNotContain("wallClockOffset");
    assertThat(json).doesNotContain("wallClockOffsetSource");
  }

  @Test
  public void aiAndTmFields_areSerialised_whenSet() throws Exception {
    var ref = new TimeseriesReference(1L);
    ref.setShepardId(1L);
    ref.setDataObject(new DataObject(2L));
    ref.setReferencedTimeseriesList(List.of());
    ref.setQualityScore(0.87);
    ref.setLastScoredAt(1700000000000L);
    ref.setTimeReference("EXPERIMENT_RELATIVE");
    ref.setWallClockOffset(1_000_000_000L);
    ref.setWallClockOffsetSource("manual");

    var io = new TimeseriesReferenceIO(ref);
    String json = new ObjectMapper().writeValueAsString(io);
    assertThat(json).contains("\"qualityScore\":0.87");
    assertThat(json).contains("\"lastScoredAt\":1700000000000");
    assertThat(json).contains("\"timeReference\":\"EXPERIMENT_RELATIVE\"");
    assertThat(json).contains("\"wallClockOffset\":1000000000");
    assertThat(json).contains("\"wallClockOffsetSource\":\"manual\"");
  }

  @Test
  public void testConversion_ContainerNull() {
    var date = new Date();
    var user = new User("bob");
    var dataObject = new DataObject(2L);
    dataObject.setShepardId(3485L);
    var ts = new Timeseries("meas", "dev", "loc", "name", "field");

    var timeseriesReference = new TimeseriesReference(1L);
    timeseriesReference.setShepardId(234L);
    timeseriesReference.setCreatedAt(date);
    timeseriesReference.setCreatedBy(user);
    timeseriesReference.setName("MyName");
    timeseriesReference.setDataObject(dataObject);
    timeseriesReference.setEnd(213);
    timeseriesReference.setStart(123);
    timeseriesReference.setReferencedTimeseriesList(List.of(new ReferencedTimeseriesNodeEntity(ts)));

    var converted = new TimeseriesReferenceIO(timeseriesReference);
    assertEquals(timeseriesReference.getShepardId(), converted.getId());
    assertEquals(timeseriesReference.getCreatedAt(), converted.getCreatedAt());
    assertEquals("bob", converted.getCreatedBy());
    assertEquals(timeseriesReference.getName(), converted.getName());
    assertEquals(dataObject.getShepardId(), converted.getDataObjectId());
    assertEquals(timeseriesReference.getEnd(), converted.getEnd());
    assertEquals(timeseriesReference.getStart(), converted.getStart());
    assertEquals(List.of(ts), converted.getTimeseries());
    assertEquals(-1, converted.getTimeseriesContainerId());
  }
}
