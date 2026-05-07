package de.dlr.shepard.data.timeseries.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.dlr.shepard.data.timeseries.model.Timeseries;
import de.dlr.shepard.data.timeseries.model.TimeseriesDataPoint;
import de.dlr.shepard.data.timeseries.model.TimeseriesEntity;
import de.dlr.shepard.data.timeseries.model.enums.DataPointValueType;
import de.dlr.shepard.data.timeseries.repositories.TimeseriesDataPointRepository;
import de.dlr.shepard.data.timeseries.repositories.TimeseriesRepository;
import de.dlr.shepard.data.timeseries.utilities.ObjectTypeEvaluator;
import de.dlr.shepard.data.timeseries.utilities.TimeseriesValidator;
import io.quarkus.logging.Log;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@RequestScoped
public class TimeseriesNdjsonService {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Inject
  TimeseriesContainerService timeseriesContainerService;

  @Inject
  TimeseriesRepository timeseriesRepository;

  @Inject
  TimeseriesDataPointRepository timeseriesDataPointRepository;

  @Inject
  Validator validator;

  @ConfigProperty(name = "shepard.timeseries.ingest.ndjson.batch-size", defaultValue = "5000")
  int batchSize;

  @ConfigProperty(name = "shepard.timeseries.ingest.ndjson.max-duration", defaultValue = "PT5M")
  Duration maxDuration;

  public int getBatchSize() {
    return batchSize;
  }

  public Duration getMaxDuration() {
    return maxDuration;
  }

  /**
   * Stream-ingest NDJSON data points: parse one JSON object per line, validate
   * each independently, write in batches via COPY, and emit per-line errors plus
   * a final summary line as NDJSON in the response.
   */
  public void ingest(long containerId, Timeseries timeseries, InputStream input, OutputStream output)
    throws IOException {
    timeseriesContainerService.getContainer(containerId);
    timeseriesContainerService.assertIsAllowedToEditContainer(containerId);
    TimeseriesValidator.assertTimeseriesPropertiesAreValid(timeseries);

    long startNanos = System.nanoTime();
    long deadlineNanos = startNanos + maxDuration.toNanos();

    Writer writer = new OutputStreamWriter(output, StandardCharsets.UTF_8);
    BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8));

    long accepted = 0;
    long rejected = 0;
    boolean truncated = false;

    TimeseriesEntity timeseriesEntity = null;
    List<TimeseriesDataPoint> pendingBatch = new ArrayList<>(batchSize);
    long pendingBatchStartLine = 0;
    long lineNumber = 0;
    String rawLine;

    while ((rawLine = reader.readLine()) != null) {
      if (System.nanoTime() >= deadlineNanos) {
        truncated = true;
        break;
      }
      lineNumber++;
      String line = rawLine.strip();
      if (line.isEmpty()) continue;

      TimeseriesDataPoint point;
      try {
        point = parseDataPoint(line);
      } catch (IOException ex) {
        rejected++;
        writeLineEvent(writer, lineNumber, "error", "invalid json: " + rootMessage(ex));
        continue;
      }

      Set<ConstraintViolation<TimeseriesDataPoint>> violations = validator.validate(point);
      if (!violations.isEmpty()) {
        rejected++;
        writeLineEvent(writer, lineNumber, "error", formatViolations(violations));
        continue;
      }

      Optional<DataPointValueType> typeOpt = ObjectTypeEvaluator.determineType(point.getValue());
      if (typeOpt.isEmpty()) {
        rejected++;
        writeLineEvent(writer, lineNumber, "error", "unsupported value type");
        continue;
      }
      DataPointValueType lineType = typeOpt.get();

      if (timeseriesEntity == null) {
        timeseriesEntity = resolveOrCreateTimeseries(containerId, timeseries, lineType);
      }

      if (lineType != timeseriesEntity.getValueType()) {
        rejected++;
        writeLineEvent(
          writer,
          lineNumber,
          "error",
          "value type %s does not match timeseries type %s".formatted(lineType, timeseriesEntity.getValueType())
        );
        continue;
      }

      if (pendingBatch.isEmpty()) {
        pendingBatchStartLine = lineNumber;
      }
      pendingBatch.add(point);

      if (pendingBatch.size() >= batchSize) {
        long batchEndLine = lineNumber;
        int written = flushBatch(pendingBatch, timeseriesEntity, writer, pendingBatchStartLine, batchEndLine);
        if (written < 0) {
          rejected += pendingBatch.size();
        } else {
          accepted += written;
        }
        pendingBatch.clear();
      }
    }

    if (!pendingBatch.isEmpty()) {
      long batchEndLine = pendingBatchStartLine + pendingBatch.size() - 1;
      int written = flushBatch(pendingBatch, timeseriesEntity, writer, pendingBatchStartLine, batchEndLine);
      if (written < 0) {
        rejected += pendingBatch.size();
      } else {
        accepted += written;
      }
      pendingBatch.clear();
    }

    long durationMs = (System.nanoTime() - startNanos) / 1_000_000L;
    writeSummary(writer, accepted, rejected, durationMs, truncated);
    writer.flush();
  }

  private static TimeseriesDataPoint parseDataPoint(String line) throws IOException {
    JsonNode node = MAPPER.readTree(line);
    if (node == null || !node.isObject()) {
      throw new IOException("expected a JSON object");
    }
    JsonNode tsNode = node.get("timestamp");
    JsonNode valueNode = node.get("value");
    if (tsNode == null || !tsNode.canConvertToLong()) {
      throw new IOException("missing or invalid 'timestamp'");
    }
    if (valueNode == null) {
      throw new IOException("missing 'value'");
    }
    Object value;
    if (valueNode.isBoolean()) value = valueNode.booleanValue();
    else if (valueNode.isIntegralNumber()) value = valueNode.longValue();
    else if (valueNode.isFloatingPointNumber()) value = valueNode.doubleValue();
    else if (valueNode.isTextual()) value = valueNode.textValue();
    else if (valueNode.isNull()) value = null;
    else throw new IOException("unsupported 'value' type");
    return new TimeseriesDataPoint(tsNode.longValue(), value);
  }

  private TimeseriesEntity resolveOrCreateTimeseries(long containerId, Timeseries timeseries, DataPointValueType type) {
    Optional<TimeseriesEntity> matching = timeseriesRepository.findTimeseries(containerId, timeseries);
    if (matching.isPresent()) return matching.get();
    TimeseriesEntity created = new TimeseriesEntity(containerId, timeseries, type);
    if (QuarkusTransaction.isActive()) {
      timeseriesRepository.upsert(containerId, created);
    } else {
      QuarkusTransaction.requiringNew().run(() -> timeseriesRepository.upsert(containerId, created));
    }
    return timeseriesRepository.findTimeseries(containerId, timeseries).orElse(created);
  }

  /**
   * Write one batch via the COPY-based repository call. Returns the number of
   * rows written on success, or -1 if the whole batch failed.
   */
  protected int flushBatch(
    List<TimeseriesDataPoint> batch,
    TimeseriesEntity timeseriesEntity,
    Writer writer,
    long startLine,
    long endLine
  ) throws IOException {
    int size = batch.size();
    try {
      timeseriesDataPointRepository.insertManyDataPointsWithCopyCommand(batch, timeseriesEntity);
      writer.write(
        "{\"line\":%d,\"status\":\"ok\",\"batchEndLine\":%d,\"count\":%d}\n".formatted(startLine, endLine, size)
      );
      writer.flush();
      return size;
    } catch (SQLException | RuntimeException ex) {
      Log.errorf(ex, "NDJSON ingest batch (lines %d-%d) failed", startLine, endLine);
      writer.write(
        "{\"line\":%d,\"status\":\"error\",\"batchEndLine\":%d,\"message\":%s}\n".formatted(
            startLine,
            endLine,
            quote("batch insert failed: " + rootMessage(ex))
          )
      );
      writer.flush();
      return -1;
    }
  }

  private void writeLineEvent(Writer writer, long lineNumber, String status, String message) throws IOException {
    if ("ok".equals(status)) {
      writer.write("{\"line\":%d,\"status\":\"ok\"}\n".formatted(lineNumber));
    } else {
      writer.write("{\"line\":%d,\"status\":\"%s\",\"message\":%s}\n".formatted(lineNumber, status, quote(message)));
    }
    writer.flush();
  }

  private void writeSummary(Writer writer, long accepted, long rejected, long durationMs, boolean truncated)
    throws IOException {
    if (truncated) {
      writer.write(
        "{\"summary\":{\"accepted\":%d,\"rejected\":%d,\"durationMs\":%d},\"truncated\":true}\n".formatted(
            accepted,
            rejected,
            durationMs
          )
      );
    } else {
      writer.write(
        "{\"summary\":{\"accepted\":%d,\"rejected\":%d,\"durationMs\":%d}}\n".formatted(accepted, rejected, durationMs)
      );
    }
    writer.flush();
  }

  private static String quote(String value) {
    StringBuilder sb = new StringBuilder(value.length() + 2);
    sb.append('"');
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      switch (c) {
        case '"' -> sb.append("\\\"");
        case '\\' -> sb.append("\\\\");
        case '\n' -> sb.append("\\n");
        case '\r' -> sb.append("\\r");
        case '\t' -> sb.append("\\t");
        default -> {
          if (c < 0x20) {
            sb.append(String.format("\\u%04x", (int) c));
          } else {
            sb.append(c);
          }
        }
      }
    }
    sb.append('"');
    return sb.toString();
  }

  private static String formatViolations(Set<ConstraintViolation<TimeseriesDataPoint>> violations) {
    StringBuilder sb = new StringBuilder("validation failed: ");
    boolean first = true;
    for (ConstraintViolation<TimeseriesDataPoint> v : violations) {
      if (!first) sb.append("; ");
      sb.append(v.getPropertyPath()).append(' ').append(v.getMessage());
      first = false;
    }
    return sb.toString();
  }

  private static String rootMessage(Throwable t) {
    Throwable cur = t;
    while (cur.getCause() != null && cur.getCause() != cur) cur = cur.getCause();
    return cur.getMessage() == null ? cur.getClass().getSimpleName() : cur.getMessage();
  }
}
