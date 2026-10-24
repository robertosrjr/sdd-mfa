# Especificações e andamento

A funcionalidade está organizada em [001-step-up-transaction-approval](001-step-up-transaction-approval/).

| Documento | Conteúdo |
|---|---|
| [Especificação](001-step-up-transaction-approval/spec.md) | Histórias e requisitos FR-001–FR-015. |
| [Plano](001-step-up-transaction-approval/plan.md) | Arquitetura e plataforma planejada/implementada. |
| [Modelo de dados](001-step-up-transaction-approval/data-model.md) | Entidades, estados e invariantes. |
| [Contrato HTTP](001-step-up-transaction-approval/contracts/operation-authorization.openapi.yaml) | Payloads e respostas da API. |
| [Tarefas](001-step-up-transaction-approval/tasks.md) | Andamento T001–T037. |
| [Guia de validação](001-step-up-transaction-approval/quickstart.md) | Comandos e critérios de aceite. |
| [Auditoria](001-step-up-transaction-approval/analyze.md) | Evidências, matriz bidirecional e limites conhecidos. |

A Fase 6 foi validada localmente. Permanecem abertas T001 (alvo original Java 25 / Spring
Boot 4, divergente do build atual) e T028 (execução PostgreSQL com Docker), além das integrações
reais de identidade, consentimento humano, política e negócio. A suíte local não certifica produção.
