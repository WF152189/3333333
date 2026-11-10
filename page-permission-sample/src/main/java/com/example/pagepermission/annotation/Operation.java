package com.example.pagepermission.annotation;

public enum Operation {
    QUERY("1"), CREATE("2"), UPDATE("3"), DELETE("4");

    private final String pageActionCode;

    Operation(String pageActionCode) {
        this.pageActionCode = pageActionCode;
    }

    public String pageActionCode() {
        return pageActionCode;
    }
}
