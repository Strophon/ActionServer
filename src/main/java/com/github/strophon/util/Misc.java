package com.github.strophon.util;

import io.vertx.ext.auth.ecdsa.EcdsaAuthProvider;

public class Misc {
    public static boolean secureEqualsIgnoreCase(String ours, String theirs) {
        // prevents information leakage due to more-equal values taking longer to compare
        ours = ours.toUpperCase();
        theirs = theirs.toUpperCase();

        return EcdsaAuthProvider.secureEquals(ours, theirs);
    }
}
