package com.github.strophon.action.output;

import java.util.Date;

public interface Event {
    int getId();
    void setId(int id);
    Date getTimestamp();
    void setTimestamp(Date timestamp);
    boolean isSeen();
    void setSeen(boolean seen);
    Integer getUserId();
    void setUserId(Integer userId);
    Integer getOtherUserId();
    void setOtherUserId(Integer otherUserId);
    String getData();
    void setData(String data);
}
