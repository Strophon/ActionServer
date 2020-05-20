package com.github.strophon.action;

import com.github.strophon.action.constants.ActionConstants;
import com.github.strophon.action.output.Event;
import com.github.strophon.action.output.Result;
import com.github.strophon.data.DataIO;
import com.github.strophon.data.transfer.ObjectContainer;
import com.github.strophon.util.Randomizer;
import com.github.strophon.action.input.ActionInput;

import java.util.List;
import java.util.function.Supplier;

public interface ActionType {
    ActionType NON_EXISTENT = new ActionType() {
        private final String enumName = "NON_EXISTENT";

        @Override
        public Supplier<Action> getConstructor() {
            return NonExistentAction::new;
        }

        @Override
        public ActionConstants getConstants() {
            return null;
        }

        @Override
        public String getAuthorityRequired() {
            return Authority.USER.name();
        }

        @Override
        public String getEnumName() {
            return enumName;
        }
    };

    Supplier<Action> getConstructor();

    ActionConstants getConstants();

    String getAuthorityRequired();

    String getEnumName();

    enum Authority {
        USER, MODERATOR, ADMIN;
    }

    class NonExistentAction implements Action {
        private Result error = new Result().setSuccess(false)
                                           .setError("Missing or invalid action type");
        private ActionInput input;

        NonExistentAction() {
        }

        public void checkInputFields() {
        }

        public void init() {
        }

        @Override
        public void setSeed(byte[] seed) {
        }

        @Override
        public byte[] getSeed() {
            return null;
        }

        @Override
        public void setRandomizer(Randomizer randomizer) {
        }

        @Override
        public Randomizer getRandomizer() {
            return null;
        }

        @Override
        public void setDataIO(DataIO dio) {
        }

        public ObjectContainer fetchAndLockDataObjects() {
            return null;
        }

        @Override
        public void inject(ObjectContainer injection) {
        }

        @Override
        public Result checkForErrors() {
            return null;
        } // override this to prevent NPE


        @Override
        public void createResult() {
        }

        @Override
        public Result performAction() {
            return null;
        }

        @Override
        public void writeChanges() {
        }

        @Override
        public List<Event> getEvents() {
            return null;
        }

        @Override
        public void setEvents(List<Event> events) {
        }

        @Override
        public void setOriginalInjection(String originalInjection) {
        }

        @Override
        public String getOriginalInjection() {
            return null;
        }

        @Override
        public String getInjectionAsString() {
            return null;
        }

        @Override
        public ObjectContainer getInjection() {
            return null;
        }

        @Override
        public void setInput(ActionInput input) {
            this.input = input;
        }

        @Override
        public ActionInput getInput() {
            return input;
        }

        @Override
        public void setConstants(ActionConstants constants) {
        }

        @Override
        public void setError(Result error) {
        }

        @Override
        public Result getError() {
            return error;
        }

        @Override
        public void setResult(Result result) {
        }

        @Override
        public Result getResult() {
            return null;
        }

        @Override
        public void setSubsequent(boolean subsequent) {
        }

        @Override
        public boolean isSubsequent() {
            return false;
        }

        @Override
        public void setSubsequentActions(List<ActionInput> subsequentActions) {
        }

        @Override
        public List<ActionInput> getSubsequentActions() {
            return null;
        }

        @Override
        public int getSubInjectionIndex() {
            return 0;
        }

        @Override
        public void setSubInjectionIndex(int subInjectionIndex) {
        }
    }
}

