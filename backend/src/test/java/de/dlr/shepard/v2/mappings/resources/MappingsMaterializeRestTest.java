package de.dlr.shepard.v2.mappings.resources;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.dlr.shepard.provenance.services.ProvenanceService;
import de.dlr.shepard.spi.transform.NoOpTransformExecutor;
import de.dlr.shepard.spi.transform.TransformException;
import de.dlr.shepard.spi.transform.TransformExecutor;
import de.dlr.shepard.spi.transform.TransformExecutorRegistry;
import de.dlr.shepard.spi.transform.TransformRequest;
import de.dlr.shepard.spi.transform.TransformResult;
import de.dlr.shepard.template.daos.ShepardTemplateDAO;
import de.dlr.shepard.template.entities.ShepardTemplate;
import de.dlr.shepard.common.exceptions.ProblemJson;
import de.dlr.shepard.v2.mappings.io.MaterializeRequestIO;
import de.dlr.shepard.v2.mappings.io.MaterializeResponseIO;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MappingsMaterializeRestTest {

  MappingsMaterializeRest rest;
  ShepardTemplateDAO templateDAO;
  ProvenanceService provenanceService;
  TransformExecutorRegistry registry;
  SecurityContext securityContext;
  ContainerRequestContext requestContext;

  private static final String IRI = NoOpTransformExecutor.IDENTITY_SHAPE_IRI;

  @BeforeEach
  void setUp() {
    rest = new MappingsMaterializeRest();
    templateDAO = mock(ShepardTemplateDAO.class);
    provenanceService = mock(ProvenanceService.class);
    rest.templateDAO = templateDAO;
    rest.provenanceService = provenanceService;

    securityContext = mock(SecurityContext.class);
    Principal principal = mock(Principal.class);
    lenient().when(principal.getName()).thenReturn("alice");
    lenient().when(securityContext.getUserPrincipal()).thenReturn(principal);

    requestContext = mock(ContainerRequestContext.class);

    // Default: the built-in NoOp identity executor.
    registry = new TransformExecutorRegistry(List.<TransformExecutor>of(new NoOpTransformExecutor()));
    registry.discover();
    rest.executorRegistry = registry;
  }

  private void wireRegistry(TransformExecutor... executors) {
    registry = new TransformExecutorRegistry(List.of(executors));
    registry.discover();
    rest.executorRegistry = registry;
  }

  private static ShepardTemplate mappingTemplate(String appId, String name, String bodyJson) {
    ShepardTemplate t = new ShepardTemplate(name, "MAPPING_RECIPE", bodyJson);
    t.setAppId(appId);
    return t;
  }

  // ─── input validation ───────────────────────────────────────────────────

  @Test
  void returns400OnBlankTemplateAppId() {
    Response r = rest.materialize("  ", null, securityContext, requestContext);
    assertThat(r.getStatus()).isEqualTo(400);
  }

  @Test
  void returns404WhenTemplateNotFound() {
    when(templateDAO.findByAppId("missing")).thenReturn(Optional.empty());
    Response r = rest.materialize("missing", null, securityContext, requestContext);
    assertThat(r.getStatus()).isEqualTo(404);
  }

  @Test
  void returns422WhenNotMappingRecipeKind() {
    ShepardTemplate t = new ShepardTemplate("View", "VIEW_RECIPE", "{\"renderer\":\"tresjs\"}");
    t.setAppId("v1");
    when(templateDAO.findByAppId("v1")).thenReturn(Optional.of(t));
    Response r = rest.materialize("v1", null, securityContext, requestContext);
    assertThat(r.getStatus()).isEqualTo(422);
  }

  @Test
  void returns422WhenBodyDeclaresNoShapeIri() {
    ShepardTemplate t = mappingTemplate("m1", "No shape", "{\"inputs\":{}}");
    when(templateDAO.findByAppId("m1")).thenReturn(Optional.of(t));
    Response r = rest.materialize("m1", new MaterializeRequestIO(Map.of()), securityContext, requestContext);
    assertThat(r.getStatus()).isEqualTo(422);
  }

  @Test
  void returns404WhenNoExecutorRegisteredForShape() {
    String body = "{\"mappingRecipeShape\":\"http://example.com/Unregistered\"}";
    ShepardTemplate t = mappingTemplate("m2", "Orphan", body);
    when(templateDAO.findByAppId("m2")).thenReturn(Optional.of(t));
    Response r = rest.materialize("m2", new MaterializeRequestIO(Map.of("a", "ref-1")), securityContext, requestContext);
    assertThat(r.getStatus()).isEqualTo(404);
    var entity = (ProblemJson) r.getEntity();
    assertThat(entity.type()).isEqualTo("/problems/transform.executor.not-registered");
  }

  // ─── happy path with the built-in identity executor ───────────────────────

  @Test
  void identityExecutorEchoesFirstInputAsDerivedReference() {
    String body = "{\"mappingRecipeShape\":\"" + IRI + "\"}";
    ShepardTemplate t = mappingTemplate("m3", "Identity recipe", body);
    when(templateDAO.findByAppId("m3")).thenReturn(Optional.of(t));

    Response r = rest.materialize(
      "m3",
      new MaterializeRequestIO(Map.of("srcFileAppId", "ref-appid-99")),
      securityContext,
      requestContext
    );

    assertThat(r.getStatus()).isEqualTo(200);
    var io = (MaterializeResponseIO) r.getEntity();
    assertThat(io.templateAppId()).isEqualTo("m3");
    assertThat(io.outputKind()).isEqualTo("REFERENCE");
    assertThat(io.derivedReferenceAppId()).isEqualTo("ref-appid-99");
    assertThat(io.executor()).isEqualTo("NoOpTransformExecutor");
  }

  @Test
  void recordsProvenanceAndSetsSkipCaptureOnSuccess() {
    String body = "{\"mappingRecipeShape\":\"" + IRI + "\"}";
    ShepardTemplate t = mappingTemplate("m4", "Identity", body);
    when(templateDAO.findByAppId("m4")).thenReturn(Optional.of(t));

    rest.materialize("m4", new MaterializeRequestIO(Map.of("a", "ref-1")), securityContext, requestContext);

    org.mockito.Mockito.verify(provenanceService).record(
      org.mockito.ArgumentMatchers.eq("EXECUTE"),
      anyString(), any(), org.mockito.ArgumentMatchers.eq("alice"),
      anyString(), anyString(), anyString(), anyInt(), anyLong(), anyLong()
    );
    org.mockito.Mockito.verify(requestContext)
      .setProperty(org.mockito.ArgumentMatchers.eq("shepard.provenance.skip-capture"), org.mockito.ArgumentMatchers.eq(Boolean.TRUE));
  }

  @Test
  void provenanceFailureDoesNotBreakPrimaryOp() {
    String body = "{\"mappingRecipeShape\":\"" + IRI + "\"}";
    ShepardTemplate t = mappingTemplate("m5", "Identity", body);
    when(templateDAO.findByAppId("m5")).thenReturn(Optional.of(t));
    when(provenanceService.record(anyString(), any(), any(), any(), anyString(), anyString(), anyString(), anyInt(), anyLong(), anyLong()))
      .thenThrow(new RuntimeException("neo4j down"));

    Response r = rest.materialize("m5", new MaterializeRequestIO(Map.of("a", "ref-1")), securityContext, requestContext);
    assertThat(r.getStatus()).isEqualTo(200);
  }

  // ─── executor failure mapping ─────────────────────────────────────────────

  @Test
  void transformExceptionInputMissingMapsTo422() {
    // The NoOp executor throws transform.input.missing when no inputs supplied.
    String body = "{\"mappingRecipeShape\":\"" + IRI + "\"}";
    ShepardTemplate t = mappingTemplate("m6", "Identity", body);
    when(templateDAO.findByAppId("m6")).thenReturn(Optional.of(t));

    Response r = rest.materialize("m6", new MaterializeRequestIO(Map.of()), securityContext, requestContext);
    assertThat(r.getStatus()).isEqualTo(422);
    var entity = (ProblemJson) r.getEntity();
    assertThat(entity.type()).isEqualTo("/problems/transform.input.missing");
  }

  @Test
  void transformExceptionInputNotFoundMapsTo404() {
    String iri = "http://example.com/NotFoundy";
    String body = "{\"mappingRecipeShape\":\"" + iri + "\"}";
    ShepardTemplate t = mappingTemplate("m7", "NotFoundy", body);
    when(templateDAO.findByAppId("m7")).thenReturn(Optional.of(t));
    wireRegistry(throwingExecutor(iri, new TransformException("transform.input.not-found", "ref gone")));

    Response r = rest.materialize("m7", new MaterializeRequestIO(Map.of("a", "ref-1")), securityContext, requestContext);
    assertThat(r.getStatus()).isEqualTo(404);
  }

  @Test
  void unexpectedRuntimeExceptionMapsTo500() {
    String iri = "http://example.com/Broken";
    String body = "{\"mappingRecipeShape\":\"" + iri + "\"}";
    ShepardTemplate t = mappingTemplate("m8", "Broken", body);
    when(templateDAO.findByAppId("m8")).thenReturn(Optional.of(t));
    wireRegistry(brokenExecutor(iri));

    Response r = rest.materialize("m8", new MaterializeRequestIO(Map.of("a", "ref-1")), securityContext, requestContext);
    assertThat(r.getStatus()).isEqualTo(500);
    var entity = (ProblemJson) r.getEntity();
    assertThat(entity.type()).isEqualTo("/problems/transform.internal-error");
  }

  @Test
  void executorReturningNullMapsTo500() {
    String iri = "http://example.com/Null";
    String body = "{\"mappingRecipeShape\":\"" + iri + "\"}";
    ShepardTemplate t = mappingTemplate("m9", "Null", body);
    when(templateDAO.findByAppId("m9")).thenReturn(Optional.of(t));
    wireRegistry(nullExecutor(iri));

    Response r = rest.materialize("m9", new MaterializeRequestIO(Map.of("a", "ref-1")), securityContext, requestContext);
    assertThat(r.getStatus()).isEqualTo(500);
  }

  @Test
  void viewResultIsCarriedThroughToWire() {
    String iri = "http://example.com/Viewy";
    String body = "{\"mappingRecipeShape\":\"" + iri + "\"}";
    ShepardTemplate t = mappingTemplate("m10", "Viewy", body);
    when(templateDAO.findByAppId("m10")).thenReturn(Optional.of(t));
    wireRegistry(viewExecutor(iri, Map.of("frames", 3)));

    Response r = rest.materialize("m10", new MaterializeRequestIO(Map.of("a", "ref-1")), securityContext, requestContext);
    assertThat(r.getStatus()).isEqualTo(200);
    var io = (MaterializeResponseIO) r.getEntity();
    assertThat(io.outputKind()).isEqualTo("VIEW");
    assertThat(io.viewModel()).containsEntry("frames", 3);
    assertThat(io.derivedReferenceAppId()).isNull();
  }

  @Test
  void nullRegistryDegradesTo404() {
    rest.executorRegistry = null;
    String body = "{\"mappingRecipeShape\":\"" + IRI + "\"}";
    ShepardTemplate t = mappingTemplate("m11", "Identity", body);
    when(templateDAO.findByAppId("m11")).thenReturn(Optional.of(t));
    Response r = rest.materialize("m11", new MaterializeRequestIO(Map.of("a", "ref-1")), securityContext, requestContext);
    assertThat(r.getStatus()).isEqualTo(404);
  }

  // ─── fixtures ─────────────────────────────────────────────────────────────

  private static TransformExecutor throwingExecutor(String iri, TransformException ex) {
    return new TransformExecutor() {
      @Override
      public Set<String> supportedShapeIris() {
        return Set.of(iri);
      }

      @Override
      public TransformResult materialize(TransformRequest req) {
        throw ex;
      }
    };
  }

  private static TransformExecutor brokenExecutor(String iri) {
    return new TransformExecutor() {
      @Override
      public Set<String> supportedShapeIris() {
        return Set.of(iri);
      }

      @Override
      public TransformResult materialize(TransformRequest req) {
        throw new IllegalStateException("synthetic boom");
      }
    };
  }

  private static TransformExecutor nullExecutor(String iri) {
    return new TransformExecutor() {
      @Override
      public Set<String> supportedShapeIris() {
        return Set.of(iri);
      }

      @Override
      public TransformResult materialize(TransformRequest req) {
        return null;
      }
    };
  }

  private static TransformExecutor viewExecutor(String iri, Map<String, Object> vm) {
    return new TransformExecutor() {
      @Override
      public Set<String> supportedShapeIris() {
        return Set.of(iri);
      }

      @Override
      public TransformResult materialize(TransformRequest req) {
        return TransformResult.view(vm, "ViewExecutor");
      }
    };
  }
}
