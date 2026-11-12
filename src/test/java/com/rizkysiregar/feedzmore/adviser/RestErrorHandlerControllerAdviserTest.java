package com.rizkysiregar.feedzmore.adviser;

import com.rizkysiregar.feedzmore.constant.GlobalMessage;
import com.rizkysiregar.feedzmore.exception.BusinessException;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RestErrorHandlerControllerAdviserTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ThrowingController())
                .setControllerAdvice(new RestErrorHandlerControllerAdviser())
                .build();
    }

    @Test
    void missingRowIsA404NotA500() throws Exception {
        mockMvc.perform(get("/entity-not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("404"));
    }

    @Test
    void wrongMethodIsA405NotA500() throws Exception {
        mockMvc.perform(post("/entity-not-found"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("405"));
    }

    @Test
    void missingRequestParameterIsA400NotA500() throws Exception {
        mockMvc.perform(get("/needs-param"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("400"))
                .andExpect(jsonPath("$.message").value(GlobalMessage.BAD_REQUEST.getMessage()));
    }

    @Test
    void businessExceptionKeepsItsStatusAndCode() throws Exception {
        mockMvc.perform(get("/business"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(GlobalMessage.ACCESS_DENIED.getCode()));
    }

    @Test
    void anythingUnexpectedIsStillA500WithAGenericMessage() throws Exception {
        mockMvc.perform(get("/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value(GlobalMessage.INTERNAL_ERROR.getMessage()));
    }

    @RestController
    static class ThrowingController {
        @GetMapping("/entity-not-found")
        String entityNotFound() {
            throw new EntityNotFoundException("Post Not Found");
        }

        @GetMapping("/needs-param")
        String needsParam(@RequestParam("page") int page) {
            return "ok";
        }

        @GetMapping("/business")
        String business() {
            throw new BusinessException(GlobalMessage.ACCESS_DENIED, HttpStatus.FORBIDDEN);
        }

        @GetMapping("/boom")
        String boom() {
            throw new IllegalStateException("secret internal detail");
        }
    }
}
