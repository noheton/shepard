package de.dlr.shepard.data.timeseries.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.dlr.shepard.common.exceptions.InvalidAuthException;
import de.dlr.shepard.data.timeseries.model.Timeseries;
import de.dlr.shepard.data.timeseries.model.TimeseriesDataPoint;
import de.dlr.shepard.data.timeseries.model.TimeseriesEntity;
import de.dlr.shepard.data.timeseries.model.enums.DataPointValueType;
import de.dlr.shepard.data.timeseries.repositories.TimeseriesDataPointRepository;
import de.dlr.shepard.data.timeseries.repositories.TimeseriesRepository;
import io.quarkus.test.InjectMock;
import io.quarkus.test.component.QuarkusComponentTest;
import io.quarkus.test.component.TestConfigProperty;
import jakarta.inject.Inject;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

@QuarkusComponentTest
@TestConfigProperty(key = "shepard.timeseries.ingest.ndjson.batch-size", value = "5000")
@TestConfigProperty(key = "shepard.timeseries.ingest.ndjson.max-duration", value = "PT5M")
public class TimeseriesNdjsonServiceTest {

  private static final ObjectMapper JSON = new ObjectMapper();

  @Inject
  TimeseriesNdjsonService service;

  @InjectMock
  TimeseriesContainerService containerService;

  @InjectMock
  TimeseriesDataPointRepository dataPointRepository;

  @InjectMock
  TimeseriesRepository timeseriesRepository;

  private Timeseries newTimeseries() {
    return new Timeseries("measurement", "device", "location", "symbolicName", "field");
  }

  private void mockResolvedTimeseries(long containerId, DataPointValueType type) {
    var entity = new TimeseriesEntity(containerId, newTimeseries(), type);
    when(timeseriesRepository.findTimeseries(anyLong(), any())).thenReturn(Optional.of(entity));
  }

  private List<JsonNode> parseNdjson(byte[] body) throws IOException {
    var lines = new String(body, StandardCharsets.UTF_8).split("\n");
    var nodes = new ArrayList<JsonNode>();
    for (var line : lines) {
      if (line.isEmpty()) continue;
      nodes.add(JSON.readTree(line));
    }
    return nodes;
  }

  @Test
  public void ingest_mixedValidAndInvalid_acceptsValidRejectsInvalid() throws Exception {
    long containerId = 1L;
    when(containerService.getContainer(containerId)).thenReturn(null);
    doNothing().when(containerService).assertIsAllowedToEditContainer(containerId);
    mockResolvedTimeseries(containerId, DataPointValueType.Integer);

    StringBuilder body = new StringBuilder();
    body.append("{\"timestamp\":1000,\"value\":1}\n");
    body.append("{\"timestamp\":1001,\"value\":2}\n");
    body.append("not-json-line\n");
    body.append("{\"timestamp\":1003,\"value\":3}\n");
    body.append("{\"timestamp\":1004,\"value\":4}\n");
    body.append("{\"timestamp\":1005,\"value\":\"unsupportedTypeForExistingTs\"}\n");
    body.append("{\"timestamp\":1006,\"value\":6}\n");
    body.append("{\"timestamp\":1007,\"value\":7}\n");
    body.append("{\"timestamp\":1008,\"value\":8}\n");
    body.append("{\"timestamp\":1009,\"value\":9}\n");

    var input = new ByteArrayInputStream(body.toString().getBytes(StandardCharsets.UTF_8));
    var output = new ByteArrayOutputStream();
    service.ingest(containerId, newTimeseries(), input, output);

    var nodes = parseNdjson(output.toByteArray());
    var summary = nodes.get(nodes.size() - 1);
    assertNotNull(summary.get("summary"));
    assertEquals(8, summary.get("summary").get("accepted").asInt());
    assertEquals(2, summary.get("summary").get("rejected").asInt());

    long errors = nodes
      .stream()
      .filter(n -> n.has("status") && "error".equals(n.get("status").asText()))
      .count();
    assertEquals(2, errors);

    var firstError = nodes
      .stream()
      .filter(n -> n.has("status") && "error".equals(n.get("status").asText()))
      .findFirst();
    assertTrue(firstError.isPresent());
    assertNotNull(firstError.get().get("message"));
    assertTrue(firstError.get().get("message").asText().length() > 0);

    verify(dataPointRepository, times(1)).insertManyDataPointsWithCopyCommand(any(), any());
  }

  @Test
  public void ingest_largeInput_streamsResponseInMultipleChunks() throws Exception {
    long containerId = 2L;
    when(containerService.getContainer(containerId)).thenReturn(null);
    doNothing().when(containerService).assertIsAllowedToEditContainer(containerId);
    mockResolvedTimeseries(containerId, DataPointValueType.Integer);

    int total = 200;
    StringBuilder body = new StringBuilder();
    for (int i = 0; i < total; i++) {
      body.append("{\"timestamp\":").append(1000 + i).append(",\"value\":").append(i).append("}\n");
    }

    var input = new ByteArrayInputStream(body.toString().getBytes(StandardCharsets.UTF_8));
    var chunkRecorder = new ChunkRecordingOutputStream();
    service.ingest(containerId, newTimeseries(), input, chunkRecorder);

    var nodes = parseNdjson(chunkRecorder.toByteArray());
    var summary = nodes.get(nodes.size() - 1);
    assertEquals(total, summary.get("summary").get("accepted").asInt());

    assertTrue(
      chunkRecorder.flushCount >= 2,
      "Expected response to be flushed at least twice (incremental streaming) but got " + chunkRecorder.flushCount
    );
  }

  @Test
  public void ingest_twelveThousandLines_runsExactlyThreeBatches() throws Exception {
    long containerId = 3L;
    when(containerService.getContainer(containerId)).thenReturn(null);
    doNothing().when(containerService).assertIsAllowedToEditContainer(containerId);
    mockResolvedTimeseries(containerId, DataPointValueType.Integer);

    int total = 12_000;
    StringBuilder body = new StringBuilder(total * 35);
    for (int i = 0; i < total; i++) {
      body.append("{\"timestamp\":").append(1_000_000L + i).append(",\"value\":").append(i).append("}\n");
    }

    AtomicInteger batchCalls = new AtomicInteger(0);
    AtomicInteger lastBatchSize = new AtomicInteger(0);
    doAnswer(invocation -> {
        List<TimeseriesDataPoint> batch = invocation.getArgument(0);
        batchCalls.incrementAndGet();
        lastBatchSize.set(batch.size());
        return null;
      })
      .when(dataPointRepository)
      .insertManyDataPointsWithCopyCommand(any(), any());

    var input = new ByteArrayInputStream(body.toString().getBytes(StandardCharsets.UTF_8));
    var output = new ByteArrayOutputStream();
    service.ingest(containerId, newTimeseries(), input, output);

    assertEquals(3, batchCalls.get(), "Expected exactly three batches for 12000 lines at batch-size 5000");
    assertEquals(2000, lastBatchSize.get(), "Last batch should hold the trailing 2000 points");

    var nodes = parseNdjson(output.toByteArray());
    var summary = nodes.get(nodes.size() - 1);
    assertEquals(total, summary.get("summary").get("accepted").asInt());
    assertEquals(0, summary.get("summary").get("rejected").asInt());
  }

  @Test
  public void ingest_withoutWritePermission_throwsInvalidAuth() throws Exception {
    long containerId = 4L;
    when(containerService.getContainer(containerId)).thenReturn(null);
    doThrow(new InvalidAuthException("no write")).when(containerService).assertIsAllowedToEditContainer(containerId);

    var input = new ByteArrayInputStream("{\"timestamp\":1,\"value\":1}\n".getBytes(StandardCharsets.UTF_8));
    var output = new ByteArrayOutputStream();
    assertThrows(InvalidAuthException.class, () -> service.ingest(containerId, newTimeseries(), input, output));
    verify(dataPointRepository, times(0)).insertManyDataPointsWithCopyCommand(any(), any());
  }

  // Verify auth is checked before reading any line of input.
  @Test
  public void ingest_authIsCheckedBeforeStreaming() throws Exception {
    long containerId = 5L;
    when(containerService.getContainer(containerId)).thenReturn(null);
    doThrow(new InvalidAuthException("forbidden")).when(containerService).assertIsAllowedToEditContainer(containerId);

    var failingInput = new java.io.InputStream() {
      @Override
      public int read() {
        throw new AssertionError("Input must not be read when auth fails");
      }
    };
    var output = new ByteArrayOutputStream();
    assertThrows(InvalidAuthException.class, () -> service.ingest(containerId, newTimeseries(), failingInput, output));
  }

  static class ChunkRecordingOutputStream extends OutputStream {

    private final ByteArrayOutputStream delegate = new ByteArrayOutputStream();
    int flushCount = 0;
    int writesSinceLastFlush = 0;

    @Override
    public void write(int b) throws IOException {
      delegate.write(b);
      writesSinceLastFlush++;
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
      delegate.write(b, off, len);
      writesSinceLastFlush++;
    }

    @Override
    public void flush() throws IOException {
      if (writesSinceLastFlush > 0) {
        flushCount++;
        writesSinceLastFlush = 0;
      }
    }

    public byte[] toByteArray() {
      return delegate.toByteArray();
    }
  }
}
