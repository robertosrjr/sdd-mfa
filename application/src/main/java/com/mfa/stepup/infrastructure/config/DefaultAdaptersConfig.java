package com.mfa.stepup.infrastructure.config;

import com.mfa.stepup.application.port.out.BusinessOperationPort;
import com.mfa.stepup.application.port.out.BusinessOperationReconciliationPort;
import com.mfa.stepup.application.port.out.IdentityProviderPort;
import com.mfa.stepup.application.port.out.PolicyAuthorizationPort;
import com.mfa.stepup.domain.model.AuthenticationEvidence;
import com.mfa.stepup.domain.model.AuthorizationDecision;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.time.Instant;

/**
 * Configuração de adaptadores padrão para execução local e testes sem necessidade de provedores externos reais.
 */
@Configuration
public class DefaultAdaptersConfig {

    @Bean
    @ConditionalOnMissingBean(BusinessOperationReconciliationPort.class)
    public BusinessOperationReconciliationPort defaultReconciliationPort() {
        return (operationId, operationType) -> BusinessOperationPort.ExecutionResult.unknown();
    }

    @Bean
    @ConditionalOnMissingBean(IdentityProviderPort.class)
    public IdentityProviderPort defaultIdentityProviderPort() {
        return new IdentityProviderPort() {
            @Override
            public AuthenticationEvidence getAuthenticationEvidence(com.mfa.stepup.domain.model.SubjectId subjectId) {
                return new AuthenticationEvidence("urn:mfa:default", Instant.now(), "FIDO2");
            }

            @Override
            public String getRequiredAcr(com.mfa.stepup.domain.model.OperationType operationType) {
                return "urn:mfa:default";
            }

            @Override
            public Duration getMaxAge(com.mfa.stepup.domain.model.OperationType operationType) {
                return Duration.ofMinutes(15);
            }
        };
    }

    @Bean
    @ConditionalOnMissingBean(PolicyAuthorizationPort.class)
    public PolicyAuthorizationPort defaultPolicyAuthorizationPort() {
        return (subjectId, actorId, operationType, operationId) -> AuthorizationDecision.allow("default_policy_1.0");
    }

    @Bean
    @ConditionalOnMissingBean(BusinessOperationPort.class)
    public BusinessOperationPort defaultBusinessOperationPort() {
        return (operationId, operationType, intentDigest, intentVersion) ->
            BusinessOperationPort.ExecutionResult.success("default_tx_ref_" + operationId.value());
    }
}

