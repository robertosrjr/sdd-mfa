# Testes e critérios de aceite

Execute a partir de `application/`:

```powershell
mvn clean test
```

Última validação completa: **102 testes descobertos, 100 aprovados, zero falhas/erros,
2 PostgreSQL ignorados sem Docker**. `mvn -o` também pode ser usado quando todas as
dependências já estão no cache local.

## Cobertura

| Grupo | Verificações |
|---|---|
| Domínio | Autenticação, intenção canônica, atribuição, aprovação de uso único e estados de idempotência. |
| Contrato HTTP | DTOs, cabeçalhos, respostas de erro e desafio de step-up. |
| Integração H2 | Consumo atômico, concorrência, replay durável, timeout, recuperação e fencing. |
| Privacidade | Logs e auditoria sem sentinelas sensíveis; erro JDBC sem mensagens/causas. |
| Observabilidade | Spans reais do SDK OpenTelemetry capturados em memória; Prometheus e rótulos limitados. |
| Aceitação | Servidor HTTP real com adaptadores externos controlados. |
| Arquitetura | ArchUnit para isolamento de domínio e direção das dependências. |

## Executar por tema

```powershell
# Privacidade, spans, métricas e aceitação HTTP
mvn "-Dtest=AuditPrivacyComplianceTest,AuthorizationObservabilityIntegrationTest,AuthorizationAcceptanceTest" test

# Concorrência e recuperação de resultado
mvn "-Dtest=ConcurrentExecutionTest,IdempotencyReconciliationIntegrationTest" test

# PostgreSQL 16; requer Docker em execução
mvn "-Dtest=PostgreSQLConcurrentExecutionTest" test
```

Sem Docker, a suíte PostgreSQL é ignorada. Esses dois testes continuam pendentes na T028;
o resultado H2 não comprova o comportamento do banco de produção.

## Evidência e limites

Relatórios Maven: `application/target/surefire-reports/`. São arquivos gerados e podem ser
substituídos por novas execuções. A suíte não valida interface humana, identidade de um
chamador real ou serviços de negócio externos. Não define SLO de latência ou throughput.

Use a compilação configurada no Maven. Se o editor gerar classes Java 25 em `target/classes`,
execute `mvn clean test` para recompilar em `release 21`. O teste de falha JDBC usa uma
implementação controlada, sem depender de instrumentação Mockito de classes JDK 25.

[Cenários de aceite](../../specs/001-step-up-transaction-approval/quickstart.md) ·
[Matriz requisito ↔ tarefa ↔ teste](../../specs/001-step-up-transaction-approval/analyze.md)
