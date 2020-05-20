package com.github.strophon.data;

import com.github.strophon.action.output.Event;
import com.github.strophon.data.transfer.User;

import java.io.Closeable;
import java.util.List;

public interface DataIO extends Closeable {

    void commitSql();

    void commitSql(boolean force);

    void close();

    User getUser(int id, boolean lock);

    User getUserByEmail(String email);

    void setUserEmailConfirmed(int userId);

    void addEvents(List<Event> events);

    void markEventSeen(int eventId);

    List<? extends Event> getUnseenEventsSinceFirstUnseen(int userId);

    boolean isNameUsed(String name);

    boolean isEmailUsed(String email);

    void addUser(User user);

    void updateUser(User user);

    void deleteUser(User user);
}
