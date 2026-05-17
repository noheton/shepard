package de.dlr.shepard.cli.commands;

import picocli.CommandLine.Command;

/**
 * ROR1 — container for {@code shepard-admin instance ror <verb>}
 * sub-commands.
 *
 * <ul>
 *   <li>{@code status} — read-only view of the current
 *       {@code :InstanceRorConfig} singleton via
 *       {@code GET /v2/admin/instance/ror}.</li>
 *   <li>{@code set} — update fields via
 *       {@code PATCH /v2/admin/instance/ror} (RFC 7396 merge-patch).</li>
 * </ul>
 *
 * <p>See {@code aidocs/16-dispatcher-backlog.md} ROR1 row and
 * {@code aidocs/22-admin-cli-draft.md} for the L1 design baseline.
 */
@Command(
  name = "ror",
  mixinStandardHelpOptions = true,
  description = "Manage the instance-level Research Organization Registry (ROR) configuration.",
  subcommands = { InstanceRorStatusCommand.class, InstanceRorSetCommand.class }
)
public final class InstanceRorCommand implements Runnable {

  @Override
  public void run() {
    // No-op: a user typing `shepard-admin instance ror` gets the
    // usage banner from Picocli's default no-subcommand behaviour.
  }
}
