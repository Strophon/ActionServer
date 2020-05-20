package com.github.strophon.web;

import co.paralleluniverse.fibers.Suspendable;
import com.github.strophon.action.output.Event;
import com.github.strophon.cache.CacheAccessObject;
import com.github.strophon.data.DataIO;
import com.github.strophon.data.transfer.User;
import com.github.strophon.init.Instantiator;
import com.github.strophon.util.Misc;
import com.github.strophon.util.SyncUtil;
import com.google.gson.Gson;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.VertxException;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.sync.Sync;
import io.vertx.ext.web.RoutingContext;

import java.util.Collections;
import java.util.List;

public abstract class PostLoginHandler implements Handler<RoutingContext> {
    private static final Logger logger = LoggerFactory.getLogger(PostLoginHandler.class);
    protected static final Gson GSON = new Gson();
    protected Vertx vertx;
    protected Instantiator instantiator;
    protected EventBus bus;
    protected CacheAccessObject cache;

    public PostLoginHandler(Vertx vertx, Instantiator instantiator) {
        this.vertx = vertx;
        this.instantiator = instantiator;
        this.cache = instantiator.getCache();
        bus = vertx.eventBus();
        bus.consumer("login.confirm", Sync.fiberHandler(this::confirmLogin));
    }

    @Override
    @Suspendable
    public void handle(RoutingContext context) {
        if(context.user() != null) {
            User user = retrieveUser(context);
            String userSessionId = retrieveSessionId(context);

            String sessionId = SyncUtil.await(handler ->
                    cache.getUserSessionId(user.getId(), handler));

            if(sessionId != null && sessionId.equalsIgnoreCase(userSessionId)) {
                context.next();
            } else if(user.isEmailConfirmed()) {
                cleanUpCacheAfterLogin(user);
                SyncUtil.<String>await(handler ->
                        cache.setUserSessionId(user.getId(), userSessionId, handler));

                String clientIpAddress = context.request().getHeader("X-Forwarded-For");

                Event loginEvent = createLoginEvent(clientIpAddress, user);

                try(DataIO dio = instantiator.getDataIoWithCache()) {
                    SyncUtil.awaitBlocking(vertx, future -> {
                        dio.addEvents(Collections.singletonList(loginEvent));
                        dio.commitSql();
                        future.complete();
                    });
                }

                logger.info("User " + user.getId() + " logged in successfully from IP "
                        + clientIpAddress);

                context.next();
            } else {
                context.response().setStatusCode(400)
                       .end("You must confirm your email before you can log in.");
            }
        } else {
            context.fail(401);
        }
    }

    /**
     * Allows for the removal of any cache information used as part of the login process, if needed.
     * Use of <code>SyncUtil.await()</code> recommended to keep method from finishing until
     * removal confirmed.
     * IMPORTANT NOTE: It is *imperative* that this method be annotated <code>@Suspendable</code>
     * if any <code>SyncUtil</code> calls are made here!
     * @param user the <code>User</code> object representing the user that logged in
     */
    @Suspendable
    protected abstract void cleanUpCacheAfterLogin(User user);

    protected abstract User retrieveUser(RoutingContext context);

    protected abstract String retrieveSessionId(RoutingContext context);

    /**
     * Create the login event, which will be included in the events sent to the client.
     * @param clientIpAddress the client's IP address, in <code>String</code> form.
     * @param user the <code>User</code> object representing the user that's logging in
     * @return the created login event
     */
    protected abstract Event createLoginEvent(String clientIpAddress, User user);

    @Suspendable
    private void confirmLogin(Message<String> msg) {
        String[] parts = GSON.fromJson(msg.body(), String[].class);
        if(parts.length != 2) {
            return; // invalid message; ignore
        }
        int userId = Integer.parseInt(parts[0]);
        String challenge = parts[1];

        String sessionId = SyncUtil.await(handler ->
                cache.getUserSessionId(userId, handler));

        if(sessionId != null && Misc.secureEqualsIgnoreCase(sessionId, challenge)) {
            List<? extends Event> unseen;
            try(DataIO dio = instantiator.getDataIoWithCache()) {
                logger.info("User " + userId + " successfully confirmed login.");

                String data = retrieveUserData(dio, userId);

                // wait for client to confirm data was received before sending the unseen events
                SyncUtil.<Message<String>>await(handler ->
                        bus.send("client." + sessionId + ".data", data, handler));

                logger.info("User data sent to user " + userId);

                unseen = SyncUtil.awaitBlocking(vertx, future ->
                        future.complete(dio.getUnseenEventsSinceFirstUnseen(userId)));

                bus.send("server.events.login", GSON.toJson(unseen));
            } catch(VertxException e) {
                SyncUtil.handleVertxReplyException(cache, userId, sessionId, e, null);
            }
        }
    }

    /**
     * This method is used to retrieve a user's data (to send to them once they have logged in). All
     * blocking calls must use <code>SyncUtil.awaitBlocking()</code>.
     * IMPORTANT NOTE: It is *imperative* that this method be annotated <code>@Suspendable</code>
     * if any <code>SyncUtil</code> calls are made here!
     * @param dio the <code>DataIO</code> object which will be used to retrieve the user's data
     * @param userId the integer ID of the user in question
     * @return the retrieved user data, in <code>String</code> form (presumably JSON)
     * @throws VertxException if event-bus message to send user's data returns a no-such-handlers
     * error, or if the reply to confirm receipt times out
     */
    @Suspendable
    protected abstract String retrieveUserData(DataIO dio, int userId) throws VertxException;
}
