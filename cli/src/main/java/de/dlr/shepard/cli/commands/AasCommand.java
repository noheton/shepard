package de.dlr.shepard.cli.commands;

import picocli.CommandLine.Command;

/**
 * {@code shepard-admin aas} — AAS administration subcommands (AAS1d+).
 *
 * <p>Currently ships one leaf command:
 * <ul>
 *   <li>{@code import-idta-templates} — idempotently upserts the three
 *       bundled IDTA Submodel Templates (AAS1d).</li>
 * </ul>
 */
@Command(
    name = "aas",
    mixinStandardHelpOptions = true,
    description = "AAS (Asset Administration Shell) administration commands.",
    subcommands = {AasImportIdtaTemplatesCommand.class})
public final class AasCommand implements Runnable {

  @Override
  public void run() {
    // Print usage when no subcommand is given — picocli handles this
    // automatically when the parent is the CommandLine root; the explicit
    // run() here satisfies the Runnable contract.
  }
}
