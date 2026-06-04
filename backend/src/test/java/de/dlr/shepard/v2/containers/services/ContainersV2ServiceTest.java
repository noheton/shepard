package de.dlr.shepard.v2.containers.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.dlr.shepard.common.neo4j.entities.BasicContainer;
import de.dlr.shepard.v2.containers.io.ContainerV2IO;
import de.dlr.shepard.v2.containers.spi.ContainerKindHandler;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * V2CONV-A3 — dispatch + discovery tests for {@link ContainersV2Service} using
 * the test-only constructor seam (no CDI container). Mirrors
 * {@code ReferencesV2ServiceTest}.
 */
class ContainersV2ServiceTest {

  /** Minimal stub handler keyed on a kind + an entity it owns by identity. */
  private static class StubHandler implements ContainerKindHandler {

    final String kind;
    final BasicContainer owned;

    StubHandler(String kind, BasicContainer owned) {
      this.kind = kind;
      this.owned = owned;
    }

    @Override
    public String kind() {
      return kind;
    }

    @Override
    public boolean owns(BasicContainer container) {
      return container == owned;
    }

    @Override
    public BasicContainer findByAppId(String appId) {
      return (owned != null && appId.equals(owned.getAppId())) ? owned : null;
    }

    @Override
    public ContainerV2IO toIO(BasicContainer container) {
      return new ContainerV2IO(container, kind);
    }

    @Override
    public ContainerV2IO create(Map<String, Object> body) {
      ContainerV2IO io = new ContainerV2IO();
      io.setKind(kind);
      return io;
    }

    @Override
    public ContainerV2IO patch(String appId, Map<String, Object> patch) {
      return new ContainerV2IO(owned, kind);
    }

    @Override
    public void delete(String appId) {
      // no-op
    }

    @Override
    public List<ContainerV2IO> list(String nameFilter) {
      return List.of(new ContainerV2IO(owned, kind));
    }
  }

  private static BasicContainer container(String appId) {
    var c = new BasicContainer(1L);
    c.setAppId(appId);
    return c;
  }

  @Test
  void registeredKinds_listsAllHandlers() {
    var svc = new ContainersV2Service(
      List.of(new StubHandler("file", container("a")), new StubHandler("timeseries", container("b")))
    );
    assertTrue(svc.registeredKinds().containsAll(List.of("file", "timeseries")));
    assertEquals(2, svc.registeredKinds().size());
  }

  @Test
  void duplicateKind_failsFast() {
    var svc = new ContainersV2Service(
      List.of(new StubHandler("file", container("a")), new StubHandler("file", container("b")))
    );
    assertThrows(IllegalStateException.class, svc::registeredKinds);
  }

  @Test
  void handlerForKind_caseInsensitive() {
    var svc = new ContainersV2Service(List.of(new StubHandler("file", container("a"))));
    assertTrue(svc.handlerForKind("FILE").isPresent());
    assertFalse(svc.handlerForKind("hdf").isPresent());
  }

  @Test
  void resolveByAppId_findsOwningHandler() {
    var fileC = container("file-1");
    var tsC = container("ts-1");
    var svc = new ContainersV2Service(
      List.of(new StubHandler("file", fileC), new StubHandler("timeseries", tsC))
    );
    var resolved = svc.resolveByAppId("ts-1");
    assertTrue(resolved.isPresent());
    assertEquals("timeseries", resolved.get().handler().kind());
  }

  @Test
  void getByAppId_unknown_throwsNotFound() {
    var svc = new ContainersV2Service(List.of(new StubHandler("file", container("a"))));
    assertThrows(NotFoundException.class, () -> svc.getByAppId("missing"));
  }

  @Test
  void create_unknownKind_throwsBadRequest() {
    var svc = new ContainersV2Service(List.of(new StubHandler("file", container("a"))));
    assertThrows(BadRequestException.class, () -> svc.create("hdf", Map.of()));
  }

  @Test
  void list_dispatchesToKind() {
    var svc = new ContainersV2Service(List.of(new StubHandler("file", container("f"))));
    var out = svc.list("file", null);
    assertEquals(1, out.size());
    assertEquals("file", out.get(0).getKind());
  }

  @Test
  void patchByAppId_unknown_throwsNotFound() {
    var svc = new ContainersV2Service(List.of(new StubHandler("file", container("a"))));
    assertThrows(NotFoundException.class, () -> svc.patchByAppId("missing", Map.of()));
  }
}
