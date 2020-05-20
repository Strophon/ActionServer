package com.github.strophon.init;

import com.github.strophon.web.ActionVerticle;
import com.google.gson.Gson;
import io.vertx.core.Verticle;
import io.vertx.core.Vertx;
import io.vertx.ext.auth.ecdsa.EcdsaUser;
import io.vertx.ext.auth.ecdsa.EcdsaUserData;
import io.vertx.ext.auth.ecdsa.EcdsaUserRetriever;
import com.github.strophon.action.ActionType;
import com.github.strophon.action.input.ActionInput;
import com.github.strophon.action.output.Event;
import com.github.strophon.cache.CacheAccessObject;
import com.github.strophon.data.DataIO;
import com.github.strophon.email.EmailSender;
import com.github.strophon.web.EventVerticle;
import com.github.strophon.web.PostLoginHandler;

import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

public interface Instantiator extends EcdsaUserRetriever {
    Gson GSON = new Gson();

    Supplier<Verticle> getServerVerticleSupplier();

    default Supplier<Verticle> getActionVerticleSupplier() {
        return () -> new ActionVerticle(this);
    }

    default Supplier<Verticle> getEventVerticleSupplier() {
        return () -> new EventVerticle(this);
    }

    CacheAccessObject getCache();

    EmailSender createEmailSender();

    PostLoginHandler createPostLoginHandler(Vertx vertx);

    List<ActionType> getActionTypes();

    Function<String, ActionType> getTypeRetriever();

    ActionInput createInputFromString(String in);

    DataIO getDataIoWithCache();

    DataIO getDataIoWithoutCache();

    Event[] createEventArrayFromString(String events);

    EcdsaUser getAuthorizedUser(EcdsaUserData user, String challenge);

    default EcdsaUserData getUserData(int userId) {
        try(DataIO dio = getDataIoWithoutCache()) {
            return dio.getUser(userId, false);
        }
    }

    /**
     * Specifies the number of bytes to use for a token. Default is 20, for a
     * 32-character, 160-bit token.
     * @return number of bytes in a token
     */
    default int getTokenSize() {
        return 20;
    }
}
