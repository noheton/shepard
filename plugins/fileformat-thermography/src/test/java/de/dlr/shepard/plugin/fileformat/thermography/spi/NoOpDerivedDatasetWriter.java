package de.dlr.shepard.plugin.fileformat.thermography.spi;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Test-only no-op implementation of {@link DerivedDatasetWriter} that
 * records every call into a thread-safe in-memory log. The pipeline tests
 * assert against {@link #calls()} to verify the writer was (or was not)
 * invoked and that the right arguments were passed.
 *
 * <p>Lives in {@code src/test} on purpose — the production plugin module
 * ships only the interface; the runtime supplies the real implementation.
 */
public final class NoOpDerivedDatasetWriter implements DerivedDatasetWriter {

    private final List<Call> calls = new CopyOnWriteArrayList<>();

    @Override
    public void writeOmeZarrDataset(
            String parentDataObjectAppId,
            String fileReferenceAppId,
            String storeUrl,
            Map<String, String> annotations) {
        calls.add(new Call(
                parentDataObjectAppId,
                fileReferenceAppId,
                storeUrl,
                new LinkedHashMap<>(annotations)));
    }

    /** @return immutable snapshot of recorded calls in invocation order. */
    public List<Call> calls() {
        return List.copyOf(calls);
    }

    /** A single recorded call. */
    public static final class Call {
        public final String parentDataObjectAppId;
        public final String fileReferenceAppId;
        public final String storeUrl;
        public final Map<String, String> annotations;

        Call(String parentDataObjectAppId,
             String fileReferenceAppId,
             String storeUrl,
             Map<String, String> annotations) {
            this.parentDataObjectAppId = parentDataObjectAppId;
            this.fileReferenceAppId = fileReferenceAppId;
            this.storeUrl = storeUrl;
            this.annotations = Map.copyOf(annotations);
        }
    }
}
