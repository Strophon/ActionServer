package com.github.strophon.web;

import co.paralleluniverse.fibers.Suspendable;
import com.github.strophon.action.output.Event;
import com.github.strophon.cache.CacheAccessObject;
import com.github.strophon.data.DataIO;
import com.github.strophon.init.Instantiator;
import com.github.strophon.util.SyncUtil;
import com.google.gson.Gson;
import io.vertx.core.Future;
import io.vertx.core.VertxException;
import io.vertx.core.eventbus.Message;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.sync.Sync;
import io.vertx.ext.sync.SyncVerticle;

import java.util.*;

public class EventVerticle extends SyncVerticle {
    private static final Logger logger = LoggerFactory.getLogger(EventVerticle.class);
    private static final Gson GSON = new Gson();

    private Instantiator instantiator;
    private CacheAccessObject cache;

    public EventVerticle(Instantiator instantiator) {
        this.instantiator = instantiator;
    }

    @Override
    @Suspendable
    public void start(Future<Void> startFuture) {
        cache = instantiator.getCache();
        vertx.eventBus().consumer(
                "server.events.login", Sync.fiberHandler(this::acceptLoginEvents));
        vertx.eventBus().consumer("server.events", Sync.fiberHandler(this::acceptEvents));
        startFuture.complete();
    }

    @Suspendable
    private void acceptLoginEvents(Message<String> msg) {
        String stringArray = msg.body();

        Map<Integer, List<Event>> map = sortEvents(stringArray);

        if(map.size() > 2
                || (map.size() == 2 && !map.containsKey(null))) {
            throw new RuntimeException(
                    "Multiple users' events provided to acceptLoginEvents()");
        } else if(map.size() == 2) {
            map.remove(null); // null recipient ID means broadcast, but event was already broadcast
        }

        assert map.size() == 1;
        assert !map.containsKey(null);

        handleSingleUserEvents(map, stringArray);
    }

    @Suspendable
    private void acceptEvents(Message<String> msg) {
        String stringArray = msg.body();
        Map<Integer, List<Event>> map = sortEvents(stringArray);

        if(map.size() > 1) {
            // can't use map.forEach() due to quasar limitation
            for(Map.Entry<Integer, List<Event>> e : map.entrySet()) {
                // need to do all of these concurrently; they don't actually block, just await
                vertx.executeBlocking(Sync.fiberHandler(future ->
                        handleEvents(e.getKey(), e.getValue())), false, null);
            }
        } else if(map.size() == 1) {
            // only one user's events present, so we can avoid re-serializing the events list
            handleSingleUserEvents(map, stringArray);
        }
    }

    @Suspendable
    private void handleSingleUserEvents(Map<Integer, List<Event>> map, String stringArray) {
        Map.Entry<Integer, List<Event>> entry = map.entrySet().iterator().next();
        Integer id = entry.getKey();
        List<Event> list = entry.getValue();

        handleEvents(id, list, stringArray);
    }

    @Suspendable
    private void handleEvents(Integer id, List<Event> list) {
        handleEvents(id, list, GSON.toJson(list));
    }

    @Suspendable
    private void handleEvents(Integer id, List<Event> list, String stringList) {
        if(id == null) {
            Map<Integer, String> userSessionIds = SyncUtil.await(handler ->
                    cache.getAllUserSessionIds(handler));

            // can't use map.forEach() due to quasar limitation
            for(Map.Entry<Integer, String> entry : userSessionIds.entrySet()) {
                Integer userId = entry.getKey();
                String sessionId = entry.getValue();
                sendEvents(userId, sessionId, stringList, list);
            }
        } else {
            String sessionId = SyncUtil.await(handler ->
                    cache.getUserSessionId(id, handler));
            if(sessionId != null) {
                sendEvents(id, sessionId, stringList, list);
            } else {
                logger.info("Received event(s) for user " + id + " but user not logged in.");
            }
        }
    }

    private Map<Integer, List<Event>> sortEvents(String msg) {
        Map<Integer, List<Event>> map = new HashMap<>();

        Event[] array = instantiator.createEventArrayFromString(msg);
        for(Event event : array) {
            map.computeIfAbsent(event.getUserId(), id -> new ArrayList<>()).add(event);
        }

        return map;
    }

    @Suspendable
    private void sendEvents(int userId, String sessionId,
                            String stringList, List<Event> events) {
        try(DataIO dio = instantiator.getDataIoWithCache()) {
            SyncUtil.<Message<String>>await(handler ->
                    vertx.eventBus().send("client." + sessionId + ".events", stringList, handler));

            Set<Integer> eventIds = new HashSet<>();

            SyncUtil.awaitBlocking(vertx, future -> {
                for(Event event : events) {
                    if(event.getUserId() != null) {
                        dio.markEventSeen(event.getId());
                    }
                    eventIds.add(event.getId());
                }
                dio.commitSql();
                future.complete();
            });

            logger.info("Sent event(s) to user " + userId + " (sessionId: " + sessionId + "): "
                    + GSON.toJson(eventIds));
        } catch(VertxException e) {
            SyncUtil.handleVertxReplyException(cache, userId, sessionId, e,
                    Sync.fiberHandler(correctSessionId ->
                            sendEvents(userId, correctSessionId, stringList, events)));
        }
    }

}
