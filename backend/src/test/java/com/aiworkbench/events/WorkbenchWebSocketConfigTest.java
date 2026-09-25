package com.aiworkbench.events;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class WorkbenchWebSocketConfigTest {
    @Test
    void acceptsOnlyExplicitBrowserOrigins() {
        var sockets = mock(WorkbenchSocketSessions.class);
        var guard = mock(WorkbenchStompGuard.class);
        assertDoesNotThrow(() -> new WorkbenchWebSocketConfig(sockets, guard,
                "https://workbench.example.com,http://127.0.0.1:8088"));
        for (String invalid : new String[]{"*", "", ",", "https://example.com,", "https://*.example.com", "https://example.com/path",
                "https://user@example.com", "https://example.com?x=1", "https://example.com#fragment"}) {
            assertThrows(IllegalArgumentException.class, () -> new WorkbenchWebSocketConfig(sockets, guard, invalid));
        }
    }
}
