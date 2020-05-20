package com.github.strophon.auth;


import io.vertx.core.shareddata.impl.ClusterSerializable;
import io.vertx.ext.auth.ecdsa.EcdsaUser;
import io.vertx.ext.auth.ecdsa.EcdsaUserData;

public abstract class AuthorizedUser extends EcdsaUser implements ClusterSerializable {
	public AuthorizedUser(EcdsaUserData user, String challenge) {
		super(user, challenge);
	}

	public EcdsaUserData getUser() {
		return user;
	}

	public String getChallenge() {
		return challenge;
	}

	protected abstract EcdsaUserData userFromJson(String userJson);
}