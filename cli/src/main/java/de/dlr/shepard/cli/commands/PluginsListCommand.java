package de.dlr.shepard.cli.commands;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import de.dlr.shepard.cli.AbstractCommand;
import de.dlr.shepard.cli.http.AdminCliException;
import de.dlr.shepard.cli.io.PluginInfo;
import de.dlr.shepard.cli.io.PluginList;
import de.dlr.shepard.cli.output.TableFormatter;
import picocli.CommandLine.Command;

/**
 * {@code shepard-admin plugins list} — PM1b + PM1c.
 *
 * <p>Calls {@code GET /v2/admin/plugins} and renders the registry's
 * insertion-ordered snapshot.
 *
 * <p>Human-mode columns (PM1c-expanded): ID, TITLE, VERSION, STATE,
 * ENABLED, LICENCE, REPOSITORY, SOURCE. TITLE / REPOSITORY get
 * width-capped (TITLE 30 chars, REPOSITORY 40 chars; both truncate
 * with {@code …}) so a wide screen reads cleanly without blowing
 * up the terminal. The source column collapses null (build
 * classpath) to {@code (build-classpath)} so the table stays
 * scannable.
 *
 * <p>JSON-mode output is unchanged — the richer
 * {@link PluginInfo} shape goes out verbatim. Operators scripting
 * against the CLI should pin {@code --output=json} for stability
 * (the human table can grow / reflow columns without notice).
 *
 * <p>Exit codes: 0 on success (read-only command); propagates
 * {@link AdminCliException} as exit 1 on HTTP / IO errors; 2 on
 * unexpected runtime exceptions (shared {@code AbstractCommand}
 * shape).
 */
@Command(
  name = "list",
  mixinStandardHelpOptions = true,
  description = "List every discovered plugin (id, title, version, state, enabled, licence, repository, source)."
)
public final class PluginsListCommand extends AbstractCommand {

  /** PM1c — width cap for the human-mode TITLE column. */
  static final int TITLE_MAX = 30;

  /** PM1c — width cap for the human-mode LICENCE column. */
  static final int LICENCE_MAX = 12;

  /** PM1c — width cap for the human-mode REPOSITORY column. */
  static final int REPOSITORY_MAX = 40;

  @Override
  protected Integer run() {
    PluginList list = buildClient().getJson(PluginsAdminPaths.LIST, new TypeReference<PluginList>() {});

    if (wantsJson()) {
      try {
        out().println(jsonMapper().writeValueAsString(list));
      } catch (JsonProcessingException e) {
        throw new AdminCliException("Could not serialise plugin list to JSON: " + e.getMessage(), e);
      }
      return 0;
    }

    TableFormatter table = new TableFormatter(
      "ID",
      "TITLE",
      "VERSION",
      "STATE",
      "ENABLED",
      "LICENCE",
      "REPOSITORY",
      "SOURCE"
    );
    for (PluginInfo p : list.getPlugins()) {
      table.addRow(
        safe(p.getId()),
        truncate(safe(p.getTitle()), TITLE_MAX),
        safe(p.getVersion()),
        safe(p.getState()),
        p.isEnabled() ? "true" : "false",
        truncate(blankToDash(p.getLicence()), LICENCE_MAX),
        truncate(blankToDash(p.getRepositoryUrl()), REPOSITORY_MAX),
        p.getSourcePath() == null ? "(build-classpath)" : p.getSourcePath()
      );
    }
    out().print(table.render());
    return 0;
  }

  private static String safe(String s) {
    return s == null || s.isBlank() ? "-" : s;
  }

  private static String blankToDash(String s) {
    return s == null || s.isBlank() ? "-" : s;
  }

  /**
   * Truncate {@code value} to {@code max} display chars, appending
   * {@code …} (single character) when truncation happens. Idempotent
   * on shorter values — the table stays aligned regardless. Marked
   * package-private so the test asserts behaviour directly.
   */
  static String truncate(String value, int max) {
    if (value == null) {
      return "-";
    }
    if (value.length() <= max) {
      return value;
    }
    if (max <= 1) {
      return "…";
    }
    return value.substring(0, max - 1) + "…";
  }
}
