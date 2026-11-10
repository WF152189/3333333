package com.example.pagepermission.core;

import com.example.pagepermission.annotation.Operation;
import com.example.pagepermission.annotation.PagePermissionGroup;
import com.example.pagepermission.annotation.PagePermissionItem;

public record PermissionContext(
        PageClaims sourceClaims,
        String sourcePageId,
        String targetPageId,
        boolean relatedCall,
        PagePermissionGroup group,
        PagePermissionItem item,
        String code,
        Operation operation) {
}
