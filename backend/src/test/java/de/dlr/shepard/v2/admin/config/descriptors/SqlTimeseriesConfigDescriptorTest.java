package de.dlr.shepard.v2.admin.config.descriptors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.dlr.shepard.v2.admin.config.ConfigValidationException;
import de.dlr.shepard.v2.admin.sqltimeseries.entities.SqlTimeseriesConfig;
import de.dlr.shepard.v2.admin.sqltimeseries.io.SqlTimeseriesConfigIO;
import de.dlr.shepard.v2.admin.sqltimeseries.services.SqlTimeseriesConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * V2CONV-A4 — unit tests for {@link SqlTimeseriesConfigDescriptor}.
 */
class SqlTimeseriesConfigDescriptorTest {

  private static final long DEFAULT_MAX_ROWS = 1_000_000L;
  private static final String DEFAULT_MAX_DURATION = "PT60S";
  private static final ObjectMapper MAPPER = new ObjectMapper();

  private SqlTimeseriesConfigService service;
  private SqlTimeseriesConfigDescriptor descriptor;

  @BeforeEach
  void setUp() {
    service = mock(SqlTimeseriesConfigService.class);
    when(service.getDefaultMaxRows()).thenReturn(DEFAULT_MAX_ROWS);
    when(service.getDefaultMaxDuration()).thenReturn(DEFAULT_MAX_DURATION);

    descriptor = new SqlTimeseriesConfigDescriptor();
    descriptor.service = service;
  }

  @Test
  void featureName_isSqlTimeseries() {
    assertEquals("sql-timeseries", descriptor.featureName());
  }

  @Test
  void read_returnsFullyResolvedIO() {
    SqlTimeseriesConfig cfg = new SqlTimeseriesConfig();
    cfg.setMaxRows(500_000L);
    cfg.setMaxDurationIso("PT30S");
    when(service.current()).thenReturn(cfg);

    Object result = descriptor.read();

    SqlTimeseriesConfigIO io = assertInstanceOf(SqlTimeseriesConfigIO.class, result);
    assertEquals(500_000L, io.maxRows());
    assertEquals("PT30S", io.maxDuration());
  }

  @Test
  void patch_nullBody_noOp_returnsCurrentRead() throws Exception {
    SqlTimeseriesConfig cfg = new SqlTimeseriesConfig();
    cfg.setMaxRows(1_000_000L);
    cfg.setMaxDurationIso("PT60S");
    when(service.current()).thenReturn(cfg);
    when(service.patch(any(), any())).thenReturn(cfg);

    // null body = no field present → effective values = current values
    Object result = descriptor.patch(null);

    // patch() was still called with current values (no-op patch)
    verify(service).patch(eq(1_000_000L), eq("PT60S"));
    assertNotNull(result);
  }

  @Test
  void patch_maxRows_updatesOnlyMaxRows() throws Exception {
    SqlTimeseriesConfig current = new SqlTimeseriesConfig();
    current.setMaxRows(1_000_000L);
    current.setMaxDurationIso("PT60S");

    SqlTimeseriesConfig updated = new SqlTimeseriesConfig();
    updated.setMaxRows(200_000L);
    updated.setMaxDurationIso("PT60S");

    when(service.current()).thenReturn(current);
    when(service.patch(eq(200_000L), eq("PT60S"))).thenReturn(updated);

    Object result = descriptor.patch(MAPPER.readTree("{\"maxRows\": 200000}"));

    SqlTimeseriesConfigIO io = assertInstanceOf(SqlTimeseriesConfigIO.class, result);
    assertEquals(200_000L, io.maxRows());
  }

  @Test
  void patch_maxRowsNull_clearsToDefault() throws Exception {
    SqlTimeseriesConfig current = new SqlTimeseriesConfig();
    current.setMaxRows(500_000L);
    current.setMaxDurationIso("PT60S");

    SqlTimeseriesConfig cleared = new SqlTimeseriesConfig();
    // maxRows null → effectiveMaxRows() falls back to deploy-time default

    when(service.current()).thenReturn(current);
    when(service.patch(isNull(), eq("PT60S"))).thenReturn(cleared);

    descriptor.patch(MAPPER.readTree("{\"maxRows\": null}"));

    verify(service).patch(isNull(), eq("PT60S"));
  }

  @Test
  void patch_invalidMaxRows_zero_throwsValidationException() {
    SqlTimeseriesConfig current = new SqlTimeseriesConfig();
    current.setMaxRows(1_000_000L);
    when(service.current()).thenReturn(current);

    ConfigValidationException ex = assertThrows(ConfigValidationException.class,
      () -> descriptor.patch(MAPPER.readTree("{\"maxRows\": 0}")));

    assertEquals(SqlTimeseriesConfigDescriptor.PROBLEM_MAX_ROWS, ex.getProblemType());
  }

  @Test
  void patch_invalidMaxRows_negative_throwsValidationException() throws Exception {
    SqlTimeseriesConfig current = new SqlTimeseriesConfig();
    current.setMaxRows(1_000_000L);
    when(service.current()).thenReturn(current);

    ConfigValidationException ex = assertThrows(ConfigValidationException.class,
      () -> descriptor.patch(MAPPER.readTree("{\"maxRows\": -1}")));

    assertEquals(SqlTimeseriesConfigDescriptor.PROBLEM_MAX_ROWS, ex.getProblemType());
  }

  @Test
  void patch_invalidMaxDuration_throwsValidationException() {
    SqlTimeseriesConfig current = new SqlTimeseriesConfig();
    current.setMaxDurationIso("PT60S");
    when(service.current()).thenReturn(current);

    ConfigValidationException ex = assertThrows(ConfigValidationException.class,
      () -> descriptor.patch(MAPPER.readTree("{\"maxDuration\": \"not-a-duration\"}")));

    assertEquals(SqlTimeseriesConfigDescriptor.PROBLEM_MAX_DURATION, ex.getProblemType());
  }

  @Test
  void patch_validDuration_applies() throws Exception {
    SqlTimeseriesConfig current = new SqlTimeseriesConfig();
    current.setMaxRows(1_000_000L);
    current.setMaxDurationIso("PT60S");

    SqlTimeseriesConfig updated = new SqlTimeseriesConfig();
    updated.setMaxRows(1_000_000L);
    updated.setMaxDurationIso("PT2M30S");

    when(service.current()).thenReturn(current);
    when(service.patch(eq(1_000_000L), eq("PT2M30S"))).thenReturn(updated);

    Object result = descriptor.patch(MAPPER.readTree("{\"maxDuration\": \"PT2M30S\"}"));

    SqlTimeseriesConfigIO io = assertInstanceOf(SqlTimeseriesConfigIO.class, result);
    assertEquals("PT2M30S", io.maxDuration());
  }
}
