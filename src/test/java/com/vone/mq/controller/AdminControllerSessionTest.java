package com.vone.mq.controller;

import com.vone.mq.service.AdminService;
import com.vone.mq.utils.ResUtil;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AdminControllerSessionTest {

    private final AdminService adminService = mock(AdminService.class);
    private final AdminController controller = new AdminController(adminService);

    @Test
    void successfulLoginRotatesSessionIdAndMarksSessionAuthenticated() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpSession session = new MockHttpSession();
        request.setSession(session);
        String originalSessionId = session.getId();
        when(adminService.login("admin", "secret")).thenReturn(ResUtil.success());

        controller.login(request, "admin", "secret");

        assertFalse(originalSessionId.equals(request.getSession(false).getId()));
        assertEquals("1", request.getSession(false).getAttribute("login"));
    }

    @Test
    void logoutInvalidatesCurrentSession() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("login", "1");
        request.setSession(session);

        controller.logout(request);

        assertTrue(session.isInvalid());
    }

    @Test
    void menuUsesStableAssetVersionsInsteadOfPerRequestTimestamps() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("login", "1");

        List<Map<String, Object>> first = controller.getMenu(session);
        List<Map<String, Object>> second = controller.getMenu(session);

        assertEquals(first, second);
        assertTrue(String.valueOf(first.get(0).get("url")).contains("?v="));
        assertNull(controller.getMenu(new MockHttpSession()));
    }
}
