package de.dlr.shepard.v2.admin.config.descriptors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.dlr.shepard.v2.admin.config.spi.ConfigPatchException;
import de.dlr.shepard.v2.admin.sqltimeseries.entities.SqlTimeseriesConfig;
import de.dlr.shepard.v2.admin.sqltimeseries.io.SqlTimeseriesConfigIO;
import de.dlr.shepard.v2.admin.sqltimeseries.services.SqlTimeseriesConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/** V2CONV-A4 — unit tests for {@link SqlTimeseriesConfigDescriptor}. */
class SqlTimeseriesConfigDescriptorTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private SqlTimeseriesConfigService service;
  private SqlTimeseriesConfigDescriptor descriptor;

  private static SqlTimeseriesConfig cfg(Long rows, String dur) {
    SqlTimeseriesConfig c = new SqlTimeseriesConfig();
    c.setMaxRows(rows);
    c.setMaxDurationIso(dur);
    return c;
  }

  @BeforeEach
  void setUp() {
    service = Mockito.mock(SqlTimeseriesConfigService.class);
    Mockito.when(service.getDefaultMaxRows()).thenReturn(1000L);
    Mockito.when(service.getDefaultMaxDuration()).thenReturn("PT60S");
    descriptor = new SqlTimeseriesConfigDescriptor();
    descriptor.service = service;
  }

  @Test
  void featureNameIsSqlTimeseries() {
    assertEquals("sql-timeseries", descriptor.featureName());
  }

  @Test
  void currentShapeResolvesDefaults() {
    Mockito.when(service.current()).thenReturn(cfg(null, null));
    SqlTimeseriesConfigIO io = descriptor.currentShape();
    assertEquals(1000L, io.maxRows());
    assertEquals("PT60S", io.maxDuration());
  }

  @Test
  void patchAppliesNewValues() throws Exception {
    Mockito.when(service.current()).thenReturn(cfg(500L, "PT30S"));
    Mockito.when(service.patch(Mockito.any(), Mockito.any())).thenAnswer(i -> cfg(i.getArgument(0), i.getArgument(1)));
    SqlTimeseriesConfigIO io = descriptor.applyMergePatch(MAPPER.readTree("{\"maxRows\":2000}"));
    assertEquals(2000L, io.maxRows());
    // maxDuration carried through.
    Mockito.verify(service).patch(2000L, "PT30S");
  }

  @Test
  void nonPositiveMaxRowsThrows() {
    Mockito.when(service.current()).thenReturn(cfg(500L, "PT30S"));
    ConfigPatchException ex = assertThrows(
      ConfigPatchException.class,
      () -> descriptor.applyMergePatch(MAPPER.readTree("{\"maxRows\":0}"))
    );
    assertEquals(SqlTimeseriesConfigDescriptor.PROBLEM_TYPE_INVALID_MAX_ROWS, ex.getProblemType());
  }

  @Test
  void invalidDurationThrows() {
    Mockito.when(service.current()).thenReturn(cfg(500L, "PT30S"));
    ConfigPatchException ex = assertThrows(
      ConfigPatchException.class,
      () -> descriptor.applyMergePatch(MAPPER.readTree("{\"maxDuration\":\"not-iso\"}"))
    );
    assertEquals(SqlTimeseriesConfigDescriptor.PROBLEM_TYPE_INVALID_MAX_DURATION, ex.getProblemType());
  }

  @Test
  void nullClearsToDefault() throws Exception {
    Mockito.when(service.current()).thenReturn(cfg(500L, "PT30S"));
    Mockito.when(service.patch(Mockito.any(), Mockito.any())).thenAnswer(i -> cfg(i.getArgument(0), i.getArgument(1)));
    descriptor.applyMergePatch(MAPPER.readTree("{\"maxRows\":null}"));
    Mockito.verify(service).patch(null, "PT30S");
  }
}
