package com.github.strophon.action.input;

public interface ActionInput {
    String getSessionId();
    void setSessionId(String sessionId);

    Integer getUserId();
    void setUserId(Integer userId);

    String getActionType();
    void setActionType(String actionType);
}
