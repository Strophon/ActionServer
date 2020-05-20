package com.github.strophon.web;

import co.paralleluniverse.fibers.Suspendable;
import com.github.strophon.action.Action;
import com.github.strophon.util.Misc;
import com.github.strophon.util.Randomizer;
import com.github.strophon.util.SyncUtil;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import io.vertx.core.Future;
import io.vertx.core.eventbus.Message;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.sync.Sync;
import io.vertx.ext.sync.SyncVerticle;
import com.github.strophon.action.ActionExecutor;
import com.github.strophon.action.ActionType;
import com.github.strophon.action.input.ActionInput;
import com.github.strophon.action.output.Result;
import com.github.strophon.cache.CacheAccessObject;
import com.github.strophon.data.DataIO;
import com.github.strophon.data.transfer.DataSource;
import com.github.strophon.init.Instantiator;

public class ActionVerticle extends SyncVerticle {
    private static final Gson GSON = new Gson();
    private static final Logger logger = LoggerFactory.getLogger(ActionVerticle.class);

    private Instantiator instantiator;
    private CacheAccessObject cache;
    private ActionExecutor executor;

    public ActionVerticle(Instantiator instantiator) {
        this.instantiator = instantiator;
    }

    @Override
    @Suspendable
    public void start(Future<Void> startFuture) {
        cache = instantiator.getCache();

        executor = new ActionExecutor(instantiator.getActionTypes(), instantiator.getTypeRetriever());

        vertx.eventBus().consumer("action", Sync.fiberHandler(this::handleAction));

        startFuture.complete();
    }

    @Suspendable
    private void handleAction(Message<String> msg) {
        String in = msg.body();
        ActionInput input;

        try {
            input = instantiator.createInputFromString(in);
        } catch(JsonSyntaxException e) { // malformed JSON input
            handleError("Malformed JSON", in, e, msg);
            return;
        }

        Action action;
        try {
            action = executor.getAction(input);
        } catch(Exception e) {
            handleError("Error during getAction() call", in, e, msg);
            return;
        }

        if(action instanceof ActionType.NonExistentAction) { // unrecognized action type
            handleError("Action type missing or invalid", in, null, msg);
            return;
        }

        String sessionId;

        try {
            if(!action.allowedWhilePaused() && SyncUtil.<Boolean>await(handler -> cache.isPaused(handler))) {
                msg.reply(
                        GSON.toJson(
                                new Result().setSuccess(false)
                                            .setError("Sorry, the system is currently paused.")));
                return;
            }

            sessionId = SyncUtil.await(handler ->
                    cache.getUserSessionId(input.getUserId(), handler));
        } catch(Exception e) {
            handleError("Error during cache access", in, e, msg);
            return;
        }

        if(sessionId == null) {
            Exception e = new RuntimeException(
                    "No session ID found for user ID "  + input.getUserId());

            handleError("No cached sessionId found", in, e, msg);
            return;
        }

        if(input.getSessionId() == null
                || !Misc.secureEqualsIgnoreCase(sessionId, input.getSessionId())) {
            String desc = (input.getSessionId() == null ? "Null" : "Invalid") + " sessionId";
            handleError(desc, in, null, msg);
            return;
        }

        try(DataIO dio = instantiator.getDataIoWithCache()) {
            byte[] seed = action.needsRandomNumbers() ?
                    Randomizer.getFreshTokenBytes(instantiator.getTokenSize()) : null;

            Result result = SyncUtil.awaitBlockingUnordered(vertx, future ->
                    future.complete( executor.execute(action, seed, new DataSource(dio)) ) );

            if(result == null) { // this shouldn't happen
                handleError("Null Result from Action", in, null, msg);
                action.logAction(true, true, logger::error);
                return;
            }

            action.logAction(logger.isDebugEnabled(), logger.isTraceEnabled(), logger::info);

            msg.reply( GSON.toJson( result ) );

            if(result.getSuccess() && !action.getEvents().isEmpty()) {
                vertx.eventBus().send("server.events", GSON.toJson(action.getEvents()));
            }
        } catch(Exception e) {
            handleError("Exception Encountered Post-Authentication", in, e, msg);
        }
    }

    private static final String GENERIC_ERROR =
            GSON.toJson( new Result().setSuccess(false)
                                     .setError("An error occurred while processing your request") );

    private void handleError(String desc, String in, Exception e, Message<String> msg) {
        String logMessage = desc + "; Input: " + in;

        if(e != null)
            logger.error(logMessage, e);
        else
            logger.info(logMessage);

        msg.reply(GENERIC_ERROR);
    }
}
