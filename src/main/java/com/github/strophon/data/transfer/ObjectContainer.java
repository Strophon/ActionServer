package com.github.strophon.data.transfer;

import java.util.ArrayList;
import java.util.List;

public abstract class ObjectContainer {
    private long timestamp;
    private User user;
    private List<ObjectContainer> subInjections;

    public ObjectContainer() {
        this(null);
    }

    public ObjectContainer(Long timestamp) {
        if(timestamp == null) {
            this.timestamp = System.currentTimeMillis();
        } else {
            this.timestamp = timestamp;
        }
    }

    public long getTimestamp() {
        return timestamp;
    }
    public ObjectContainer setTimestamp(long timestamp) {
        this.timestamp = timestamp;
        return this;
    }
    public User getUser() {
        return user;
    }
    public ObjectContainer setUser(User user) {
        this.user = user;
        return this;
    }
    public ObjectContainer addSubInjection(ObjectContainer injection) {
        if(subInjections == null) {
            subInjections = new ArrayList<>();
        }
        subInjections.add(injection);
        return this;
    }
    public List<ObjectContainer> getSubInjections() {
        return subInjections;
    }
}
