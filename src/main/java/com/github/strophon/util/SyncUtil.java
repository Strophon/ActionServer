package com.github.strophon.util;

import co.paralleluniverse.fibers.Fiber;
import co.paralleluniverse.fibers.FiberScheduler;
import co.paralleluniverse.fibers.Suspendable;
import com.github.strophon.cache.CacheAccessObject;
import com.github.strophon.web.EventVerticle;
import io.vertx.core.*;
import io.vertx.core.eventbus.ReplyException;
import io.vertx.core.eventbus.ReplyFailure;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.sync.Sync;

import java.util.function.Consumer;

public class SyncUtil {
    private static final Logger logger = LoggerFactory.getLogger(SyncUtil.class);

    @Suspendable
    public static <T> T await(Consumer<Handler<AsyncResult<T>>> consumer) {
        try {
            return Sync.awaitResult(consumer);
        } catch(VertxException e) {
            if(e.getCause() instanceof ReplyException) {
                ReplyFailure failureType = ((ReplyException) e.getCause()).failureType();

                if(failureType == ReplyFailure.NO_HANDLERS || failureType == ReplyFailure.TIMEOUT) {
                    throw e; // we should've already logged this error, so don't log it again
                }
            }

            logger.error("Error during Sync.awaitResult():", e.getCause());
            throw e;
        }
    }

    @Suspendable
    public static <T> T awaitBlocking(Vertx vertx, Handler<Future<T>> blockingHandler) {
        return await(resultHandler -> vertx.executeBlocking(blockingHandler, resultHandler));
    }

    @Suspendable
    public static <T> T awaitBlockingUnordered(Vertx vertx, Handler<Future<T>> blockingHandler) {
        return await(resultHandler -> vertx.executeBlocking(blockingHandler, false, resultHandler));
    }

    /**
     * Convert a standard consumer to a consumer which runs on a fiber. This is necessary if you want to do fiber blocking
     * synchronous operations in your consumer.
     *
     * @param consumer  the standard consumer
     * @param <T>  the event type of the consumer
     * @return  a wrapped consumer that runs the consumer on a fiber
     */
    @Suspendable
    public static <T> Consumer<T> fiberConsumer(Consumer<T> consumer) {
        FiberScheduler scheduler = Sync.getContextScheduler();
        return p -> new Fiber<Void>(scheduler, () -> consumer.accept(p)).start();
    }

    @Suspendable
    public static void handleVertxReplyException(CacheAccessObject cache, int userId,
                                                 String sessionId, VertxException e, Handler<String> retry) {
        if(e.getCause() instanceof ReplyException) {
            ReplyFailure failureType = ((ReplyException) e.getCause()).failureType();

            if(failureType == ReplyFailure.TIMEOUT || failureType == ReplyFailure.NO_HANDLERS) {
                // retry if this failure is due to changed session ID
                String retrievedSessionId = await(handler ->
                        cache.getUserSessionId(userId, handler));

                if(retrievedSessionId != null && !sessionId.equalsIgnoreCase(retrievedSessionId)) {
                    if(retry != null) {
                        retry.handle(retrievedSessionId);
                    }
                } else {
                    cache.removeUserSessionId(userId, res -> { });
                    logger.info("Unable to send message to user " + userId + " (" +
                            failureType.name() + "); closing session (id: " + sessionId + ")");
                }
            } else {
                throw e;
            }
        } else {
            throw e;
        }
    }
}
