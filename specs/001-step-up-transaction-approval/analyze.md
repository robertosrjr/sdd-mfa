---
description: "Implementation evidence and open gates for sensitive operation authorization"
date: "2026-10-24"
status: "LOCAL_IMPLEMENTATION_WITH_OPEN_GATES"
---

# Auditoria da implementação

A revisão distingue código implementado, comportamento verificado localmente e integração
externa ainda ausente. Cobertura por tarefa não significa requisito validado em produção.
Os requisitos funcionais originais foram preservados.

## Validação

- Testes focados da Fase 6: 14 aprovados, sem falhas ou erros.
- Suíte completa: `mvn -o clean test`, 102 testes descobertos, 100 aprovados, zero falhas/erros,
  2 PostgreSQL ignorados sem Docker. Log local em `application/target/phase6-verification.log`.
- Aceitação HTTP: servidor real em porta aleatória, H2 e adaptadores controlados para identidade,
  política, execução e consulta do resultado. Não inclui interface humana ou sistemas externos reais.
- Telemetria: spans reais do SDK OpenTelemetry exportados em memória durante os testes,
  logs Logback capturados e leitura de `/actuator/prometheus`.
- PostgreSQL: dois testes disponíveis, não executados sem Docker. T028 permanece aberta.

## Quality gates SDD

| Gate | Resultado | Evidência / limite |
|---|---|---|
| Requisitos observáveis | Atendido no contrato de backend | Cenários de autenticação, política, intenção, delegação, replay e resultado incerto possuem asserções. |
| Requisitos separados da tecnologia | Atendido | `spec.md` mantém requisitos funcionais; decisões técnicas ficam no plano e código. |
| Alinhamento constitucional | Parcial para a jornada completa | Backend separa evidências e protege consumo; vínculo real com identidade e apresentação ao titular ainda são integrações externas. |
| Rastreabilidade | Mapeada abaixo | Cobertura documental não foi confundida com validação externa. |
| Complexidade proporcional | Atendido no backend local | Transações curtas, chave durável, consulta sem reenvio e decorador de telemetria; sem fila de aprovação. |

## Pendências e achados

| ID | Estado | Achado | Ação restante |
|---|---|---|---|
| F1 | Corrigido | Especificação dizia que não havia Git/aplicação. | Metadados e plano atualizados para o workspace existente. |
| F2 | Aberto / T001 | Plano original pede compilação Java 25 e Spring Boot 4; o build usa release 21 e Boot 3.4.2. | Alinhar a plataforma original em trabalho próprio; a Fase 6 não afirma ter realizado essa migração. |
| F3 | Implementado | Retenção/lease de idempotência sem padrão operacional. | Padrões PT24H/PT30S configurados; limpeza automática não implementada e resultados incertos não são descartados. |
| F4 | Aberto / T028 | PostgreSQL/Testcontainers não foi executado sem Docker. | Executar `mvn "-Dtest=PostgreSQLConcurrentExecutionTest" test` com Docker. |
| F5 | Integração externa | Identidade, política, negócio e consulta usam adaptadores locais simulados. IDs do payload não provam identidade. | Vincular o principal autenticado e substituir adaptadores antes de usar com operações reais. |
| F6 | Integração externa | Interface de apresentação e coleta humana de consentimento não faz parte deste backend. | Verificar apresentação da intenção/atribuição e emissão confiável da aprovação no fluxo titular. |
| F7 | Configuração externa | Referências HMAC usam chave aleatória por processo quando não há segredo configurado. | Definir `MFA_AUDIT_PSEUDONYM_KEY` estável e protegido para correlacionar auditoria durável entre reinícios. |
| F8 | Corrigido e testado | Logs de auditoria continham identificadores/texto livre; handlers e tracing podiam registrar mensagens/causas sensíveis. | Auditoria com referências HMAC e vocabulário limitado; texto livre omitido; erros de tracing saneados antes da captura. |
| F9 | Limite operacional | Lockout atual é mantido em memória; produção e topologia distribuída não foram avaliadas. | Definir armazenamento/limites conforme implantação. A suíte verifica isolamento por operação no processo local. |

Referências HMAC são pseudônimos, não uma declaração de anonimização ou certificação legal.
O adaptador de auditoria realiza apenas INSERT; permissões de banco, retenção e operação do
coletor de logs pertencem à implantação. Não foi configurado exportador externo de tracing.

## Matriz requisito → tarefas → evidências

Os nomes de testes abaixo estão em `application/src/test/java/com/mfa/stepup/`.

| Requisito | Tarefas | Evidência principal | Resultado |
|---|---|---|---|
| FR-001: determinar step-up | T010, T015, T016, T036 | `AuthenticationContextTest`, `AuthorizationAcceptanceTest` | Validado local; política real pendente. |
| FR-002: decisões separadas | T004, T010, T011, T013, T016, T036 | `OperationAuthorizationTest`, `AuthorizationAcceptanceTest` | Autenticação, política e consentimento independentes. |
| FR-003: recusar evidência inválida | T011, T012, T016, T018, T019, T025 | `AuthorizeSensitiveOperationIntegrationTest`, `DelegatedOperationIntegrationTest` | Sem consumo/execução nos caminhos recusados. |
| FR-004: apresentar intenção ao titular | T009, T014, T018 | Canonicalizador e contrato de aprovação | Integração de apresentação não implementada; F6 aberta. |
| FR-005: vincular intenção e operação | T009, T011, T013, T014, T016, T025 | `CanonicalIntentIntegrityTest`, `DelegatedOperationIntegrationTest` | Digest, versão, operação e atribuição comparados à evidência persistida; emissão confiável externa pendente. |
| FR-006: mudança exige nova aprovação | T009, T011, T014, T019, T025, T036 | `CanonicalIntentIntegrityTest`, `AuthorizationAcceptanceTest` | Alteração material recusada; codificação tipada evita colisões de delimitadores. |
| FR-007: validade e consumo único | T006, T011, T013, T016, T017, T019, T028 | `ConcurrentExecutionTest`, `DelegatedOperationIntegrationTest` | H2 validado; PostgreSQL pendente. |
| FR-008: recusa/cancelamento impedem execução | T011, T013, T016, T018, T025 | Casos `rejected`, `cancelled`, `missingApproval` em `DelegatedOperationIntegrationTest` | Backend não executa; cancelamento na interação humana depende de F6. |
| FR-009: atribuição e consentimento delegado | T020–T025 | `DelegationAttributionTest`, `DelegatedOperationContractTest`, `DelegatedOperationIntegrationTest` | Atribuição preservada e prova humana exigida; apresentação/identidade real pendentes. |
| FR-010: impedir autoaprovação | T020, T022, T023, T025 | `DelegationAttributionTest`, caso `agentApproval` de integração | Agente não substitui titular na evidência persistida. |
| FR-011: replay sem nova execução | T026–T031, T033, T036 | `ConcurrentExecutionTest`, `IdempotencyReconciliationIntegrationTest`, `AuthorizationAcceptanceTest` | Resultado terminal persistido/reproduzido; payload diferente retorna 409. |
| FR-012: reconciliar resultado incerto | T026, T030, T032, T033, T036 | `IdempotencyReconciliationIntegrationTest`, `AuthorizationAcceptanceTest` | HTTP 202, recuperação por consulta, fencing de proprietário antigo; sem reenvio de execução. |
| FR-013: auditoria sem dados desnecessários | T007, T034 | `AuditPrivacyComplianceTest`, `AuthorizationObservabilityIntegrationTest` | Referências protegidas, sem texto livre/credenciais nos cenários verificados, inclusive erro JDBC. |
| FR-014: erros informativos sem segredos | T005, T012, T018, T034 | `GlobalExceptionHandlerTest`, `AuthorizationObservabilityIntegrationTest` | Detalhes genéricos, metadados de política limitados e causas omitidas. |
| FR-015: bloqueio temporário por operação | T008, T016, T036 | `TemporaryLockoutAdapterTest`, `AuthorizationAcceptanceTest` | HTTP 429/Retry-After; outra operação não recebe bloqueio global. |

## Matriz tarefas → requisitos

| Tarefas | Requisitos / propósito |
|---|---|
| T001–T003 | Infraestrutura de build e limites arquiteturais para todos os requisitos; versão original de T001 pendente. |
| T004–T006 | FR-002, FR-005, FR-007, FR-014: tipos, erros e schema. |
| T007 | FR-013: registro de auditoria. |
| T008 | FR-015: limite temporário de tentativas. |
| T009–T011 | FR-001–FR-008: intenção, autenticação, estados e consumo. |
| T012–T019 | FR-001–FR-008, FR-014, FR-015: contrato, orquestração, persistência e integração. |
| T020–T025 | FR-009, FR-010 e vinculação FR-005: atribuição e aprovação do titular. |
| T026–T031 | FR-007, FR-011: estados duráveis, fingerprint, exclusão e replay. |
| T032–T033 | FR-012: consulta/reconciliação de resultado incerto. |
| T034 | FR-013, FR-014: privacidade da auditoria, erros, métricas e spans. |
| T035 | Observabilidade transversal: contagem de decisões e latência com rótulos limitados. |
| T036 | Verificação HTTP local dos requisitos acima; não representa validação das integrações F5/F6. |
| T037 | Esta auditoria e rastreabilidade bidirecional. |

## Critérios de sucesso

| Critério | Evidência | Limite |
|---|---|---|
| SC-001 / SC-002 | Falhas de autenticação, política, aprovação e mudança de intenção não executam. | Adaptadores controlados. |
| SC-003 | Repetição e concorrência com mesmo escopo/chave chamam negócio uma vez. | PostgreSQL pendente. |
| SC-004 | API preserva sujeito/ator validados; agente não aprova. | Interface humana externa. |
| SC-005 | Timeout/interrupção levam à consulta; resposta antiga não sobrescreve a reconciliação. | Consulta real depende do sistema proprietário. |
| SC-006 | Auditoria com pseudônimos; logs e spans capturados sem sentinelas sensíveis. | Configurar chave estável e operação externa. |
| SC-007 | Bloqueio temporário afeta somente a operação correspondente. | Estado em memória no adaptador local. |

## Parecer

O backend possui evidência automatizada para o escopo local. As pendências F2, F4, F5 e F6
impedem declarar a jornada completa ou a plataforma originalmente planejada como concluídas.
A Fase 6 produz instrumentação, testes e diagnóstico desses limites; não certifica produção.
