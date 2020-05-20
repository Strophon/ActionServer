package com.github.strophon.action.constants;

public class ActionConstants {
    private boolean mutable;
    private long max;

    public ActionConstants() {
        mutable = true;
    }

    public ActionConstants(int max) {
        this.max = max;
        mutable = true;
    }

    public long getMax() {
        return max;
    }
    public ActionConstants setMax(long max) {
        if(isMutable())
            this.max = max;
        return this;
    }

    protected final boolean isMutable() {
        return mutable;
    }

    public final ActionConstants setImmutable() {
        this.mutable = false;
        return this;
    }
}
