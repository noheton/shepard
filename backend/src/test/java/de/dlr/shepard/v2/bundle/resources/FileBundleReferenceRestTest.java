package de.dlr.shepard.v2.bundle.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tombstone verification for {@link FileBundleReferenceRest}.
 *
 * <p>APISIMP-BUNDLE-REF-KIND-UNIFY slice 2: every method on this class now returns
 * 410 Gone. The original business-logic tests were removed when the implementation
 * was tombstoned; coverage is now provided by {@code BundleGroupsV2RestTest} (slice 1)
 * for the new canonical surface at {@code /v2/references/{bundleAppId}/groups/...}.
 */
class FileBundleReferenceRestTest {

  private static final String BUNDLE_APP_ID = "bundle-appid-1";
  private static final String GROUP_APP_ID  = "group-appid-1";

  FileBundleReferenceRest resource;

  @BeforeEach
  void setUp() {
    resource = new FileBundleReferenceRest();
  }

  @Test
  void getBundle_returns410Gone() {
    assertEquals(410, resource.getBundle(BUNDLE_APP_ID).getStatus());
  }

  @Test
  void listGroups_returns410Gone() {
    assertEquals(410, resource.listGroups(BUNDLE_APP_ID).getStatus());
  }

  @Test
  void createGroup_returns410Gone() {
    assertEquals(410, resource.createGroup(BUNDLE_APP_ID).getStatus());
  }

  @Test
  void getGroup_returns410Gone() {
    assertEquals(410, resource.getGroup(BUNDLE_APP_ID, GROUP_APP_ID).getStatus());
  }

  @Test
  void patchGroup_returns410Gone() {
    assertEquals(410, resource.patchGroup(BUNDLE_APP_ID, GROUP_APP_ID).getStatus());
  }

  @Test
  void deleteGroup_returns410Gone() {
    assertEquals(410, resource.deleteGroup(BUNDLE_APP_ID, GROUP_APP_ID).getStatus());
  }

  @Test
  void listGroupFiles_returns410Gone() {
    assertEquals(410, resource.listGroupFiles(BUNDLE_APP_ID, GROUP_APP_ID).getStatus());
  }

  @Test
  void uploadFileIntoGroup_returns410Gone() {
    assertEquals(410, resource.uploadFileIntoGroup(BUNDLE_APP_ID, GROUP_APP_ID).getStatus());
  }
}
