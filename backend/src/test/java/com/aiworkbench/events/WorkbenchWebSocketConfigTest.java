package com.aiworkbench.events;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class WorkbenchWebSocketConfigTest {
    @Test
    void acceptsOnlyExplicitBrowserOrigins() {
        var hub = mock(WorkbenchEventHub.class);
        assertDoesNotThrow(() -> new WorkbenchWebSocketConfig(hub,
                "https://workbench.example.com,http://127.0.0.1:8088"));
        for (String invalid : new String[]{"*", "", ",", "https://example.com,", "https://*.example.com", "https://example.com/path",
                "https://user@example.com", "https://example.com?x=1", "https://example.com#fragment"}) {
            assertThrows(IllegalArgumentException.class, () -> new WorkbenchWebSocketConfig(hub, invalid));
        }
    }
}
