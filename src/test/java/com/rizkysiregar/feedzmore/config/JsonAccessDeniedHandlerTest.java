package com.rizkysiregar.feedzmore.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rizkysiregar.feedzmore.constant.GlobalMessage;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonAccessDeniedHandlerTest {

    private final JsonAccessDeniedHandler handler = new JsonAccessDeniedHandler(new ObjectMapper());

    @Test
    void answers403WithTheAppJsonBodyAndNeverSendsAnError() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.handle(new MockHttpServletRequest("GET", "/api/v1/admin/users"), response, new AccessDeniedException("denied"));

        assertEquals(403, response.getStatus());
        assertTrue(response.getContentType().startsWith("application/json"));
        assertTrue(response.getContentAsString().contains("\"code\":\"403\""));
        assertTrue(response.getContentAsString().contains(GlobalMessage.ACCESS_DENIED.getMessage()));
        // sendError would dispatch to /error, where the lost login turns the 403 into an empty 401
        assertNull(response.getErrorMessage());
    }
}
