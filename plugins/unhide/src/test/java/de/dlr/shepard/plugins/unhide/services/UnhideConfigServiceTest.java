package de.dlr.shepard.plugins.unhide.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.plugins.unhide.daos.UnhideConfigDAO;
import de.dlr.shepard.plugins.unhide.entities.UnhideConfig;
import de.dlr.shepard.plugins.unhide.services.UnhideConfigService.MintResult;
import de.dlr.shepard.plugins.unhide.services.UnhideConfigService.ReadOnlyFieldException;
import de.dlr.shepard.plugins.unhide.services.UnhideConfigService.UnhidePatch;
import jakarta.enterprise.context.control.RequestContextController;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * UH1a — exercises the service-level invariants without booting
 * Quarkus or a Neo4j testcontainer (per N1a / N1b precedent — full
 * integration coverage lands later).
 *
 * <p>Coverage targets per the slice ticket:
 *
 * <ul>
 *   <li>seed-on-first-start is idempotent (no second row created)</li>
 *   <li>patch happy path + read-only-field rejection</li>
 *   <li>mint-then-verify-then-revoke end-to-end</li>
 *   <li>wrong-key verification fails (constant-time compare)</li>
 *   <li>fingerprint format / null-safety</li>
 *   <li>SHA-256 hashing matches the canonical Bouncy Castle / OpenSSL
 *       output for a known input</li>
 * </ul>
 */
class UnhideConfigServiceTest {

  /** Hex-string check — fingerprint is the first 8 hex chars of SHA-256. */
  private static final Pattern HEX8 = Pattern.compile("^[0-9a-f]{8}$");

  private UnhideConfigDAO dao;
  private UnhideConfigService service;

  @BeforeEach
  void setUp() {
    dao = mock(UnhideConfigDAO.class);
    // No-arg createOrUpdate returns its argument unmodified so we can
    // assert on the post-save shape without going through a real OGM
    // session.
    when(dao.createOrUpdate(any(UnhideConfig.class))).thenAnswer(inv -> inv.getArgument(0));

    service = new UnhideConfigService();
    service.dao = dao;
    service.installDefaultEnabled = false;
    service.installDefaultFeedPublic = false;
    service.installDefaultContactEmail = "";
    // Not used in any code-path the unit tests exercise — but the
    // service's StartupEvent path activates it, so a no-op stand-in
    // keeps the field non-null.
    service.requestContextController = mock(RequestContextController.class);
  }

  // ─── seed-on-first-start ─────────────────────────────────────────────────

  @Test
  void seedIfNeeded_createsSingletonWhenAbsent() {
    when(dao.findSingleton()).thenReturn(null);

    UnhideConfig seeded = service.seedIfNeeded();

    assertNotNull(seeded, "seedIfNeeded must return a non-null row");
    assertFalse(seeded.isEnabled(), "default enabled is false");
    assertFalse(seeded.isFeedPublic(), "default feedPublic is false");
    assertNull(seeded.getContactEmail(), "empty deploy default ⇒ null contactEmail");
    assertNull(seeded.getHarvestApiKeyHash(), "no key minted at seed time");
    assertNull(seeded.getHarvestApiKeyLastRotatedAt(), "no key minted at seed time");
    verify(dao).createOrUpdate(any(UnhideConfig.class));
  }

  @Test
  void seedIfNeeded_idempotent_returnsExistingWhenPresent() {
    UnhideConfig existing = new UnhideConfig();
    existing.setAppId("existing-appid");
    existing.setEnabled(true);
    when(dao.findSingleton()).thenReturn(existing);

    UnhideConfig result = service.seedIfNeeded();

    assertEquals("existing-appid", result.getAppId(), "existing row returned unchanged");
    assertTrue(result.isEnabled(), "existing enabled preserved");
    verify(dao, never()).createOrUpdate(any(UnhideConfig.class));
  }

  @Test
  void seedIfNeeded_honoursInstallDefaults_whenSeedingFresh() {
    when(dao.findSingleton()).thenReturn(null);
    service.installDefaultEnabled = true;
    service.installDefaultFeedPublic = true;
    service.installDefaultContactEmail = "ops@example.dlr.de";

    UnhideConfig seeded = service.seedIfNeeded();

    assertTrue(seeded.isEnabled(), "seeded from deploy-time default");
    assertTrue(seeded.isFeedPublic(), "seeded from deploy-time default");
    assertEquals("ops@example.dlr.de", seeded.getContactEmail());
  }

  @Test
  void seedIfNeeded_trimsAndNullifiesEmptyContactEmail() {
    when(dao.findSingleton()).thenReturn(null);
    service.installDefaultContactEmail = "   ";

    UnhideConfig seeded = service.seedIfNeeded();

    assertNull(seeded.getContactEmail(), "blank install default normalised to null");
  }

  // ─── patch ───────────────────────────────────────────────────────────────

  @Test
  void patch_setsEnabledAndFeedPublic_idempotent() {
    UnhideConfig existing = new UnhideConfig();
    when(dao.findSingleton()).thenReturn(existing);

    UnhidePatch patch = new UnhidePatch();
    patch.enabled = true;
    patch.feedPublic = true;

    UnhideConfig result = service.patch(patch);

    assertTrue(result.isEnabled());
    assertTrue(result.isFeedPublic());
    verify(dao).createOrUpdate(existing);
  }

  @Test
  void patch_absentFields_leaveExistingValues() {
    UnhideConfig existing = new UnhideConfig();
    existing.setEnabled(true);
    existing.setFeedPublic(true);
    existing.setContactEmail("alice@example.dlr.de");
    when(dao.findSingleton()).thenReturn(existing);

    UnhidePatch patch = new UnhidePatch();
    // All fields absent / not-touched.

    UnhideConfig result = service.patch(patch);

    assertTrue(result.isEnabled(), "absent enabled leaves field alone");
    assertTrue(result.isFeedPublic(), "absent feedPublic leaves field alone");
    assertEquals("alice@example.dlr.de", result.getContactEmail(), "absent contactEmail leaves field alone");
  }

  @Test
  void patch_explicitNullContactEmail_clearsField() {
    UnhideConfig existing = new UnhideConfig();
    existing.setContactEmail("alice@example.dlr.de");
    when(dao.findSingleton()).thenReturn(existing);

    UnhidePatch patch = new UnhidePatch();
    patch.contactEmailTouched = true;
    patch.contactEmail = null;

    UnhideConfig result = service.patch(patch);

    assertNull(result.getContactEmail(), "RFC 7396 null ⇒ clear");
  }

  @Test
  void patch_rejectsTouchedHarvestApiKeyHash() {
    when(dao.findSingleton()).thenReturn(new UnhideConfig());
    UnhidePatch patch = new UnhidePatch();
    patch.harvestApiKeyHashTouched = true;

    ReadOnlyFieldException thrown = assertThrows(ReadOnlyFieldException.class, () -> service.patch(patch));
    assertEquals("harvestApiKeyHash", thrown.field());
    verify(dao, never()).createOrUpdate(any(UnhideConfig.class));
  }

  @Test
  void patch_blankContactEmail_storedAsNull() {
    UnhideConfig existing = new UnhideConfig();
    when(dao.findSingleton()).thenReturn(existing);

    UnhidePatch patch = new UnhidePatch();
    patch.contactEmailTouched = true;
    patch.contactEmail = "   ";

    UnhideConfig result = service.patch(patch);

    assertNull(result.getContactEmail(), "whitespace-only contactEmail normalises to null");
  }

  // ─── rotate / verify / revoke ────────────────────────────────────────────

  @Test
  void rotateHarvestKey_mintsPlaintextOnce_storesHashOnly() {
    UnhideConfig existing = new UnhideConfig();
    when(dao.findSingleton()).thenReturn(existing);

    MintResult result = service.rotateHarvestKey();

    assertNotNull(result.plaintext(), "plaintext returned");
    // UUID v4 hyphenated shape: 8-4-4-4-12 hex chars.
    assertTrue(result.plaintext().matches("^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$"),
      "plaintext is UUID v4 shape: " + result.plaintext());
    assertNotNull(result.config().getHarvestApiKeyHash(), "hash stored on the config");
    assertEquals(64, result.config().getHarvestApiKeyHash().length(), "SHA-256 hex is 64 chars");
    assertNotEquals(result.plaintext(), result.config().getHarvestApiKeyHash(), "hash != plaintext");
    assertNotNull(result.config().getHarvestApiKeyLastRotatedAt(), "rotatedAt timestamp set");
  }

  @Test
  void verifyHarvestKey_acceptsPlaintextThatMatchesStoredHash() {
    UnhideConfig cfg = new UnhideConfig();
    when(dao.findSingleton()).thenReturn(cfg);
    MintResult minted = service.rotateHarvestKey();

    assertTrue(service.verifyHarvestKey(minted.plaintext()), "freshly-minted plaintext must verify");
  }

  @Test
  void verifyHarvestKey_rejectsWrongPlaintext() {
    UnhideConfig cfg = new UnhideConfig();
    when(dao.findSingleton()).thenReturn(cfg);
    service.rotateHarvestKey();

    assertFalse(service.verifyHarvestKey("00000000-0000-4000-8000-000000000000"));
    assertFalse(service.verifyHarvestKey(""), "blank plaintext rejected");
    assertFalse(service.verifyHarvestKey(null), "null plaintext rejected");
  }

  @Test
  void verifyHarvestKey_rejectsAnyPlaintextWhenHashAbsent() {
    UnhideConfig cfg = new UnhideConfig();
    when(dao.findSingleton()).thenReturn(cfg);
    // No mint — hash is null.

    assertFalse(service.verifyHarvestKey("00000000-0000-4000-8000-000000000000"));
  }

  @Test
  void revokeHarvestKey_clearsHash_bumpsTimestamp() {
    UnhideConfig cfg = new UnhideConfig();
    when(dao.findSingleton()).thenReturn(cfg);
    MintResult minted = service.rotateHarvestKey();
    assertNotNull(minted.config().getHarvestApiKeyHash());

    UnhideConfig revoked = service.revokeHarvestKey();

    assertNull(revoked.getHarvestApiKeyHash(), "hash cleared on revoke");
    assertNotNull(revoked.getHarvestApiKeyLastRotatedAt(), "rotatedAt bumped on revoke");
    assertFalse(service.verifyHarvestKey(minted.plaintext()), "post-revoke plaintext no longer verifies");
  }

  @Test
  void rotate_twice_changesPlaintext_andInvalidatesPriorPlaintext() {
    UnhideConfig cfg = new UnhideConfig();
    when(dao.findSingleton()).thenReturn(cfg);
    MintResult first = service.rotateHarvestKey();
    MintResult second = service.rotateHarvestKey();

    assertNotEquals(first.plaintext(), second.plaintext(), "two mints must produce different plaintexts");
    assertFalse(service.verifyHarvestKey(first.plaintext()), "first plaintext invalid after rotate");
    assertTrue(service.verifyHarvestKey(second.plaintext()), "second plaintext still valid");
  }

  // ─── fingerprint + SHA-256 ───────────────────────────────────────────────

  @Test
  void fingerprint_isFirst8HexChars() {
    String hash = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
    assertEquals("01234567", UnhideConfigService.fingerprint(hash));
  }

  @Test
  void fingerprint_nullSafe_andShortHashSafe() {
    assertNull(UnhideConfigService.fingerprint(null));
    assertNull(UnhideConfigService.fingerprint(""));
    assertNull(UnhideConfigService.fingerprint("abc"));
  }

  @Test
  void sha256Hex_matchesKnownVector() {
    // "abc" → ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad
    String hex = UnhideConfigService.sha256Hex("abc");
    assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", hex);
  }

  @Test
  void mint_fingerprintHasExpectedShape() {
    UnhideConfig cfg = new UnhideConfig();
    when(dao.findSingleton()).thenReturn(cfg);
    MintResult result = service.rotateHarvestKey();
    String fp = UnhideConfigService.fingerprint(result.config().getHarvestApiKeyHash());
    assertNotNull(fp);
    assertTrue(HEX8.matcher(fp).matches(), "fingerprint must be 8 lowercase hex: " + fp);
  }
}
