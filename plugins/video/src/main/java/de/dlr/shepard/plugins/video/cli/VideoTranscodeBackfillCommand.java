package de.dlr.shepard.plugins.video.cli;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import de.dlr.shepard.cli.AbstractCommand;
import de.dlr.shepard.cli.http.AdminCliException;
import de.dlr.shepard.cli.output.TableFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * VIDEO-HEVC-TRANSCODE-BACKFILL-2026-06-30 —
 * {@code shepard-admin video transcode-backfill}.
 *
 * <p>Re-submits pre-feature {@code :VideoStreamReference} rows (proxyStatus
 * NULL or FAILED) to the transcode orchestrator. The MFFD welding HEVC
 * uploads that predate PR-2a's on-upload pipeline are the primary target.
 *
 * <p>Flags inherited from {@link AbstractCommand}: {@code --url},
 * {@code --api-key}, {@code --output={human,json}}.
 */
@Command(
  name = "transcode-backfill",
  mixinStandardHelpOptions = true,
  description = "Re-submit existing video references for proxy transcode."
)
public final class VideoTranscodeBackfillCommand extends AbstractCommand {

  @Option(names = "--filter", description = "Filter expression (currently supports codec=<id>, e.g. codec=hevc).")
  private String filter;

  @Option(names = "--limit", description = "Max number of references to submit (0 = no cap).")
  private int limit = 0;

  @Option(names = "--dry-run", description = "Preview matches without submitting any jobs.")
  private boolean dryRun;

  @Override
  protected Integer run() {
    Map<String, Object> body = new LinkedHashMap<>();
    if (filter != null && !filter.isBlank()) {
      Map<String, Object> filterMap = parseFilter(filter);
      if (!filterMap.isEmpty()) body.put("filter", filterMap);
    }
    if (limit > 0) body.put("limit", limit);
    if (dryRun) body.put("dryRun", true);

    @SuppressWarnings("unchecked")
    Map<String, Object> result = buildClient().postJson(
      VideoAdminPaths.TRANSCODE_BACKFILL,
      body,
      new TypeReference<Map<String, Object>>() {}
    );

    if (wantsJson()) {
      try {
        out().println(jsonMapper().writeValueAsString(result));
      } catch (JsonProcessingException e) {
        throw new AdminCliException("Could not serialise backfill result to JSON: " + e.getMessage(), e);
      }
      return 0;
    }

    out().println("submitted: " + result.get("submitted")
      + ", skipped: " + result.get("skipped")
      + ", total: " + result.get("total")
      + (Boolean.TRUE.equals(result.get("dryRun")) ? " (dry-run)" : ""));
    Object jobsObj = result.get("jobs");
    if (jobsObj instanceof List<?> jobs && !jobs.isEmpty()) {
      TableFormatter table = new TableFormatter("APPID", "NAME", "CODEC", "PRIOR", "STATUS");
      for (Object jobObj : jobs) {
        if (jobObj instanceof Map<?, ?> job) {
          @SuppressWarnings("unchecked")
          Map<String, Object> j = (Map<String, Object>) job;
          table.addRow(
            String.valueOf(j.getOrDefault("appId", "")),
            String.valueOf(j.getOrDefault("name", "")),
            String.valueOf(j.getOrDefault("videoCodec", "")),
            String.valueOf(j.getOrDefault("priorProxyStatus", "")),
            String.valueOf(j.getOrDefault("status", ""))
          );
        }
      }
      out().print(table.render());
    }
    return 0;
  }

  /**
   * Parse a {@code key=value[,key=value]} filter string into a map.
   * Currently the backend only honours {@code codec}; future filters will
   * land additively on the same shape.
   */
  static Map<String, Object> parseFilter(String s) {
    Map<String, Object> out = new LinkedHashMap<>();
    for (String token : s.split(",")) {
      String t = token.trim();
      int eq = t.indexOf('=');
      if (eq <= 0) continue;
      String k = t.substring(0, eq).trim();
      String v = t.substring(eq + 1).trim();
      if (!k.isEmpty() && !v.isEmpty()) out.put(k, v);
    }
    return out;
  }
}
