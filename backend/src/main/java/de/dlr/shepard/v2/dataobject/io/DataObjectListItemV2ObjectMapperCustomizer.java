package de.dlr.shepard.v2.dataobject.io;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ser.impl.SimpleBeanPropertyFilter;
import com.fasterxml.jackson.databind.ser.impl.SimpleFilterProvider;
import io.quarkus.jackson.ObjectMapperCustomizer;
import jakarta.inject.Singleton;

/**
 * DB-OPT5 — registers a safe default for the {@code "doListV2"} Jackson filter
 * id on the application's global {@link ObjectMapper}.
 *
 * <p>{@link DataObjectListItemV2IO} carries
 * {@code @JsonFilter(DataObjectListItemV2IO.FILTER_ID)}. The list endpoint
 * always uses a per-request {@link ObjectMapper#copy() copy} configured with a
 * concrete {@link SimpleFilterProvider}, so this customizer is never the path
 * that actually filters fields.
 *
 * <p>But Jackson's default behaviour when a class carries {@code @JsonFilter}
 * and the active mapper has <em>no</em> {@link SimpleFilterProvider} (or one
 * that has not registered the filter id) is to throw
 * {@code JsonMappingException: Cannot resolve PropertyFilter with id 'doListV2'}
 * on serialisation. If any future caller serialises a
 * {@link DataObjectListItemV2IO} through the injected global mapper (e.g. an
 * MCP tool, a future REST endpoint, an audit dump), they would hit this
 * exception without an obvious diagnostic.
 *
 * <p>The customizer pre-registers a serialise-all default with
 * {@code failOnUnknownId=false}, so:
 * <ul>
 *   <li>the per-request path in {@code DataObjectV2Rest.list()} keeps its
 *       precise filter behaviour (it sets its own provider on its own mapper
 *       copy), and</li>
 *   <li>any other caller using the global mapper sees the full IO shape,
 *       not an exception.</li>
 * </ul>
 *
 * <p>The cost is a single static field-provider object per global mapper —
 * negligible.
 */
@Singleton
public class DataObjectListItemV2ObjectMapperCustomizer implements ObjectMapperCustomizer {

  @Override
  public void customize(ObjectMapper objectMapper) {
    objectMapper.setFilterProvider(
      new SimpleFilterProvider()
        .addFilter(DataObjectListItemV2IO.FILTER_ID, SimpleBeanPropertyFilter.serializeAll())
        .setFailOnUnknownId(false)
    );
  }
}
