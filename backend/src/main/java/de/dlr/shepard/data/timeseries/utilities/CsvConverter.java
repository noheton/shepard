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
import java.util.Set;
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
      .map(entry -> {
        Timeseries ts = entry.getKey();
        List<TimeseriesDataPoint> pts = entry.getValue();
        if (isBinaryChannel(ts, pts)) {
          pts = coerceToBooleanPoints(pts);
          Log.infof("[ts-import] binary channel detected — coercing to Boolean: %s / %s", ts.getMeasurement(), ts.getField());
        }
        return new TimeseriesWithDataPoints(ts, pts);
      })
      .collect(Collectors.toList());
    return timeseriesWithDataPointsList;
  }

  // Keywords in measurement or field that signal a binary (on/off) channel.
  private static final Set<String> BINARY_NAME_TOKENS = Set.of(
    "digital", "binary", "bool", "boolean", "status", "flag",
    "enable", "enabled", "active", "switch", "relay", "on_off", "onoff", "state"
  );

  // Returns true if the channel looks like a binary (0/1) signal.
  // Primary: measurement or field name contains a binary-indicative token.
  // Fallback: all non-null Double values are exactly 0.0 or 1.0 and both appear.
  static boolean isBinaryChannel(Timeseries ts, List<TimeseriesDataPoint> pts) {
    String measurement = ts.getMeasurement() == null ? "" : ts.getMeasurement().toLowerCase(Locale.ROOT);
    String field = ts.getField() == null ? "" : ts.getField().toLowerCase(Locale.ROOT);
    for (String token : BINARY_NAME_TOKENS) {
      if (measurement.contains(token) || field.contains(token)) {
        return true;
      }
    }
    // Value-spread fallback: all numeric values are in {0.0, 1.0} with both present.
    boolean hasZero = false;
    boolean hasOne = false;
    boolean hasOther = false;
    int nonNullCount = 0;
    for (TimeseriesDataPoint dp : pts) {
      Object v = dp.getValue();
      if (v == null) continue;
      nonNullCount++;
      if (v instanceof Double d) {
        if (d == 0.0) { hasZero = true; }
        else if (d == 1.0) { hasOne = true; }
        else { hasOther = true; break; }
      } else if (v instanceof Integer i) {
        if (i == 0) { hasZero = true; }
        else if (i == 1) { hasOne = true; }
        else { hasOther = true; break; }
      } else {
        hasOther = true;
        break;
      }
    }
    return !hasOther && nonNullCount >= 2 && (hasZero || hasOne);
  }

  // Converts Double/Integer 0→false and 1→true; leaves nulls as null.
  private static List<TimeseriesDataPoint> coerceToBooleanPoints(List<TimeseriesDataPoint> pts) {
    List<TimeseriesDataPoint> out = new ArrayList<>(pts.size());
    for (TimeseriesDataPoint dp : pts) {
      Object v = dp.getValue();
      Boolean bVal = null;
      if (v instanceof Double d) {
        bVal = d != 0.0;
      } else if (v instanceof Integer i) {
        bVal = i != 0;
      } else if (v instanceof Boolean b) {
        bVal = b; // already boolean
      }
      out.add(new TimeseriesDataPoint(dp.getTimestamp(), bVal));
    }
    return out;
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
