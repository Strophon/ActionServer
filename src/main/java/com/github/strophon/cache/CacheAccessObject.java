package com.github.strophon.cache;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.ext.auth.ecdsa.EcdsaAuthCache;

import java.io.Closeable;
import java.util.Map;

public interface CacheAccessObject extends EcdsaAuthCache, Closeable {

    int getIpErrorThreshold();

    // hash
    void checkForIpBan(String ip, Handler<AsyncResult<Boolean>> handler);

    void logIpForPotentialBan(String ip, Handler<AsyncResult<Integer>> handler);

    // hash
    void setChallenge(int userId, String challenge, Handler<AsyncResult<String>> handler);

    void getChallenge(int userId, Handler<AsyncResult<String>> handler);

    void removeChallenge(int userId, Handler<AsyncResult<Void>> handler);

    // hash
    void setUserSessionId(int userId, String sessionId, Handler<AsyncResult<String>> handler);

    void getUserSessionId(int userId, Handler<AsyncResult<String>> handler);

    void removeUserSessionId(int userId, Handler<AsyncResult<Long>> handler);

    void getAllUserSessionIds(Handler<AsyncResult<Map<Integer, String>>> handler);

    // hash
    void addEmailToken(int userId, String token, Handler<AsyncResult<String>> handler);
    void getEmailToken(int userId, Handler<AsyncResult<String>> handler);
    void removeEmailToken(int userId, Handler<AsyncResult<Long>> handler);

    // string
    void pause(Handler<AsyncResult<Void>> handler);
    void isPaused(Handler<AsyncResult<Boolean>> handler);
    void resume(Handler<AsyncResult<Boolean>> handler);

    void close();
}