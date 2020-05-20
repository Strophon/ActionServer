package com.github.strophon.action.output;

public class Result {
    private String type;
    private Boolean success;
    private String result;
    private String error;

    public String getType() {
        return type;
    }
    public Result setType(String type) {
        this.type = type;
        return this;
    }
    public Boolean getSuccess() {
        return success;
    }
    public Result setSuccess(Boolean success) {
        this.success = success;
        return this;
    }
    public String getResult() {
        return result;
    }
    public Result setResult(String result) {
        this.result = result;
        return this;
    }
    public String getError() {
        return error;
    }
    public Result setError(String error) {
        this.error = error;
        return this;
    }
}
