package de.dlr.shepard.timeseries.utilities;

import com.opencsv.bean.CsvToBeanBuilder;
import com.opencsv.bean.StatefulBeanToCsv;
import com.opencsv.bean.StatefulBeanToCsvBuilder;
import com.opencsv.exceptions.CsvException;
import de.dlr.shepard.exceptions.InvalidBodyException;
import de.dlr.shepard.exceptions.InvalidRequestException;
import de.dlr.shepard.timeseries.io.TimeseriesWithDataPoints;
import de.dlr.shepard.timeseries.model.Timeseries;
import de.dlr.shepard.timeseries.model.TimeseriesDataPoint;
import io.quarkus.logging.Log;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import org.apache.commons.lang3.math.NumberUtils;

public final class CsvConverter {

  public static InputStream convertToCsv(Timeseries timeseries, List<TimeseriesDataPoint> dataPoints) {
    return convertToCsv(List.of(new TimeseriesWithDataPoints(timeseries, dataPoints)));
  }

  public static InputStream convertToCsv(List<TimeseriesWithDataPoints> timeseriesWithDataPointsList) {
    Path tmpfile = null;
    try {
      tmpfile = Files.createTempFile("shepard", ".csv");
      try (var stream = Files.newOutputStream(tmpfile); var streamWriter = new OutputStreamWriter(stream)) {
        StatefulBeanToCsv<CsvTimeseriesDataPoint> writer = new StatefulBeanToCsvBuilder<CsvTimeseriesDataPoint>(
          streamWriter
        )
          .withApplyQuotesToAll(false)
          .build();

        for (var timeseriesWithDataPoints : timeseriesWithDataPointsList) {
          try {
            writer.write(
              convertTimeseriesWithDataToCsv(
                timeseriesWithDataPoints.getTimeseries(),
                timeseriesWithDataPoints.getPoints()
              )
            );
          } catch (CsvException e) {
            Log.error("CsvException while writing stream", e);
            throw new InvalidRequestException();
          }
        }
      }
    } catch (IOException e) {
      Log.error("IOException while creating or writing to the temp file", e);
      throw new InvalidRequestException();
    }

    InputStream result = null;
    if (tmpfile != null) {
      try {
        result = Files.newInputStream(tmpfile);
      } catch (IOException e) {
        Log.error("IOException while opening the temp file for reading", e);
        throw new InvalidRequestException();
      }
    }
    return result;
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

  private static List<CsvTimeseriesDataPoint> convertTimeseriesWithDataToCsv(
    Timeseries timeseries,
    List<TimeseriesDataPoint> dataPoints
  ) {
    var result = new ArrayList<CsvTimeseriesDataPoint>(dataPoints.size());
    for (var dataPoint : dataPoints) {
      var tsc = new CsvTimeseriesDataPoint(
        dataPoint.getTimestamp(),
        timeseries.getMeasurement(),
        timeseries.getDevice(),
        timeseries.getLocation(),
        timeseries.getSymbolicName(),
        timeseries.getField(),
        dataPoint.getValue()
      );
      result.add(tsc);
    }
    return result;
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
      if (NumberUtils.isCreatable(sInput)) {
        try {
          Integer intValue = Integer.parseInt(sInput);
          return intValue;
        } catch (NumberFormatException e) {
          Double doubleValue = Double.parseDouble(sInput);
          return doubleValue;
        }
      } else if (boolString.contains(sInput.toLowerCase())) {
        return sInput.equalsIgnoreCase("true");
      }
    }
    return input;
  }
}
