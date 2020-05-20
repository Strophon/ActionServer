package com.github.strophon.action;

import com.github.strophon.util.Randomizer;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.github.strophon.action.constants.ActionConstants;
import com.github.strophon.action.input.ActionInput;
import com.github.strophon.action.output.Event;
import com.github.strophon.action.output.Result;
import com.github.strophon.data.DataIO;
import com.github.strophon.data.transfer.ObjectContainer;

import java.util.List;
import java.util.function.Consumer;

public interface Action {
    Gson GSON = new Gson();

    void setInput(ActionInput input);
    ActionInput getInput();

    void setConstants(ActionConstants constants);

    void setError(Result error);
    Result getError();

    void setResult(Result result);
    Result getResult();

    void setSubsequent(boolean subsequent);
    boolean isSubsequent();

    void setSubsequentActions(List<ActionInput> subsequentActions);
    List<ActionInput> getSubsequentActions();

    void setSubInjectionIndex(int subInjectionIndex);
    int getSubInjectionIndex();



    // TODO: javadoc for below methods

    void checkInputFields();

    void init();

    void setSeed(byte[] seed);
    byte[] getSeed();

    void setRandomizer(Randomizer randomizer);
    Randomizer getRandomizer();

    void setDataIO(DataIO dio);

    ObjectContainer fetchAndLockDataObjects();

    void inject(ObjectContainer container);

    Result checkForErrors();

    void createResult();

    Result performAction();

    void writeChanges();



    void setEvents(List<Event> events);
    List<Event> getEvents();

    void setOriginalInjection(String originalInjection);
    String getOriginalInjection();

    String getInjectionAsString();

    ObjectContainer getInjection();

    default boolean needsRandomNumbers() {
        return false;
    }

    default boolean allowedWhilePaused() {
        return false;
    }

    default void logAction(boolean debug, boolean trace, Consumer<Object> loggingFunction) {
        JsonObject object = new JsonObject();

        object.addProperty("input", GSON.toJson(getInput()));

        if(needsRandomNumbers() && getSeed() != null) {
            object.addProperty("seed", GSON.toJson(getSeed()));
        }

        Result result = getError() == null ? getResult() : getError();
        object.addProperty("result", GSON.toJson(result));

        if(debug) {
            object.addProperty("originalInjection", getOriginalInjection());
        }

        if(trace) {
            object.addProperty("injection", getInjectionAsString());
            object.addProperty("events", GSON.toJson(getEvents()));
        }

        loggingFunction.accept("Action performed: " + object.toString());
    }
}
