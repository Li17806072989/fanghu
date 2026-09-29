package com.fanghu.platform.service;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class WorkflowStatusTest {
    @Test void acceptsOnlySupportedLeadStatuses() {
        assertEquals("WON", WorkflowStatus.lead("WON"));
        assertThrows(IllegalArgumentException.class, () -> WorkflowStatus.lead("unknown"));
    }
}
