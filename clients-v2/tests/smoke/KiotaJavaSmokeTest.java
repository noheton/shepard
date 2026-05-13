/*
 * Smoke test for the Kiota-generated Java client (CG1a).
 *
 * Verifies that:
 *   - the generated source tree compiles (this happens implicitly when
 *     `mvn -pl java-kiota compile` succeeds — CI gates on that),
 *   - the top-level ShepardV2Client class is discoverable via reflection,
 *   - the class exposes a single-arg constructor taking a Kiota
 *     RequestAdapter (the contract every Kiota Java generation honours).
 *
 * NO live HTTP call. Run from clients-v2/java-kiota via:
 *   mvn -pl java-kiota -Dtest=KiotaJavaSmokeTest test
 *
 * (The CI workflow .github/workflows/clients-kiota.yml runs this from
 * the smoke-test step after `mvn compile` succeeds.)
 */
package de.dlr.shepard.v2.clients;

import java.lang.reflect.Constructor;

public final class KiotaJavaSmokeTest {

    private KiotaJavaSmokeTest() {}

    public static void main(String[] args) throws Exception {
        Class<?> client = Class.forName("de.dlr.shepard.v2.clients.ShepardV2Client");
        if (client == null) {
            throw new AssertionError("ShepardV2Client not loadable");
        }

        Constructor<?>[] ctors = client.getDeclaredConstructors();
        if (ctors.length == 0) {
            throw new AssertionError(
                    "ShepardV2Client has no declared constructors — generation likely incomplete");
        }

        // Every Kiota Java client exposes a (RequestAdapter) constructor.
        // We don't instantiate (no real adapter), but we assert the seam exists.
        boolean foundAdapterCtor = false;
        for (Constructor<?> ctor : ctors) {
            Class<?>[] params = ctor.getParameterTypes();
            if (params.length >= 1
                    && params[0].getName().equals("com.microsoft.kiota.RequestAdapter")) {
                foundAdapterCtor = true;
                break;
            }
        }
        if (!foundAdapterCtor) {
            StringBuilder sb = new StringBuilder(
                    "Expected ShepardV2Client to declare a constructor taking "
                            + "com.microsoft.kiota.RequestAdapter; declared signatures:\n");
            for (Constructor<?> ctor : ctors) {
                sb.append("  - ");
                Class<?>[] params = ctor.getParameterTypes();
                for (int i = 0; i < params.length; i++) {
                    if (i > 0) sb.append(", ");
                    sb.append(params[i].getName());
                }
                sb.append('\n');
            }
            throw new AssertionError(sb.toString());
        }

        System.out.println(
                "OK — ShepardV2Client constructor seam verified ("
                        + ctors.length
                        + " constructor(s) declared)");
    }
}
