package de.dlr.shepard.influxDB;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import org.apache.commons.lang3.math.NumberUtils;

import com.opencsv.bean.CsvToBeanBuilder;
import com.opencsv.bean.StatefulBeanToCsvBuilder;
import com.opencsv.exceptions.CsvException;

import de.dlr.shepard.exceptions.InvalidBodyException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CsvConverter {

	public InputStream convertToCsv(List<TimeseriesPayload> payloads) throws IOException {
		// TODO: Avoid writing files

		var tmpfile = Files.createTempFile("shepard", ".csv");
		var stream = Files.newOutputStream(tmpfile);
		var streamWriter = new OutputStreamWriter(stream);
		var writer = new StatefulBeanToCsvBuilder<TimeseriesCsv>(streamWriter).withApplyQuotesToAll(false).build();
		log.debug("Write temp file to: {}", tmpfile.toAbsolutePath().toString());

		for (var payload : payloads) {
			try {
				writer.write(convertPayloadToCsv(payload));
			} catch (CsvException e) {
				log.error("CsvException while writing stream");
			}
		}

		streamWriter.close();
		var result = Files.newInputStream(tmpfile);
		return result;
	}

	public List<TimeseriesPayload> convertToPayload(InputStream stream) throws IOException {
		var reader = new InputStreamReader(stream);
		var cb = new CsvToBeanBuilder<TimeseriesCsv>(reader).withType(TimeseriesCsv.class)
				.withErrorLocale(Locale.forLanguageTag("en")).withExceptionHandler(e -> {
					var encoder = StandardCharsets.ISO_8859_1.newEncoder();
					var message = encoder.canEncode(e.getMessage()) ? e.getMessage() : "Invalid CSV";
					log.error("CsvException while reading stream: {}", message);
					throw new InvalidBodyException(message);
				}).build();

		List<TimeseriesCsv> result = cb.parse();
		reader.close();
		return convertCsvToPayload(result);
	}

	private List<TimeseriesCsv> convertPayloadToCsv(TimeseriesPayload payload) {
		var ts = payload.getTimeseries();
		var result = new ArrayList<TimeseriesCsv>(payload.getPoints().size());
		for (var p : payload.getPoints()) {
			var tsc = new TimeseriesCsv(p.getTimeInNanoseconds(), ts.getMeasurement(), ts.getDevice(), ts.getLocation(),
					ts.getSymbolicName(), ts.getField(), p.getValue());
			result.add(tsc);
		}
		return result;
	}

	private List<TimeseriesPayload> convertCsvToPayload(List<TimeseriesCsv> inputList) {
		var result = new HashMap<Integer, TimeseriesPayload>();
		for (var input : inputList) {
			var key = Objects.hash(input.getMeasurement(), input.getDevice(), input.getLocation(),
					input.getSymbolicName(), input.getField());
			var point = new InfluxPoint(input.getTimestamp(), parseValue(input.getValue()));
			if (result.containsKey(key)) {
				result.get(key).getPoints().add(point);
			} else {
				var points = new ArrayList<InfluxPoint>();
				points.add(point);
				var payload = new TimeseriesPayload(new Timeseries(input.getMeasurement(), input.getDevice(),
						input.getLocation(), input.getSymbolicName(), input.getField()), points);
				result.put(key, payload);
			}
		}
		return new ArrayList<>(result.values());
	}

	private Object parseValue(Object input) {
		List<String> boolString = List.of("true", "false");

		if (input instanceof String sInput) {
			if (NumberUtils.isCreatable(sInput)) {
				return NumberUtils.createNumber(sInput);
			} else if (boolString.contains(sInput.toLowerCase())) {
				return sInput.equalsIgnoreCase("true");
			}
		}
		return input;
	}

}
