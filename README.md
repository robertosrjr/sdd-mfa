# MFA Step-Up

Backend de autorização de operações sensíveis com step-up, consentimento do titular,
atribuição de representantes, aprovação de uso único e idempotência durável.

**Stack atual:** Java 21, Spring Boot 3.4.2, Maven, Flyway e H2 local.

- [Execução e API](application/README.md)
- [Modelo de negócio e ofertas de serviço](docs/business/modelo-de-negocio.md)
- [Jornada do usuário](docs/journey/jornada-do-usuario.md)
- [Jornada técnica com cliente REST](docs/journey/jornada-tecnica-rest.md)
- [Arquitetura e fluxo](docs/architecture/README.md)
- [Testes e critérios de aceite](docs/testing/README.md)
- [Auditoria, métricas e tracing](docs/observability/README.md)
- [Especificações, tarefas e pendências](specs/README.md)

**Validação local:** 100 testes aprovados; 2 de PostgreSQL ignorados sem Docker.
Identidade, política e negócio ainda usam adaptadores simulados. Veja as
[pendências da auditoria](specs/001-step-up-transaction-approval/analyze.md#pendências-e-achados).
