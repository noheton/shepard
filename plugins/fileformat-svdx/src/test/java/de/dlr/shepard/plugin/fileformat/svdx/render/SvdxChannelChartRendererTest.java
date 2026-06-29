package de.dlr.shepard.plugin.fileformat.svdx.render;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.dlr.shepard.context.references.file.daos.SingletonFileReferenceDAO;
import de.dlr.shepard.context.references.file.entities.FileReference;
import de.dlr.shepard.context.semantic.daos.SemanticAnnotationDAO;
import de.dlr.shepard.context.semantic.entities.SemanticAnnotation;
import de.dlr.shepard.plugin.fileformat.svdx.SvdxAnnotations;
import de.dlr.shepard.spi.view.RenderException;
import de.dlr.shepard.spi.view.RenderRequest;
import de.dlr.shepard.spi.view.RenderResponse;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link SvdxChannelChartRenderer}.
 *
 * <p>The renderer's render path performs a {@link jakarta.enterprise.inject.spi.CDI}
 * lookup for {@link SemanticAnnotationDAO} + {@link SingletonFileReferenceDAO}; the
 * tests exercise the package-private
 * {@link SvdxChannelChartRenderer#renderWithDaos(RenderRequest, SemanticAnnotationDAO, SingletonFileReferenceDAO)}
 * entry to bypass the CDI container — matching the
 * {@link de.dlr.shepard.v2.svdx.transform.SvdxCsvTransformExecutor} test pattern.
 */
class SvdxChannelChartRendererTest {

  private final SvdxChannelChartRenderer renderer = new SvdxChannelChartRenderer();

  // ─────────────────────────────────────────────────────────── basic claims

  @Test
  void claimsTheSvdxChannelChartShapeIri() {
    assertThat(renderer.supportedShapeIris())
      .containsExactly(SvdxChannelChartRenderer.SVDX_CHANNEL_CHART_SHAPE_IRI);
  }

  @Test
  void nameIsStable() {
    assertThat(renderer.name()).isEqualTo("SvdxChannelChartRenderer");
  }

  @Test
  void rejectsBlankFocusShepardId() {
    var req = new RenderRequest(
      "tmpl-1", "  ", SvdxChannelChartRenderer.SVDX_CHANNEL_CHART_SHAPE_IRI, null);
    var annDao = mock(SemanticAnnotationDAO.class);
    var refDao = mock(SingletonFileReferenceDAO.class);

    assertThatThrownBy(() -> renderer.renderWithDaos(req, annDao, refDao))
      .isInstanceOf(RenderException.class)
      .hasMessageContaining("focusShepardId");
  }

  // ─────────────────────────────────────────────────────────── direct FileReference focus

  @Test
  void projectsChannelsForDirectFileReferenceFocus() {
    String fileRefAppId = "019ed586-901c-78aa-91fd-6294db19a881";
    var annDao = mock(SemanticAnnotationDAO.class);
    var refDao = mock(SingletonFileReferenceDAO.class);

    // Pass-1 focus probe: the FileReference resolves directly.
    FileReference asRef = new FileReference();
    asRef.setAppId(fileRefAppId);
    when(refDao.findByAppId(fileRefAppId)).thenReturn(asRef);
    when(annDao.findBySubjectAppId(fileRefAppId)).thenReturn(svdxAnnotations(fileRefAppId,
      List.of("ch_force", "ch_temp"),
      List.of("GVL_IO.rForce", "GVL_IO.rTemp"),
      List.of("REAL32", "REAL32"),
      List.of("1.2.3.4.1.1"),
      List.of("851"),
      "Scope Project Welding"));

    var req = new RenderRequest(
      "tmpl-svdx", fileRefAppId,
      SvdxChannelChartRenderer.SVDX_CHANNEL_CHART_SHAPE_IRI,
      "{\"viewRecipeShape\":\"" + SvdxChannelChartRenderer.SVDX_CHANNEL_CHART_SHAPE_IRI + "\"}");

    RenderResponse out = renderer.renderWithDaos(req, annDao, refDao);
    assertThat(out.renderer()).isEqualTo(SvdxChannelChartRenderer.RENDERER_HINT);
    assertThat(out.channelBindings()).hasSize(4); // 2 channels + 2 acquisitions

    // Every binding has status OK and resolves to the .svdx FileReference appId.
    out.channelBindings().forEach(b -> {
      assertThat(b.status()).isEqualTo("OK");
      assertThat(b.resolved()).isNotNull();
      assertThat(b.resolved().channelRef()).isEqualTo(fileRefAppId);
    });

    assertThat(out.channelBindings().stream().map(RenderResponse.ChannelBindingProjection::role))
      .containsExactly("channel-0", "channel-1", "acquisition-0", "acquisition-1");

    // The channel selector JSON carries channelName + dataType + AMS + port + manifest.
    assertThat(out.channelBindings().get(0).channelSelector())
      .contains("\"channelName\":\"ch_force\"")
      .contains("\"dataType\":\"REAL32\"")
      .contains("\"amsNetId\":\"1.2.3.4.1.1\"")
      .contains("\"port\":\"851\"")
      .contains("\"projectName\":\"Scope Project Welding\"");
  }

  // ─────────────────────────────────────────────────────────── DataObject focus → walk children

  @Test
  void walksDataObjectChildrenAndPicksSvdxFile() {
    String doAppId = "019ed455-67f7-7725-bf2d-7cd1b67aca9f";
    String svdxAppId = "019ed586-aaa-bbbb-cccc-ddddeeeeffff";
    var annDao = mock(SemanticAnnotationDAO.class);
    var refDao = mock(SingletonFileReferenceDAO.class);

    // Pass-1 fails (the focus is NOT a FileReference).
    when(refDao.findByAppId(doAppId)).thenReturn(null);

    // Pass-2: DataObject has two children — one .svdx, one .csv (a hypothetical
    // sibling that this renderer should ignore because it doesn't carry svdx:* annotations).
    FileReference svdx = new FileReference();
    svdx.setAppId(svdxAppId);
    svdx.setName("Scope Project_AutoSave_18_08_40.svdx");
    svdx.setFileKind("svdx");

    FileReference csv = new FileReference();
    csv.setAppId("019ed586-csv-csv-csv-csvcsvcsvcsv");
    csv.setName("notes.csv");
    csv.setFileKind("csv");

    when(refDao.findByDataObjectAppId(doAppId)).thenReturn(List.of(svdx, csv));
    when(annDao.findBySubjectAppId(svdxAppId)).thenReturn(svdxAnnotations(svdxAppId,
      List.of("ch_a", "ch_b", "ch_c"),
      List.of("Acq.A", "Acq.B"),
      List.of("REAL32", "INT16"),
      List.of("169.254.1.1.1.1"),
      List.of("851"),
      "WeldingProject"));

    var req = new RenderRequest("tmpl-svdx", doAppId,
      SvdxChannelChartRenderer.SVDX_CHANNEL_CHART_SHAPE_IRI, null);

    RenderResponse out = renderer.renderWithDaos(req, annDao, refDao);
    assertThat(out.channelBindings()).hasSize(5); // 3 channels + 2 acquisitions
    out.channelBindings().forEach(b -> {
      assertThat(b.status()).isEqualTo("OK");
      assertThat(b.resolved().channelRef()).isEqualTo(svdxAppId);
    });
  }

  // ─────────────────────────────────────────────────────────── empty annotations → MISSING

  @Test
  void surfacesMissingWhenFocusFileHasNoSvdxAnnotations() {
    String fileRefAppId = "019ed587-43d3-7876-a861-907a4318daa4"; // the 1/21 in seed
    var annDao = mock(SemanticAnnotationDAO.class);
    var refDao = mock(SingletonFileReferenceDAO.class);

    FileReference asRef = new FileReference();
    asRef.setAppId(fileRefAppId);
    when(refDao.findByAppId(fileRefAppId)).thenReturn(asRef);
    when(annDao.findBySubjectAppId(fileRefAppId)).thenReturn(List.of());

    var req = new RenderRequest("tmpl-svdx", fileRefAppId,
      SvdxChannelChartRenderer.SVDX_CHANNEL_CHART_SHAPE_IRI, null);

    RenderResponse out = renderer.renderWithDaos(req, annDao, refDao);
    assertThat(out.channelBindings()).hasSize(1);
    assertThat(out.channelBindings().get(0).status()).isEqualTo("MISSING");
    assertThat(out.channelBindings().get(0).role()).isEqualTo("svdx-unparsed");
  }

  // ─────────────────────────────────────────────────────────── no svdx child anywhere

  @Test
  void surfacesMissingWhenNoSvdxAnywhereOnTheDataObject() {
    String doAppId = "do-with-no-svdx";
    var annDao = mock(SemanticAnnotationDAO.class);
    var refDao = mock(SingletonFileReferenceDAO.class);
    when(refDao.findByAppId(doAppId)).thenReturn(null);
    when(refDao.findByDataObjectAppId(doAppId)).thenReturn(List.of());

    var req = new RenderRequest("tmpl-svdx", doAppId,
      SvdxChannelChartRenderer.SVDX_CHANNEL_CHART_SHAPE_IRI, null);
    RenderResponse out = renderer.renderWithDaos(req, annDao, refDao);
    assertThat(out.channelBindings()).hasSize(1);
    assertThat(out.channelBindings().get(0).status()).isEqualTo("MISSING");
    assertThat(out.channelBindings().get(0).role()).isEqualTo("svdx");
  }

  // ─────────────────────────────────────────────────────────── body knobs

  @Test
  void showAcquisitionsFalseSuppressesAcquisitionBindings() {
    String fileRefAppId = "019ed586-suppress-acq-test";
    var annDao = mock(SemanticAnnotationDAO.class);
    var refDao = mock(SingletonFileReferenceDAO.class);
    FileReference asRef = new FileReference();
    asRef.setAppId(fileRefAppId);
    when(refDao.findByAppId(fileRefAppId)).thenReturn(asRef);
    when(annDao.findBySubjectAppId(fileRefAppId)).thenReturn(svdxAnnotations(fileRefAppId,
      List.of("c1", "c2"),
      List.of("Sym.One", "Sym.Two", "Sym.Three"),
      List.of("REAL32"),
      List.of("1.1.1.1.1.1"),
      List.of("851"),
      "WeldingProject"));

    var req = new RenderRequest("tmpl-svdx", fileRefAppId,
      SvdxChannelChartRenderer.SVDX_CHANNEL_CHART_SHAPE_IRI,
      "{\"showAcquisitions\": false}");

    RenderResponse out = renderer.renderWithDaos(req, annDao, refDao);
    // 2 channels, 0 acquisitions (suppressed).
    assertThat(out.channelBindings()).hasSize(2);
    out.channelBindings().forEach(b -> assertThat(b.role()).startsWith("channel-"));
  }

  @Test
  void maxChannelsCapsTheProjection() {
    String fileRefAppId = "019ed586-cap-test";
    var annDao = mock(SemanticAnnotationDAO.class);
    var refDao = mock(SingletonFileReferenceDAO.class);
    FileReference asRef = new FileReference();
    asRef.setAppId(fileRefAppId);
    when(refDao.findByAppId(fileRefAppId)).thenReturn(asRef);

    // 5 channels + 5 acquisitions; cap to 3.
    when(annDao.findBySubjectAppId(fileRefAppId)).thenReturn(svdxAnnotations(fileRefAppId,
      List.of("c1", "c2", "c3", "c4", "c5"),
      List.of("S1", "S2", "S3", "S4", "S5"),
      List.of("REAL32"),
      List.of("1.1.1.1.1.1"),
      List.of("851"),
      "Capping"));

    var req = new RenderRequest("tmpl-svdx", fileRefAppId,
      SvdxChannelChartRenderer.SVDX_CHANNEL_CHART_SHAPE_IRI,
      "{\"maxChannels\": 3}");

    RenderResponse out = renderer.renderWithDaos(req, annDao, refDao);
    assertThat(out.channelBindings()).hasSize(3);
  }

  @Test
  void usesDefaultsWhenBodyIsBlankOrMalformed() {
    var knobs = renderer.parseBody(null);
    assertThat(knobs.maxChannels).isEqualTo(SvdxChannelChartRenderer.DEFAULT_MAX_CHANNELS);
    assertThat(knobs.showAcquisitions).isTrue();
    assertThat(knobs.groupBy).isEqualTo(SvdxChannelChartRenderer.DEFAULT_GROUP_BY);

    var knobs2 = renderer.parseBody("{ not json }");
    assertThat(knobs2.maxChannels).isEqualTo(SvdxChannelChartRenderer.DEFAULT_MAX_CHANNELS);
    assertThat(knobs2.showAcquisitions).isTrue();
  }

  // ─────────────────────────────────────────────────────────── DAO failure → fail-soft to MISSING

  @Test
  void failsSoftWhenSingletonDaoThrowsOnFocusProbe() {
    String focusAppId = "019ed586-dao-throws";
    var annDao = mock(SemanticAnnotationDAO.class);
    var refDao = mock(SingletonFileReferenceDAO.class);
    when(refDao.findByAppId(anyString())).thenThrow(new RuntimeException("dao down"));
    when(refDao.findByDataObjectAppId(anyString())).thenReturn(List.of());

    var req = new RenderRequest("tmpl-svdx", focusAppId,
      SvdxChannelChartRenderer.SVDX_CHANNEL_CHART_SHAPE_IRI, null);

    RenderResponse out = renderer.renderWithDaos(req, annDao, refDao);
    assertThat(out.channelBindings()).hasSize(1);
    assertThat(out.channelBindings().get(0).status()).isEqualTo("MISSING");
  }

  // ─────────────────────────────────────────────────────────── fixture builder

  /**
   * Build a list of SemanticAnnotations resembling what the SvdxManifestParser
   * emits. Mirrors the predicate set + cardinality observed in the seeded MFFD
   * welding files (CHANNEL_NAME many, SYMBOL_NAME many, DATA_TYPE many-dedup,
   * AMS_NET_ID many-dedup, PORT many-dedup, plus the file-level scalars).
   */
  private List<SemanticAnnotation> svdxAnnotations(
    String subjectAppId,
    List<String> channelNames,
    List<String> symbolNames,
    List<String> dataTypes,
    List<String> amsNetIds,
    List<String> ports,
    String projectName
  ) {
    List<SemanticAnnotation> out = new ArrayList<>();
    out.add(ann(subjectAppId, SvdxAnnotations.FORMAT_VERSION, "0x71960c00"));
    out.add(ann(subjectAppId, SvdxAnnotations.PROJECT_NAME, projectName));
    out.add(ann(subjectAppId, SvdxAnnotations.CHANNEL_COUNT,
      Integer.toString(channelNames.size())));
    out.add(ann(subjectAppId, SvdxAnnotations.ACQUISITION_COUNT,
      Integer.toString(symbolNames.size())));
    channelNames.forEach(n -> out.add(ann(subjectAppId, SvdxAnnotations.CHANNEL_NAME, n)));
    symbolNames.forEach(n -> out.add(ann(subjectAppId, SvdxAnnotations.SYMBOL_NAME, n)));
    dataTypes.forEach(n -> out.add(ann(subjectAppId, SvdxAnnotations.DATA_TYPE, n)));
    amsNetIds.forEach(n -> out.add(ann(subjectAppId, SvdxAnnotations.AMS_NET_ID, n)));
    ports.forEach(n -> out.add(ann(subjectAppId, SvdxAnnotations.PORT, n)));
    return out;
  }

  private static SemanticAnnotation ann(String subject, String iri, String value) {
    SemanticAnnotation a = new SemanticAnnotation();
    a.setSubjectAppId(subject);
    a.setSubjectKind("FileReference");
    a.setPropertyIRI(iri);
    a.setPropertyName(iri);
    a.setValueName(value);
    a.setSource("file-parser");
    return a;
  }
}
