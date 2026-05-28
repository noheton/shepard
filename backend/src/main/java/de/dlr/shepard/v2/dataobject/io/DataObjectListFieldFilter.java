package de.dlr.shepard.v2.dataobject.io;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.ser.impl.SimpleBeanPropertyFilter;
import com.fasterxml.jackson.databind.ser.impl.SimpleFilterProvider;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * DB-OPT5 — per-request Jackson field filter for the v2 DataObject list
 * endpoint. Knows three filter modes:
 *
 * <ol>
 *   <li><b>explicit {@code ?fields=foo,bar}</b> — only the named fields
 *       are emitted (plus {@code id} / {@code appId}, which are always
 *       included as resource identity).</li>
 *   <li><b>default-trim</b> (no query param) — drops fields that the
 *       collection-detail UI never reads: {@code description},
 *       {@code attributes}, and the three deprecated {@code int} count
 *       siblings of the v2 {@code long} counts. Saves ~50% on the
 *       median MFFD-Dropbox DataObject list response.</li>
 *   <li><b>{@code ?include=full}</b> — opt-back-in to the full (pre-DB-OPT5)
 *       wire shape, mainly for callers that still depend on the dropped
 *       fields. Acts as a transitional safety valve until a future
 *       breaking-version bump can drop the deprecated ints unconditionally.</li>
 * </ol>
 *
 * <p>Field-name validation is performed by reflection over the {@link
 * DataObjectListItemV2IO} class hierarchy so the allow-list stays in
 * lock-step with the IO shape — there is no hand-maintained list to
 * forget about.
 *
 * <p>The filter does <em>not</em> support dotted-path nested selection
 * (e.g. {@code attributes.bench}). The list endpoint already drops the
 * heavy nested maps by default; deep selection is a follow-up if a real
 * caller need surfaces. Tracked as DB-OPT5-NESTED in {@code aidocs/16}.
 *
 * <p>Cite: GitHub REST API {@code fields=} flat-CSV convention
 * (<a href="https://docs.github.com/en/rest/overview/api-versions">REST API
 * versioning, "Sparse fieldsets"</a>). Jackson reference:
 * <a href="https://github.com/FasterXML/jackson-annotations/blob/master/src/main/java/com/fasterxml/jackson/annotation/JsonFilter.java">{@code @JsonFilter}</a>
 * + {@code SimpleBeanPropertyFilter} (per-request {@code ObjectMapper}
 * copy avoids global mutation; the {@code ObjectMapper.copy()} call
 * shares the underlying caches so the cost is small).
 */
public final class DataObjectListFieldFilter {

  /**
   * Fields removed from the default ({@code ?fields=} absent,
   * {@code ?include=full} absent) wire shape. The frontend list-row
   * template does not render any of these — they're available via
   * {@code GET /v2/collections/{a}/data-objects/{b}} (detail endpoint)
   * or by opting into the full shape with {@code ?include=full}.
   *
   * <ul>
   *   <li>{@code description} — heavy CommonMark string, never shown in the list.</li>
   *   <li>{@code attributes} — heavy key-value map, never shown in the list.</li>
   *   <li>{@code timeseriesReferenceCount} — deprecated {@code int} sibling
   *       of {@code timeseriesCount} (v2 {@code long}). The replacement counts
   *       non-deleted only and is overflow-safe.</li>
   *   <li>{@code fileBundleCount} — deprecated {@code int} sibling of
   *       {@code fileCount} (v2 {@code long}; covers both bundle + singleton
   *       file refs).</li>
   *   <li>{@code structuredDataReferenceCount} — deprecated {@code int}
   *       sibling of {@code structuredDataCount} (v2 {@code long}).</li>
   * </ul>
   *
   * <p>{@code videoStreamReferenceCount} is NOT in this list because no
   * v2 long-typed replacement has shipped yet — dropping it would lose
   * data with no client-visible replacement.
   */
  public static final Set<String> DEFAULT_TRIM_FIELDS = Collections.unmodifiableSet(
    new LinkedHashSet<>(Arrays.asList(
      "description",
      "attributes",
      "timeseriesReferenceCount",
      "fileBundleCount",
      "structuredDataReferenceCount"
    ))
  );

  /**
   * Always-included fields that the resource identity contract requires.
   * When a caller passes {@code ?fields=}, these are silently added to
   * the requested set — without them, the response is unusable
   * (no way to follow up with {@code /v2/.../data-objects/{appId}}).
   */
  public static final Set<String> ALWAYS_INCLUDED_FIELDS = Collections.unmodifiableSet(
    new LinkedHashSet<>(Arrays.asList("id", "appId", "name"))
  );

  private DataObjectListFieldFilter() {
    // utility
  }

  /**
   * Reflects over the {@link DataObjectListItemV2IO} class hierarchy and
   * returns every declared instance field name (ignoring statics and
   * synthetic fields). Used for {@code ?fields=} parameter validation.
   *
   * <p>Reflection is run once per call (cheap) rather than cached
   * because the IO shape is stable at startup; if this surfaces in a
   * profile, memoise in a {@code static final} initialised via
   * {@code knownFields()} once.
   */
  public static Set<String> knownFields() {
    Set<String> names = new LinkedHashSet<>();
    Class<?> c = DataObjectListItemV2IO.class;
    while (c != null && c != Object.class) {
      for (Field f : c.getDeclaredFields()) {
        if (f.isSynthetic()) continue;
        if (java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
        names.add(f.getName());
      }
      c = c.getSuperclass();
    }
    return Collections.unmodifiableSet(names);
  }

  /**
   * Builds the per-request {@link ObjectWriter} with the right
   * Jackson filter wired. Returns null if the caller passed an
   * unknown field name; the resource then surfaces a 400.
   *
   * @param objectMapper the request-scoped {@link ObjectMapper} to copy
   * @param fieldsParam  the {@code ?fields=} value (CSV); may be null/blank
   * @param includeFull  true when {@code ?include=full} was also passed;
   *                     defeats the default-trim, full shape on the wire
   * @return a configured {@link ObjectWriter}, or null if {@code fieldsParam}
   *         contained an unknown field (caller gets the offending name
   *         via {@link #firstUnknownField(String)})
   */
  public static ObjectWriter writerFor(ObjectMapper objectMapper, String fieldsParam, boolean includeFull) {
    SimpleBeanPropertyFilter filter;
    if (fieldsParam != null && !fieldsParam.isBlank()) {
      Set<String> requested = parseFields(fieldsParam);
      Set<String> known = knownFields();
      for (String f : requested) {
        if (!known.contains(f)) return null;
      }
      Set<String> effective = new LinkedHashSet<>(ALWAYS_INCLUDED_FIELDS);
      effective.addAll(requested);
      filter = SimpleBeanPropertyFilter.filterOutAllExcept(effective);
    } else if (includeFull) {
      filter = SimpleBeanPropertyFilter.serializeAll();
    } else {
      filter = SimpleBeanPropertyFilter.serializeAllExcept(DEFAULT_TRIM_FIELDS);
    }
    SimpleFilterProvider provider = new SimpleFilterProvider()
      .addFilter(DataObjectListItemV2IO.FILTER_ID, filter)
      .setFailOnUnknownId(false);
    return objectMapper.copy().setFilterProvider(provider).writer();
  }

  /**
   * Finds the first unknown field name in the supplied {@code ?fields=}
   * CSV, for use in the 400 response body. Returns null when every
   * supplied field is valid.
   */
  public static String firstUnknownField(String fieldsParam) {
    if (fieldsParam == null || fieldsParam.isBlank()) return null;
    Set<String> known = knownFields();
    for (String f : parseFields(fieldsParam)) {
      if (!known.contains(f)) return f;
    }
    return null;
  }

  /**
   * Parses a CSV field list into a normalised {@link LinkedHashSet}.
   * Whitespace around entries is trimmed; empty entries are dropped;
   * order is preserved so debug output matches the caller's input.
   */
  public static Set<String> parseFields(String fieldsParam) {
    Set<String> out = new LinkedHashSet<>();
    if (fieldsParam == null) return out;
    for (String raw : fieldsParam.split(",")) {
      String f = raw.trim();
      if (!f.isEmpty()) out.add(f);
    }
    return out;
  }
}
