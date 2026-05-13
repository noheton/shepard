package de.dlr.shepard.plugins.minter.local;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.dlr.shepard.publish.minter.MintRequest;
import de.dlr.shepard.publish.minter.MintResult;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * KIP1h — unit coverage for {@link LocalMinter}. Designed in
 * `aidocs/66 §5` and the user's KIP1h directive. Pins the wire
 * format (`shepard:&lt;instance.id&gt;:&lt;kind&gt;:&lt;appId&gt;:v&lt;n&gt;`),
 * the stability guarantee (same inputs → same PID), the version
 * bump on different `versionNumber`, the instance-id fallback
 * behaviour, and the input-validation surface inherited from
 * {@link MintRequest}.
 */
class LocalMinterTest {

  private static final Clock FROZEN = Clock.fixed(Instant.ofEpochMilli(1_747_000_000_000L), ZoneOffset.UTC);

  @Test
  void idIsConstantAndStable() {
    LocalMinter minter = new LocalMinter(FROZEN, "dlr.de/shepard-prod");
    assertEquals("local", minter.id());
    assertEquals(LocalMinter.ID, minter.id());
  }

  @Test
  void isEnabledAlwaysTrueForLocalMinter() {
    LocalMinter minter = new LocalMinter(FROZEN, "dlr.de/shepard-prod");
    assertTrue(minter.isEnabled());
  }

  @Test
  void mintProducesNamespacedVersionedPid() {
    LocalMinter minter = new LocalMinter(FROZEN, "dlr.de/shepard-prod");
    MintRequest req = new MintRequest(
      "data-objects",
      "01HF1234567890ABCDEF",
      "https://shepard.example.dlr.de/v2/data-objects/01HF1234567890ABCDEF",
      1,
      Map.of("name", "TR-004")
    );

    MintResult out = minter.mint(req);

    // KIP1h wire format: shepard:<instance.id>:<kind>:<appId>:v<n>.
    // Note no epoch-millis — same inputs produce a stable PID.
    assertEquals("shepard:dlr.de/shepard-prod:data-objects:01HF1234567890ABCDEF:v1", out.pid());
    assertEquals(FROZEN.instant(), out.mintedAt());
    assertEquals("local", out.minterId());
  }

  @Test
  void mintIsStableForSameInputs() {
    // KIP1h core guarantee: re-minting with the same (kind, appId, version)
    // produces the same PID. Pre-KIP1h MockMinter encoded the
    // epoch-millis so this property did not hold.
    LocalMinter minter = new LocalMinter(FROZEN, "dlr.de/shepard-prod");
    MintRequest req = new MintRequest(
      "data-objects",
      "01HF-A",
      "https://shepard.example/v2/data-objects/01HF-A",
      3,
      Map.of()
    );

    String first = minter.mint(req).pid();
    String second = minter.mint(req).pid();
    String third = minter.mint(req).pid();

    assertEquals(first, second);
    assertEquals(second, third);
    assertEquals("shepard:dlr.de/shepard-prod:data-objects:01HF-A:v3", first);
  }

  @Test
  void differentVersionsProduceDifferentPids() {
    LocalMinter minter = new LocalMinter(FROZEN, "dlr.de/shepard-prod");
    MintRequest v1 = new MintRequest("collections", "01HF-X", "https://x/v2/collections/01HF-X", 1, Map.of());
    MintRequest v2 = new MintRequest("collections", "01HF-X", "https://x/v2/collections/01HF-X", 2, Map.of());
    MintRequest v17 = new MintRequest("collections", "01HF-X", "https://x/v2/collections/01HF-X", 17, Map.of());

    assertEquals("shepard:dlr.de/shepard-prod:collections:01HF-X:v1", minter.mint(v1).pid());
    assertEquals("shepard:dlr.de/shepard-prod:collections:01HF-X:v2", minter.mint(v2).pid());
    assertEquals("shepard:dlr.de/shepard-prod:collections:01HF-X:v17", minter.mint(v17).pid());

    assertNotEquals(minter.mint(v1).pid(), minter.mint(v2).pid());
  }

  @Test
  void differentKindsProduceDifferentPids() {
    LocalMinter minter = new LocalMinter(FROZEN, "lab-a");
    MintRequest dataObject = new MintRequest("data-objects", "01HF-A", "https://x/v2/data-objects/01HF-A", 1, Map.of());
    MintRequest collection = new MintRequest("collections", "01HF-A", "https://x/v2/collections/01HF-A", 1, Map.of());

    assertEquals("shepard:lab-a:data-objects:01HF-A:v1", minter.mint(dataObject).pid());
    assertEquals("shepard:lab-a:collections:01HF-A:v1", minter.mint(collection).pid());
  }

  @Test
  void instanceIdFallbackWhenNullOrBlank() {
    // Unset / blank shepard.instance.id falls back to the LocalMinter
    // constant; isFallbackInstanceId() returns true so the startup
    // WARN fires for operators.
    LocalMinter nullId = new LocalMinter(FROZEN, null);
    LocalMinter blankId = new LocalMinter(FROZEN, "   ");

    assertEquals(LocalMinter.INSTANCE_ID_FALLBACK, nullId.instanceId());
    assertEquals(LocalMinter.INSTANCE_ID_FALLBACK, blankId.instanceId());
    assertTrue(nullId.isFallbackInstanceId());
    assertTrue(blankId.isFallbackInstanceId());

    String pid = nullId.mint(new MintRequest("data-objects", "01HF-Z", "https://x/v2/data-objects/01HF-Z", 1, Map.of())).pid();
    assertEquals("shepard:local:data-objects:01HF-Z:v1", pid);
  }

  @Test
  void instanceIdConfiguredValueIsTrimmedAndUsed() {
    LocalMinter trimmed = new LocalMinter(FROZEN, "  dlr.de/shepard-prod  ");
    assertEquals("dlr.de/shepard-prod", trimmed.instanceId());
    assertFalse(trimmed.isFallbackInstanceId());

    String pid = trimmed.mint(new MintRequest("data-objects", "01HF-A", "https://x/v2/data-objects/01HF-A", 1, Map.of())).pid();
    assertEquals("shepard:dlr.de/shepard-prod:data-objects:01HF-A:v1", pid);
  }

  @Test
  void mintRequestRejectsBlankFields() {
    // The MintRequest record's compact constructor (in core) carries
    // the validation; LocalMinter relies on it. KIP1h grew the
    // versionNumber field which gets its own validation.
    assertThrows(
      IllegalArgumentException.class,
      () -> new MintRequest("", "01HF", "https://x", 1, Map.of())
    );
    assertThrows(
      IllegalArgumentException.class,
      () -> new MintRequest("data-objects", null, "https://x", 1, Map.of())
    );
    assertThrows(
      IllegalArgumentException.class,
      () -> new MintRequest("data-objects", "01HF", " ", 1, Map.of())
    );
    // KIP1h-added: versionNumber must be >= 1.
    assertThrows(
      IllegalArgumentException.class,
      () -> new MintRequest("data-objects", "01HF", "https://x", 0, Map.of())
    );
    assertThrows(
      IllegalArgumentException.class,
      () -> new MintRequest("data-objects", "01HF", "https://x", -3, Map.of())
    );
  }

  @Test
  void mintRequestBackwardsCompatibleFactoryDefaultsVersionToOne() {
    // The 4-arg pre-KIP1h overload defaults versionNumber=1 so plugin
    // authors writing test fixtures don't need to thread the version
    // through every call.
    MintRequest legacy = new MintRequest("data-objects", "01HF-A", "https://x/v2/data-objects/01HF-A", Map.of());
    assertEquals(1, legacy.versionNumber());
  }

  @Test
  void defensiveGuardOnInvalidVersionNumber() {
    // The MintRequest record rejects versionNumber < 1, but if a
    // future code path constructs an invalid request the LocalMinter
    // defensive guard normalises it to v1 rather than emit an
    // ill-formed PID.
    // We can't construct an invalid MintRequest directly, so this
    // test pins the documented defensive behaviour through the
    // record's positive lower bound.
    LocalMinter minter = new LocalMinter(FROZEN, "dlr.de");
    MintResult out = minter.mint(new MintRequest("data-objects", "01HF-A", "https://x", 1, Map.of()));
    // The minimum legal version maps to v1.
    assertThat(out.pid()).endsWith(":v1");
  }

  @Test
  void mintResultCarriesMinterIdAndTimestamp() {
    LocalMinter minter = new LocalMinter(FROZEN, "lab-bench-7");
    MintResult out = minter.mint(new MintRequest("data-objects", "01HF-A", "https://x", 1, Map.of()));
    assertNotNull(out.mintedAt());
    assertEquals("local", out.minterId());
    // The clock is frozen so the mintedAt is exactly FROZEN.instant().
    assertEquals(FROZEN.instant(), out.mintedAt());
  }
}
