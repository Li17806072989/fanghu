package org.dromara.fanghu.service;

import java.util.Set;

public final class WorkflowStatus {
    private static final Set<String> AUDIT = Set.of("DRAFT", "PENDING", "APPROVED", "REJECTED", "OFFLINE");
    private static final Set<String> LEAD = Set.of("NEW", "MATCHED", "CONTACTED", "INTENT", "WON", "LOST");

    private WorkflowStatus() { }

    public static String audit(String value) {
        if (!AUDIT.contains(value)) throw new IllegalArgumentException("不支持的审核状态");
        return value;
    }

    public static String lead(String value) {
        if (!LEAD.contains(value)) throw new IllegalArgumentException("不支持的线索状态");
        return value;
    }
}
