package com.example.pagepermission.core;

public final class PagePermissionException extends RuntimeException {
    private final PagePermissionError error;

    public PagePermissionException(PagePermissionError error, String message) {
        super(message);
        this.error = error;
    }

    public PagePermissionException(PagePermissionError error, String message, Throwable cause) {
        super(message, cause);
        this.error = error;
    }

    public PagePermissionError error() {
        return error;
    }
}
