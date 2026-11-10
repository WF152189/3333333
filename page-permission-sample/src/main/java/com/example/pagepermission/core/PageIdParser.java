package com.example.pagepermission.core;

import com.example.pagepermission.annotation.Operation;

import java.util.Arrays;

public final class PageIdParser {
    public String groupId(String pageId) {
        validate(pageId);
        return pageId.substring(0, 4);
    }

    public Operation operation(String pageId) {
        validate(pageId);
        String actionCode = pageId.substring(pageId.length() - 1);
        return Arrays.stream(Operation.values())
                .filter(it -> it.pageActionCode().equals(actionCode))
                .findFirst()
                .orElseThrow(() -> new PagePermissionException(
                        PagePermissionError.PAGE_ID_INVALID,
                        "Unknown page action code: " + actionCode));
    }

    private void validate(String pageId) {
        if (pageId == null || !pageId.matches("\\d{5}")) {
            throw new PagePermissionException(
                    PagePermissionError.PAGE_ID_INVALID,
                    "pageId must contain exactly five digits");
        }
    }
}
