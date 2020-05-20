package com.github.strophon.data.transfer;

import io.vertx.ext.auth.ecdsa.EcdsaUserData;

public interface User extends EcdsaUserData {
    int getId();
    void setId(int id);

    String getName();
    void setName(String name);

    String getSecretToken();
    void setSecretToken(String secretToken);

    default byte[] getPubkey() {
        return getAuthBlob();
    }
    default void setPubkey(byte[] pubkey) {
        setAuthBlob(pubkey);
    }

    byte[] getAuthBlob();
    void setAuthBlob(byte[] authBlob);

    String getEmail();
    void setEmail(String email);

    String getAuthorities();
    void setAuthorities(String roles);

    boolean isEmailConfirmed();
    void setEmailConfirmed(boolean emailConfirmed);

    String getEmailToken();
    void setEmailToken(String emailToken);

    String getRecoveryToken();
    void setRecoveryToken(String recoveryToken);
}
