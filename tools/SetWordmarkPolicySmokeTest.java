package com.manavault.app;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public final class SetWordmarkPolicySmokeTest {
    private static final Set<String> OPTIONS = new HashSet<>(
        Arrays.asList("m10", "m11", "m14", "m19", "m20", "m21")
    );

    private static void assertSet(String observed, String expected) {
        String actual = SetWordmarkPolicy.read(observed, OPTIONS);
        if (!expected.equals(actual)) throw new AssertionError(
            "read(" + observed + "): expected [" + expected + "], got [" + actual + "]"
        );
    }

    public static void main(String[] args) {
        for (String set : OPTIONS) {
            if (!SetWordmarkPolicy.supported(set)) throw new AssertionError(set);
        }
        if (SetWordmarkPolicy.supported("m18")) throw new AssertionError("M18 unsupported");
        if (SetWordmarkPolicy.supported("ori")) throw new AssertionError("ORI unsupported");

        assertSet("M19", "m19");
        assertSet("M 19", "m19");
        assertSet("M-20", "m20");
        assertSet("M2O", "m20");
        assertSet("M2I", "m21");
        assertSet("M14", "m14");
        assertSet("M11", "m11");
        assertSet("M10", "m10");
        assertSet("M21", "m21");
        assertSet("M18", "");              // nonexistent M18 core set
        assertSet("ORI", "");              // no inferred non-lettered set
        assertSet("M19 M20", "");          // contradictory wordmarks
        assertSet("XM19", "");            // not a separate symbol
        assertSet("M2019", "");           // not a release-year guess
        assertSet("M15", "");             // set not in candidates
        assertSet("M22", "");             // unsupported
        if (!SetWordmarkPolicy.read("M19", new HashSet<String>()).isEmpty())
            throw new AssertionError("unexpected set without candidates");
        System.out.println("SetWordmarkPolicy smoke tests PASS");
    }
}
