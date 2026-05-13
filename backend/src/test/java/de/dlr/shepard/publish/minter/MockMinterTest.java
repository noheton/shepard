package de.dlr.shepard.publish.minter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MockMinterTest {

  @Test
  void idIsConstantAndStable() {
    var minter = new MockMinter();
    assertEquals("mock", minter.id());
    assertEquals(MockMinter.ID, minter.id());
  }

  @Test
  void isEnabledAlwaysTrueForMock() {
    assertTrue(new MockMinter().isEnabled());
  }

  @Test
  void mintReturnsPidInExpectedFormat() {
    // Frozen clock so the assertion is deterministic.
    Instant fixed = Instant.ofEpochMilli(1_747_000_000_000L);
    Clock clock = Clock.fixed(fixed, ZoneOffset.UTC);
    MockMinter minter = new MockMinter(clock);
    MintRequest req = new MintRequest(
      "data-objects",
      "01HF1234567890ABCDEF",
      "https://shepard.example.dlr.de/v2/data-objects/01HF1234567890ABCDEF",
      Map.of("name", "TR-004")
    );

    MintResult out = minter.mint(req);

    // PID format per the SPI Javadoc: mock:shepard:<kind>:<appId>:<epoch-millis>
    assertEquals("mock:shepard:data-objects:01HF1234567890ABCDEF:1747000000000", out.pid());
    assertEquals(fixed, out.mintedAt());
    assertEquals("mock", out.minterId());
  }

  @Test
  void mintingDifferentAppIdsProducesDifferentPids() {
    var minter = new MockMinter();
    MintRequest a = new MintRequest("data-objects", "01HF-A", "http://x/v2/data-objects/01HF-A", Map.of());
    MintRequest b = new MintRequest("data-objects", "01HF-B", "http://x/v2/data-objects/01HF-B", Map.of());

    String pidA = minter.mint(a).pid();
    String pidB = minter.mint(b).pid();

    assertNotNull(pidA);
    assertNotNull(pidB);
    assertTrue(pidA.contains("01HF-A"));
    assertTrue(pidB.contains("01HF-B"));
  }

  @Test
  void mintRequestRejectsBlankFields() {
    org.junit.jupiter.api.Assertions.assertThrows(
      IllegalArgumentException.class,
      () -> new MintRequest("", "01HF", "https://x", Map.of())
    );
    org.junit.jupiter.api.Assertions.assertThrows(
      IllegalArgumentException.class,
      () -> new MintRequest("data-objects", null, "https://x", Map.of())
    );
    org.junit.jupiter.api.Assertions.assertThrows(
      IllegalArgumentException.class,
      () -> new MintRequest("data-objects", "01HF", " ", Map.of())
    );
  }

  @Test
  void mintRequestMetadataIsImmutable() {
    var meta = new java.util.HashMap<String, String>();
    meta.put("k", "v");
    MintRequest req = new MintRequest("data-objects", "01HF", "https://x", meta);
    org.junit.jupiter.api.Assertions.assertThrows(UnsupportedOperationException.class, () -> req.metadata().put("k2", "v2"));
  }

  @Test
  void mintResultRejectsBlankFields() {
    org.junit.jupiter.api.Assertions.assertThrows(
      IllegalArgumentException.class,
      () -> new MintResult("", Instant.now(), "mock")
    );
    org.junit.jupiter.api.Assertions.assertThrows(
      IllegalArgumentException.class,
      () -> new MintResult("pid", null, "mock")
    );
    org.junit.jupiter.api.Assertions.assertThrows(
      IllegalArgumentException.class,
      () -> new MintResult("pid", Instant.now(), "")
    );
  }
}
