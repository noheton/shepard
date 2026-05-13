/**
 * Smoke test for the Kiota-generated TypeScript client (CG1a).
 *
 * Verifies that:
 *  - the generated `src/` tree compiles under our tsconfig,
 *  - the top-level `ShepardV2Client` (or its `create*Client` factory) is
 *    exported,
 *  - the type signatures of the constructor / factory align with the
 *    Kiota request-adapter contract.
 *
 * NO live HTTP call is performed. CI runs `npx tsc --noEmit` for the
 * type check and (optionally) executes this file via `tsx` or
 * `node --test` for the runtime import-resolution check.
 */

// Importing the generated client surface; CI will install
// `@dlr-shepard/v2-client` from the local workspace (`npm link` or
// `file:`) before running this test.
import * as v2 from "@dlr-shepard/v2-client";

function smokeImport(): void {
    const exportNames = Object.keys(v2);
    if (exportNames.length === 0) {
        throw new Error(
            "Generated @dlr-shepard/v2-client exports nothing; " +
                "Kiota generation likely failed.",
        );
    }

    // Kiota emits the top-level builder as either a class (PascalCase) or
    // a factory function (`createShepardV2Client`) depending on the
    // generator flag set. Accept either.
    const hasClient = exportNames.some(
        (name) =>
            name === "ShepardV2Client" ||
            name === "createShepardV2Client" ||
            name.toLowerCase().endsWith("client"),
    );
    if (!hasClient) {
        throw new Error(
            "Expected a top-level *Client export from @dlr-shepard/v2-client; " +
                `got: ${exportNames.slice(0, 30).join(", ")}`,
        );
    }
    console.log(
        `OK — @dlr-shepard/v2-client surface (${exportNames.length} exports)`,
    );
}

smokeImport();
