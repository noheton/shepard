package de.dlr.shepard.spi.fileparser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.dlr.shepard.context.references.file.daos.SingletonFileReferenceDAO;
import de.dlr.shepard.context.references.file.entities.FileReference;
import de.dlr.shepard.context.semantic.daos.SemanticAnnotationDAO;
import de.dlr.shepard.context.semantic.entities.SemanticAnnotation;
import de.dlr.shepard.context.version.daos.VersionableEntityConcreteDAO;
import jakarta.enterprise.inject.Instance;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link FileParserRegistry}.
 *
 * <p>All tests use a recording {@link FileParserPlugin.AnnotationWriter} so no
 * database round-trip is needed. DAOs are mocked at the package boundary so that
 * annotation flush logic (write → reload → attach → save) can be verified in
 * isolation.
 */
class FileParserRegistryTest {

  // ── helpers ────────────────────────────────────────────────────────────────

  /** A simple recording annotation writer for assertions. */
  record RecordedAnnotation(String subject, String predicate, String value) {}

  /** Minimal parser that always accepts a given extension and emits one annotation. */
  static class FixedParser implements FileParserPlugin {

    private final String extension;
    private final String predicate;
    private final String value;
    private int parseCalls = 0;
    private boolean throwOnParse = false;

    FixedParser(String extension, String predicate, String value) {
      this.extension = extension;
      this.predicate = predicate;
      this.value = value;
    }

    FixedParser throwing() {
      throwOnParse = true;
      return this;
    }

    @Override
    public boolean accepts(String mimeType, String filename) {
      return filename != null && filename.toLowerCase().endsWith(extension);
    }

    @Override
    public int parse(FileParserPlugin.ParseContext ctx) {
      parseCalls++;
      if (throwOnParse) {
        throw new RuntimeException("simulated parse failure from " + getClass().getSimpleName());
      }
      String subject = ctx.fileReferenceAppId().orElse("unknown");
      ctx.annotations().write(subject, predicate, value);
      return 1;
    }

    int getParseCalls() {
      return parseCalls;
    }
  }

  @SuppressWarnings("unchecked")
  private Instance<FileParserPlugin> singletonInstance(FileParserPlugin... plugins) {
    Instance<FileParserPlugin> inst = mock(Instance.class);
    List<FileParserPlugin> list = List.of(plugins);
    when(inst.iterator()).thenAnswer(inv -> list.iterator());
    return inst;
  }

  private FileParserRegistry buildRegistry(
    Instance<FileParserPlugin> parsers,
    SemanticAnnotationDAO annotDao,
    VersionableEntityConcreteDAO entityDao,
    SingletonFileReferenceDAO refDao
  ) {
    FileParserRegistry reg = new FileParserRegistry();
    injectField(reg, "parsers", parsers);
    injectField(reg, "semanticAnnotationDAO", annotDao);
    injectField(reg, "versionableEntityConcreteDAO", entityDao);
    injectField(reg, "singletonFileReferenceDAO", refDao);
    return reg;
  }

  private void injectField(Object target, String fieldName, Object value) {
    try {
      java.lang.reflect.Field f = FileParserRegistry.class.getDeclaredField(fieldName);
      f.setAccessible(true);
      f.set(target, value);
    } catch (ReflectiveOperationException e) {
      throw new RuntimeException(e);
    }
  }

  // ── mocked DAOs ─────────────────────────────────────────────────────────────

  private SemanticAnnotationDAO annotDao;
  private VersionableEntityConcreteDAO entityDao;
  private SingletonFileReferenceDAO refDao;
  private SemanticAnnotation savedAnnotation;
  private FileReference mockRef;

  @BeforeEach
  void setUp() {
    annotDao = mock(SemanticAnnotationDAO.class);
    entityDao = mock(VersionableEntityConcreteDAO.class);
    refDao = mock(SingletonFileReferenceDAO.class);

    savedAnnotation = new SemanticAnnotation();
    savedAnnotation.setPropertyIRI("urn:shepard:test:predicate");
    savedAnnotation.setValueName("test-value");

    mockRef = new FileReference();
    mockRef.setAppId("test-ref-appid");

    when(annotDao.createOrUpdate(any())).thenReturn(savedAnnotation);
    when(entityDao.createOrUpdate(any())).thenReturn(null);
    when(refDao.findByAppId(anyString())).thenReturn(mockRef);
  }

  // ── tests ───────────────────────────────────────────────────────────────────

  @Test
  void anyAccepts_returnsFalse_whenNoParsersInstalled() {
    @SuppressWarnings("unchecked")
    Instance<FileParserPlugin> empty = mock(Instance.class);
    when(empty.iterator()).thenReturn(List.<FileParserPlugin>of().iterator());

    FileParserRegistry reg = buildRegistry(empty, annotDao, entityDao, refDao);

    assertThat(reg.anyAccepts(null, "robot.rdk")).isFalse();
  }

  @Test
  void anyAccepts_returnsTrue_whenOneParserAccepts() {
    FixedParser parser = new FixedParser(".rdk", "urn:shepard:rdk:appVersion", "11.0");
    FileParserRegistry reg = buildRegistry(
      singletonInstance(parser), annotDao, entityDao, refDao
    );

    assertThat(reg.anyAccepts(null, "station.rdk")).isTrue();
    assertThat(reg.anyAccepts(null, "document.pdf")).isFalse();
  }

  @Test
  void runAll_callsMatchingParsers() {
    FixedParser rdkParser = new FixedParser(".rdk", "urn:shepard:rdk:appVersion", "11.0");
    FixedParser stepParser = new FixedParser(".step", "urn:shepard:cad:step:schema", "AP242");

    FileParserRegistry reg = buildRegistry(
      singletonInstance(rdkParser, stepParser), annotDao, entityDao, refDao
    );

    reg.runAll(new byte[]{1, 2, 3}, "robot.rdk", "test-ref-appid", "parent-do-appid");

    assertThat(rdkParser.getParseCalls()).isEqualTo(1);
    assertThat(stepParser.getParseCalls()).isEqualTo(0);
  }

  @Test
  void runAll_callsAllMatchingParsers_whenMultipleMatch() {
    FixedParser p1 = new FixedParser(".xyz", "urn:test:pred1", "v1");
    FixedParser p2 = new FixedParser(".xyz", "urn:test:pred2", "v2");

    FileParserRegistry reg = buildRegistry(
      singletonInstance(p1, p2), annotDao, entityDao, refDao
    );

    reg.runAll(new byte[]{1}, "model.xyz", "ref-appid", null);

    assertThat(p1.getParseCalls()).isEqualTo(1);
    assertThat(p2.getParseCalls()).isEqualTo(1);
  }

  @Test
  void runAll_fireAndForget_exceptionInParserDoesNotPropagate() {
    FixedParser throwingParser = new FixedParser(".rdk", "urn:shepard:rdk:appVersion", "11.0").throwing();

    FileParserRegistry reg = buildRegistry(
      singletonInstance(throwingParser), annotDao, entityDao, refDao
    );

    // Must not throw
    assertThatNoException()
      .isThrownBy(() -> reg.runAll(new byte[]{1}, "station.rdk", "ref-appid", "do-appid"));
  }

  @Test
  void runAll_continuesWithNextParser_afterOneThrows() {
    FixedParser throwingParser = new FixedParser(".rdk", "urn:shepard:rdk:appVersion", "11.0").throwing();
    FixedParser goodParser = new FixedParser(".rdk", "urn:shepard:rdk:platform", "robodk");

    FileParserRegistry reg = buildRegistry(
      singletonInstance(throwingParser, goodParser), annotDao, entityDao, refDao
    );

    assertThatNoException()
      .isThrownBy(() -> reg.runAll(new byte[]{1}, "station.rdk", "ref-appid", "do-appid"));

    assertThat(goodParser.getParseCalls()).isEqualTo(1);
  }

  @Test
  void runAll_doesNothing_whenBytesAreNull() {
    FixedParser parser = new FixedParser(".rdk", "urn:shepard:rdk:appVersion", "11.0");

    FileParserRegistry reg = buildRegistry(
      singletonInstance(parser), annotDao, entityDao, refDao
    );

    assertThatNoException()
      .isThrownBy(() -> reg.runAll(null, "station.rdk", "ref-appid", "do-appid"));

    assertThat(parser.getParseCalls()).isEqualTo(0);
  }

  @Test
  void runAll_doesNothing_whenFilenameIsNull() {
    FixedParser parser = new FixedParser(".rdk", "urn:shepard:rdk:appVersion", "11.0");

    FileParserRegistry reg = buildRegistry(
      singletonInstance(parser), annotDao, entityDao, refDao
    );

    assertThatNoException()
      .isThrownBy(() -> reg.runAll(new byte[]{1}, null, "ref-appid", "do-appid"));

    assertThat(parser.getParseCalls()).isEqualTo(0);
  }

  @Test
  void runAll_parsesAndWritesAnnotations_whenParserEmits() {
    FixedParser parser = new FixedParser(".rdk", "urn:shepard:rdk:appVersion", "11.0.1");

    FileParserRegistry reg = buildRegistry(
      singletonInstance(parser), annotDao, entityDao, refDao
    );

    reg.runAll(new byte[]{1}, "station.rdk", "ref-appid-001", "do-appid-001");

    // Verify the annotation was persisted via the DAO
    org.mockito.Mockito.verify(annotDao).createOrUpdate(any(SemanticAnnotation.class));
    // Verify the FileReference was reloaded and re-saved
    org.mockito.Mockito.verify(refDao).findByAppId("ref-appid-001");
    org.mockito.Mockito.verify(entityDao).createOrUpdate(mockRef);
  }

  @Test
  void parseContextExposesCorrectValues() {
    List<RecordedAnnotation> written = new ArrayList<>();

    FixedParser captureParser = new FixedParser(".rdk", "", "") {
      @Override
      public int parse(FileParserPlugin.ParseContext ctx) {
        written.add(new RecordedAnnotation(
          ctx.fileReferenceAppId().orElse("none"),
          ctx.filename(),
          ctx.parentDataObjectAppId().orElse("none")
        ));
        return 0;
      }
    };

    FileParserRegistry reg = buildRegistry(
      singletonInstance(captureParser), annotDao, entityDao, refDao
    );

    reg.runAll(new byte[]{42}, "sensor.rdk", "ref-42", "do-99");

    assertThat(written).hasSize(1);
    assertThat(written.get(0).subject()).isEqualTo("ref-42");
    assertThat(written.get(0).predicate()).isEqualTo("sensor.rdk");
    assertThat(written.get(0).value()).isEqualTo("do-99");
  }
}
