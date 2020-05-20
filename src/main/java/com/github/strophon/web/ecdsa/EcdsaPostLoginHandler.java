package com.github.strophon.web.ecdsa;

import co.paralleluniverse.fibers.Suspendable;
import com.github.strophon.auth.AuthorizedUser;
import com.github.strophon.data.transfer.User;
import com.github.strophon.init.Instantiator;
import com.github.strophon.util.SyncUtil;
import io.vertx.core.Vertx;
import io.vertx.ext.web.RoutingContext;
import com.github.strophon.web.PostLoginHandler;

public abstract class EcdsaPostLoginHandler extends PostLoginHandler {
    public EcdsaPostLoginHandler(Vertx vertx, Instantiator instantiator) {
        super(vertx, instantiator);
    }

    @Override
    @Suspendable
    protected void cleanUpCacheAfterLogin(User user) {
        SyncUtil.<Void>await(handler -> cache.removeChallenge(user.getId(), handler));
    }

    @Override
    protected User retrieveUser(RoutingContext context) {
        return (User) ((AuthorizedUser) context.user()).getUser();
    }

    @Override
    protected String retrieveSessionId(RoutingContext context) {
        return ((AuthorizedUser) context.user()).getChallenge();
    }
}
