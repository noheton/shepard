package de.dlr.shepard.influxDB;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import de.dlr.shepard.BaseTestCase;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Stream;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class CsvConverterTest extends BaseTestCase {

  private CsvConverter service = new CsvConverter();

  private Timeseries ts1 = new Timeseries("meas", "dev", "loc", "sym", "field");
  private Timeseries ts2 = new Timeseries("meas2", "dev2", "loc2", "sym2", "field2");
  private Timeseries ts3 = new Timeseries("meas2", "dev2", "loc2", "sym2", "field3");

  private static String csv =
    """
    DEVICE,FIELD,LOCATION,MEASUREMENT,SYMBOLICNAME,TIMESTAMP,VALUE
    dev,field,loc,meas,sym,123,string
    dev,field,loc,meas,sym,234,another string
    dev,field,loc,meas,sym,345,true
    dev,field,loc,meas,sym,456,-123
    dev,field,loc,meas,sym,567,123.456
    dev,field,loc,meas,sym,678,1.23E45
    dev,field,loc,meas,sym,789,
    dev2,field2,loc2,meas2,sym2,123,string
    dev2,field2,loc2,meas2,sym2,234,another string
    dev2,field2,loc2,meas2,sym2,345,true
    dev2,field2,loc2,meas2,sym2,456,-123
    dev2,field2,loc2,meas2,sym2,567,123.456
    dev2,field2,loc2,meas2,sym2,678,1.23E45
    dev2,field2,loc2,meas2,sym2,789,
    dev2,field3,loc2,meas2,sym2,123,string
    dev2,field3,loc2,meas2,sym2,234,another string
    dev2,field3,loc2,meas2,sym2,345,true
    dev2,field3,loc2,meas2,sym2,456,-123
    dev2,field3,loc2,meas2,sym2,567,123.456
    dev2,field3,loc2,meas2,sym2,678,1.23E45
    dev2,field3,loc2,meas2,sym2,789,
    """;

  private static InfluxPoint p1 = new InfluxPoint(123L, "string");
  private static InfluxPoint p2 = new InfluxPoint(234L, "another string");
  private static InfluxPoint p3 = new InfluxPoint(345L, true);
  private static InfluxPoint p4 = new InfluxPoint(456L, -123);
  private static InfluxPoint p5 = new InfluxPoint(567L, 123.456F);
  private static InfluxPoint p6 = new InfluxPoint(678L, 1.23E45D);
  private static InfluxPoint p7 = new InfluxPoint(789L, null);

  private static String csv_missingColumn =
    """
    DEVICE,FIELD,LOCATION,MEASUREMENT,TIMESTAMP,VALUE
    dev,field,loc,meas,234,123
    dev,field,loc,meas,432,563
    """;
  private static String csv_missingValue =
    """
    DEVICE,FIELD,LOCATION,MEASUREMENT,SYMBOLICNAME,TIMESTAMP,VALUE
    dev,field,loc,meas,sym,,123
    dev,field,loc,meas,sym,234,543
    """;
  private static String csv_invalidType =
    """
    DEVICE,FIELD,LOCATION,MEASUREMENT,SYMBOLICNAME,TIMESTAMP,VALUE
    dev,field,loc,meas,sym,wrongType,123
    dev,field,loc,meas,sym,432,563
    """;
  private static String csv_gibberish =
    """
    DEVICE,FIELD,LOCATION,MEASUREMENT,SYMBOLICNAME,TIMESTAMP,VALUE
    dev,field,loc,meas,234sdgv
    dev,field,loc,meas,sym,234fdsa
    """;

  @Test
  public void toCsvTest() throws IOException {
    var payload1 = new TimeseriesPayload(ts1, List.of(p1, p2, p3, p4, p5, p6, p7));
    var payload2 = new TimeseriesPayload(ts2, List.of(p1, p2, p3, p4, p5, p6, p7));
    var payload3 = new TimeseriesPayload(ts3, List.of(p1, p2, p3, p4, p5, p6, p7));

    var actual = service.convertToCsv(List.of(payload1, payload2, payload3));
    assertEquals(csv, IOUtils.toString(actual, StandardCharsets.UTF_8));
  }

  @Test
  public void toPayloadTest() throws IOException {
    var payload = service.convertToPayload(new ByteArrayInputStream(csv.getBytes()));
    var payload1 = new TimeseriesPayload(ts1, List.of(p1, p2, p3, p4, p5, p6, p7));
    var payload2 = new TimeseriesPayload(ts2, List.of(p1, p2, p3, p4, p5, p6, p7));
    var payload3 = new TimeseriesPayload(ts3, List.of(p1, p2, p3, p4, p5, p6, p7));

    assertThat(payload).containsAll(List.of(payload1, payload2, payload3));
  }

  private static Stream<Arguments> toPayloadTest_Exception() {
    // @formatter:off
	    return Stream.of(
	    		Arguments.of(csv_missingColumn, "Header is missing required fields [SYMBOLICNAME]. The list of headers encountered is [DEVICE,FIELD,LOCATION,MEASUREMENT,TIMESTAMP,VALUE]."),
	    		Arguments.of(csv_missingValue, "Field 'timestamp' is mandatory but no value was provided."),
    	        Arguments.of(csv_invalidType, "Conversion of wrongType to long failed."),
	    	    Arguments.of(csv_gibberish, "Number of data fields does not match number of headers.")
	    	    );
    // @formatter:on
  }

  @ParameterizedTest
  @MethodSource
  public void toPayloadTest_Exception(String csv, String exception) throws IOException {
    var inputStream = new ByteArrayInputStream(csv.getBytes());
    var ex = assertThrows(RuntimeException.class, () -> service.convertToPayload(inputStream));
    assertEquals(exception, ex.getCause().getMessage());
  }
}
