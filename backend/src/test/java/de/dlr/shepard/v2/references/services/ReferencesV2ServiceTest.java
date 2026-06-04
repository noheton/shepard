package de.dlr.shepard.v2.references.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.dlr.shepard.context.collection.entities.DataObject;
import de.dlr.shepard.context.references.basicreference.entities.BasicReference;
import de.dlr.shepard.v2.references.io.ReferenceV2IO;
import de.dlr.shepard.v2.references.spi.ReferenceKindHandler;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * V2CONV-A2 — dispatch + discovery tests for {@link ReferencesV2Service} using
 * the test-only constructor seam (no CDI container).
 */
class ReferencesV2ServiceTest {

  /** Minimal stub handler keyed on a kind + an entity it owns by identity. */
  private static class StubHandler implements ReferenceKindHandler {

    final String kind;
    final BasicReference owned;

    StubHandler(String kind, BasicReference owned) {
      this.kind = kind;
      this.owned = owned;
    }

    @Override
    public String kind() {
      return kind;
    }

    @Override
    public boolean owns(BasicReference reference) {
      return reference == owned;
    }

    @Override
    public BasicReference findByAppId(String appId) {
      return (owned != null && appId.equals(owned.getAppId())) ? owned : null;
    }

    @Override
    public ReferenceV2IO toIO(BasicReference reference) {
      return new ReferenceV2IO(reference, kind);
    }

    @Override
    public ReferenceV2IO create(String dataObjectAppId, Map<String, Object> body) {
      ReferenceV2IO io = new ReferenceV2IO();
      io.setKind(kind);
      return io;
    }

    @Override
    public ReferenceV2IO patch(String appId, Map<String, Object> patch) {
      return new ReferenceV2IO(owned, kind);
    }

    @Override
    public void delete(String appId) {
      // no-op
    }

    @Override
    public List<ReferenceV2IO> listByDataObject(String dataObjectAppId, String subKind) {
      return List.of(new ReferenceV2IO(owned, kind));
    }
  }

  private static BasicReference ref(String appId) {
    var parent = new DataObject(99L);
    parent.setAppId("do-" + appId);
    parent.setShepardId(99L);
    var r = new BasicReference(1L);
    r.setAppId(appId);
    r.setDataObject(parent);
    return r;
  }

  @Test
  void registeredKinds_listsAllHandlers() {
    var svc = new ReferencesV2Service(List.of(new StubHandler("uri", ref("a")), new StubHandler("file", ref("b"))));
    assertTrue(svc.registeredKinds().containsAll(List.of("uri", "file")));
    assertEquals(2, svc.registeredKinds().size());
  }

  @Test
  void duplicateKind_failsFast() {
    var svc = new ReferencesV2Service(List.of(new StubHandler("uri", ref("a")), new StubHandler("uri", ref("b"))));
    assertThrows(IllegalStateException.class, svc::registeredKinds);
  }

  @Test
  void handlerForKind_caseInsensitive() {
    var svc = new ReferencesV2Service(List.of(new StubHandler("uri", ref("a"))));
    assertTrue(svc.handlerForKind("URI").isPresent());
    assertFalse(svc.handlerForKind("video").isPresent());
  }

  @Test
  void resolveByAppId_findsOwningHandler() {
    var uriRef = ref("uri-1");
    var fileRef = ref("file-1");
    var svc = new ReferencesV2Service(List.of(new StubHandler("uri", uriRef), new StubHandler("file", fileRef)));
    var resolved = svc.resolveByAppId("file-1");
    assertTrue(resolved.isPresent());
    assertEquals("file", resolved.get().handler().kind());
  }

  @Test
  void getByAppId_unknown_throwsNotFound() {
    var svc = new ReferencesV2Service(List.of(new StubHandler("uri", ref("a"))));
    assertThrows(NotFoundException.class, () -> svc.getByAppId("missing"));
  }

  @Test
  void create_unknownKind_throwsBadRequest() {
    var svc = new ReferencesV2Service(List.of(new StubHandler("uri", ref("a"))));
    assertThrows(BadRequestException.class, () -> svc.create("video", "do-1", Map.of()));
  }

  @Test
  void listByDataObject_dispatchesToKind() {
    var svc = new ReferencesV2Service(List.of(new StubHandler("file", ref("f"))));
    var out = svc.listByDataObject("file", "do-1", "urdf");
    assertEquals(1, out.size());
    assertEquals("file", out.get(0).getKind());
  }
}
