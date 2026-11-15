package com.rizkysiregar.feedzmore.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rizkysiregar.feedzmore.constant.GlobalMessage;
import com.rizkysiregar.feedzmore.model.response.BaseResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Answers 403 with the same JSON body the controller level denial (@PreAuthorize) already uses.
 * <p>
 * The default handler calls sendError(403), which makes the container dispatch to /error; /error needs a login
 * and the security context is gone by then, so a logged in user without the role ended up with an empty 401.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JsonAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        log.warn("ACCESS.DENIED {} {}", request.getMethod(), request.getRequestURI());
        if (response.isCommitted()) {
            return;
        }
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), BaseResponse.error(GlobalMessage.ACCESS_DENIED));
    }
}
