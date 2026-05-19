package de.dlr.shepard.data.timeseries.utilities;

import com.opencsv.bean.CsvToBeanBuilder;
import de.dlr.shepard.common.exceptions.InvalidBodyException;
import de.dlr.shepard.common.exceptions.InvalidRequestException;
import de.dlr.shepard.data.timeseries.io.TimeseriesWithDataPoints;
import de.dlr.shepard.data.timeseries.model.Timeseries;
import de.dlr.shepard.data.timeseries.model.TimeseriesDataPoint;
import de.dlr.shepard.data.timeseries.model.enums.CsvFormat;
import io.quarkus.logging.Log;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import org.apache.commons.lang3.math.NumberUtils;

public final class CsvConverter {

  private CsvConverter() {}

  public static InputStream convertToCsv(
    Timeseries timeseries,
    List<TimeseriesDataPoint> dataPoints,
    CsvFormat format
  ) {
    return convertToCsv(List.of(new TimeseriesWithDataPoints(timeseries, dataPoints)), format);
  }

  public static InputStream convertToCsv(
    List<TimeseriesWithDataPoints> timeseriesWithDataPointsList,
    CsvFormat format
  ) {
    CsvFormat formatNonNull = format != null ? format : CsvFormat.ROW;
    CsvLineProvider provider =
      switch (formatNonNull) {
        case ROW -> new CsvRowLineProvider(timeseriesWithDataPointsList);
        case COLUMN -> new CsvColumnLineProvider(timeseriesWithDataPointsList);
      };
    return new CsvInputStream(provider);
  }

  public static List<TimeseriesWithDataPoints> convertToTimeseriesWithData(InputStream stream) {
    try (var reader = new InputStreamReader(stream)) {
      var timeseriesDataBuilder = new CsvToBeanBuilder<CsvTimeseriesDataPoint>(reader)
        .withType(CsvTimeseriesDataPoint.class)
        .withErrorLocale(Locale.forLanguageTag("en"))
        .withExceptionHandler(e -> {
          var encoder = StandardCharsets.ISO_8859_1.newEncoder();
          var message = encoder.canEncode(e.getMessage()) ? e.getMessage() : "Invalid CSV";
          Log.errorf("CsvException while reading stream: %s", message);
          throw new InvalidBodyException(message);
        })
        .build();

      List<CsvTimeseriesDataPoint> result = timeseriesDataBuilder.parse();

      return convertCsvToTimeseriesWithData(result);
    } catch (IOException e) {
      Log.error("IOException while reading the provided InputStream", e);
      throw new InvalidRequestException();
    }
  }

  private static List<TimeseriesWithDataPoints> convertCsvToTimeseriesWithData(
    List<CsvTimeseriesDataPoint> csvInputList
  ) {
    HashMap<Timeseries, List<TimeseriesDataPoint>> result = new HashMap<Timeseries, List<TimeseriesDataPoint>>();

    for (var csvInputLine : csvInputList) {
      var timeseries = new Timeseries(
        csvInputLine.getMeasurement(),
        csvInputLine.getDevice(),
        csvInputLine.getLocation(),
        csvInputLine.getSymbolicName(),
        csvInputLine.getField()
      );
      var dataPoint = new TimeseriesDataPoint(csvInputLine.getTimestamp(), parseValue(csvInputLine.getValue()));

      if (result.containsKey(timeseries)) {
        result.get(timeseries).add(dataPoint);
      } else {
        var dataPoints = new ArrayList<TimeseriesDataPoint>();
        dataPoints.add(dataPoint);
        result.put(timeseries, dataPoints);
      }
    }
    List<TimeseriesWithDataPoints> timeseriesWithDataPointsList = result
      .entrySet()
      .stream()
      .map(entry -> new TimeseriesWithDataPoints(entry.getKey(), entry.getValue()))
      .collect(Collectors.toList());
    return timeseriesWithDataPointsList;
  }

  private static Object parseValue(Object input) {
    List<String> boolString = List.of("true", "false");
    if (input instanceof String sInput) {
      // Fix D: coerce IEEE 754 special-value strings to null so they are
      // skipped rather than stored as non-finite doubles or mis-classified
      // as String type (which would lock the channel to String forever).
      String trimmed = sInput.trim();
      String lower = trimmed.toLowerCase();
      if (lower.equals("nan") || lower.equals("infinity") || lower.equals("-infinity") || lower.equals("+infinity")) {
        return null;
      }
      if (NumberUtils.isCreatable(sInput)) {
        try {
          return Integer.parseInt(sInput);
        } catch (NumberFormatException e) {
          double d = Double.parseDouble(sInput);
          // Guard: isCreatable may accept "NaN"/"Infinity" in some Commons Lang versions.
          if (!Double.isFinite(d)) return null;
          return d;
        }
      } else if (boolString.contains(lower)) {
        return sInput.equalsIgnoreCase("true");
      }
    }
    // Fix D: non-finite Double arriving directly (e.g. from Jackson) → skip.
    if (input instanceof Double d && !Double.isFinite(d)) return null;
    return input;
  }
}
