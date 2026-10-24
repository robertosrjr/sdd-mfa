package com.mfa.stepup.contract;

import com.mfa.stepup.MfaStepupApplication;
import com.mfa.stepup.application.port.in.AuthorizeSensitiveOperationUseCase;
import com.mfa.stepup.infrastructure.adapter.in.web.OperationAuthorizationController;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers=OperationAuthorizationController.class)
@ContextConfiguration(classes=MfaStepupApplication.class)
class IdempotencyHeaderContractTest {
    @Autowired MockMvc mvc;
    @MockitoBean AuthorizeSensitiveOperationUseCase useCase;
    @Test void rejectsMissingHeader() throws Exception {
        mvc.perform(post("/api/v1/operation-authorizations").contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.title").value("Invalid Idempotency Key"));
        verifyNoInteractions(useCase);
    }
    @ParameterizedTest @ValueSource(strings={"short","bad key with spaces",""})
    void rejectsMalformedHeader(String key) throws Exception {
        mvc.perform(post("/api/v1/operation-authorizations").header("Idempotency-Key",key).contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isBadRequest()).andExpect(content().contentTypeCompatibleWith("application/problem+json"));
        verifyNoInteractions(useCase);
    }
    @Test void rejectsExcessiveHeader() throws Exception {
        mvc.perform(post("/api/v1/operation-authorizations").header("Idempotency-Key","a".repeat(256)).contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.title").value("Invalid Idempotency Key"));
        verifyNoInteractions(useCase);
    }
}
