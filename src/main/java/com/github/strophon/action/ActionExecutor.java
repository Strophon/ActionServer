package com.github.strophon.action;


import com.github.strophon.data.transfer.DataSource;
import com.github.strophon.util.Randomizer;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import com.github.strophon.action.input.ActionInput;
import com.github.strophon.action.output.Result;
import com.github.strophon.data.DataIO;
import com.github.strophon.data.transfer.ObjectContainer;

import java.lang.reflect.Type;
import java.util.*;
import java.util.function.Function;

public class ActionExecutor {
    private static final Logger logger = LoggerFactory.getLogger(ActionExecutor.class);
    private static final Gson GSON = new Gson();

    private Set<ActionType> allowedTypes;
    private Function<String, ActionType> typeRetriever;

    public ActionExecutor(List<ActionType> allowedTypes,
                          Function<String, ActionType> typeRetriever) {
        this.typeRetriever = typeRetriever;

        this.allowedTypes = new HashSet<>();

        for(ActionType at : allowedTypes) {
            if(at.getConstructor() != null) {
                this.allowedTypes.add(at);
            }
        }
    }

    public Action getAction(ActionInput input) {
        ActionType type = getTypeFromInput(input);

        Action action = type.getConstructor().get();

        action.setInput(input);
        action.setConstants(type.getConstants());

        action.setResult(null);
        action.setError(null);

        action.checkInputFields();

        if(action.getError() == null) {
            action.setSubsequent(false);
            action.setSubsequentActions(new ArrayList<>());
            action.setSubInjectionIndex(0);

            action.setEvents(new ArrayList<>());

            action.init();
        }

        return action;
    }

    private ActionType getTypeFromInput(ActionInput input) {
        try {
            ActionType type = typeRetriever.apply(input.getActionType());

            if(!allowedTypes.contains(type)) {
                return ActionType.NON_EXISTENT;
            }

            return type;
        } catch(Exception e) {
            return ActionType.NON_EXISTENT;
        }
    }

    public Result execute(Action action, byte[] seed, DataSource dataSource) {
        if(action.needsRandomNumbers()) {
            setRandomizerSeed(action, seed);
        }

        ObjectContainer injection = dataSource.getContainer();
        if(action.getError() == null && dataSource.useDatabase()) {
            action.setDataIO(dataSource.getDataIO());
            injection = action.fetchAndLockDataObjects();
        }

        inject(action, injection);

        Result result = performAction(action);

        writeChanges(action, dataSource.getDataIO());

        while(action.getError() == null
                && action.getSubsequentActions() != null
                && !action.getSubsequentActions().isEmpty()) {
            performSubsequentActions(action, dataSource.getDataIO());
        }

        return result;
    }

    private void setRandomizerSeed(Action action, byte[] seed) {
        action.setSeed(seed);
        Randomizer rand = new Randomizer(seed);
        action.setRandomizer(rand);
    }

    private void inject(Action action, ObjectContainer container) {
        String originalInjection = GSON.toJson(container);
        action.setOriginalInjection(originalInjection);

        if(action.getError() == null) {
            action.inject(container);
        }
    }

    private Result performAction(Action action) {
        // see if any errors came up during initialization,
        // then check for data-dependent errors if not
        Result error = action.getError();
        if(error != null || (error = checkForErrors(action)) != null) {
            action.setError(error);
            return error.setType(action.getInput().getActionType());
        }

        createResult(action);

        return action.performAction();
    }

    private static final Type STRING_SET_TYPE = new TypeToken<Set<String>>(){}.getType();

    private Result checkForErrors(Action action) {
        ActionType type = getTypeFromInput(action.getInput());
        if(!type.getAuthorityRequired().equals(ActionType.Authority.USER.name())) {
            boolean unauthorized = true;

            if(action.getInjection().getUser() != null) {
                Set<String> authorities =
                        GSON.fromJson(
                                action.getInjection().getUser().getAuthorities(), STRING_SET_TYPE);

                if(authorities.contains(type.getAuthorityRequired())) {
                    unauthorized = false;
                }
            }

            if(unauthorized) {
                return new Result()
                        .setSuccess(false)
                        .setError("You do not have the authority to perform that action.");
            }
        }

        return action.checkForErrors();
    }

    private void createResult(Action action) {
        action.createResult();
        action.getResult().setType(action.getInput().getActionType());
    }

    private void writeChanges(Action action, DataIO dio) {
        if(action.getError() == null) {
            if(dio != null) {
                action.writeChanges();

                if(!action.getEvents().isEmpty()) {
                    dio.addEvents(action.getEvents());
                }

                dio.commitSql();
            }

            action.getResult().setSuccess(true);
        }
    }

    private void performSubsequentActions(Action action, DataIO dio) {
        List<ActionInput> currentSubsequentActions = action.getSubsequentActions();
        action.setSubsequentActions(new ArrayList<>());

        for(ActionInput input : currentSubsequentActions) {
            Action subsequentAction = getAction(input);
            subsequentAction.setSubsequent(true);
            try {
                subsequentAction.setRandomizer(action.getRandomizer());

                if(dio != null) {
                    subsequentAction.setDataIO(dio);
                    action.getInjection().addSubInjection(subsequentAction.fetchAndLockDataObjects());
                }

                int subInjectionIndex = action.getSubInjectionIndex();

                inject(subsequentAction, action.getInjection()
                                               .getSubInjections()
                                               .get(subInjectionIndex));

                action.setSubInjectionIndex(++subInjectionIndex);

                Result subsequentResult = performAction(subsequentAction);

                writeChanges(subsequentAction, dio);

                action.getSubsequentActions().addAll(subsequentAction.getSubsequentActions());

                if(subsequentResult.getSuccess() != null && subsequentResult.getSuccess()) {
                    action.getEvents().addAll(subsequentAction.getEvents());
                } else {
                    logger.error("Error from subsequent action (Input: "
                            + GSON.toJson(subsequentAction.getInput()) + " ): "
                            + subsequentResult.getError());
                }
            } catch(Exception e) {
                logger.error("Exception from subsequent action (Input: "
                        + GSON.toJson(subsequentAction.getInput()) + " ): ", e);
            }
        }
    }
}
