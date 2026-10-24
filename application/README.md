# Execução e API

## Requisitos

- JDK 21 ou superior, com compilação configurada em `release 21`.
- Maven instalado; não há Maven Wrapper no projeto.
- Docker somente para os testes PostgreSQL/Testcontainers.

O build usa Spring Boot 3.4.2. A última validação foi executada com JDK 25, compilando
para Java 21. O alvo original Java 25 / Spring Boot 4 continua registrado na auditoria.

## Executar localmente

Na raiz do repositório:

```powershell
cd application
mvn spring-boot:run
```

Aplicação em `http://localhost:8080`. Verifique a disponibilidade em
`http://localhost:8080/actuator/health`.

O padrão usa H2 em memória e Flyway. Dados são perdidos ao encerrar o processo.
`DefaultAdaptersConfig` fornece identidade, política e execução simuladas. Isso não
constitui autenticação de um chamador real ou autorização para operações de produção.

## Autorizar uma operação

`POST /api/v1/operation-authorizations`

Cabeçalhos:

| Cabeçalho | Uso |
|---|---|
| `Idempotency-Key` | Obrigatório: 16–255 caracteres ASCII visíveis, sem espaços; use chave aleatória e preserve-a nos retries. |
| `X-Correlation-Id` | Opcional; a auditoria registra uma referência protegida. |

```json
{
  "subjectId": "owner",
  "actorId": "agent",
  "delegated": true,
  "operationId": "op",
  "operationType": "TRANSFER",
  "intentVersion": 1,
  "materialData": { "amount": "10", "currency": "BRL" },
  "approvalId": "approval"
}
```

A autorização e a aprovação já precisam existir, emitidas pela interação confiável do
titular. A API não cria consentimento e uma instalação vazia não executa esse exemplo.
Os testes preparam esses registros em ambiente controlado. `actorId` pode ser omitido em
operações diretas; quando delegado, deve coincidir com a evidência persistida.

| HTTP | Significado |
|---|---|
| 200 | Resultado confirmado, inclusive falha de negócio, ou replay de resultado terminal. Consulte `state` e `executed`. |
| 202 | Resultado externo incerto; repita com a mesma chave para consultar/reconciliar. |
| 400 | Cabeçalho, payload, atribuição, intenção ou aprovação inválidos. |
| 401 | Step-up necessário, com desafio `WWW-Authenticate`. |
| 403 | Política negada. |
| 409 | Chave usada com conteúdo diferente ou execução/reconciliação ativa. |
| 429 | Bloqueio temporário por operação, com `Retry-After`. |
| 500 | Falha interna com mensagem genérica. |

Detalhes: [contrato OpenAPI](../specs/001-step-up-transaction-approval/contracts/operation-authorization.openapi.yaml).
Identificadores no payload são alegações; o vínculo ao principal autenticado depende da integração real.

## Configuração

| Configuração | Padrão |
|---|---|
| `server.port` | `8080` |
| `mfa.idempotency.retention` | `PT24H`; não há limpeza automática. |
| `mfa.idempotency.lease` | `PT30S`; vencimento permite consulta, nunca reenvio da execução. |
| `mfa.stepup.rate-limit.max-failures` | `3` |
| `mfa.stepup.rate-limit.window-seconds` | `300` |
| `mfa.stepup.rate-limit.lockout-seconds` | `900` |
| `MFA_AUDIT_PSEUDONYM_KEY` | Ausente: chave aleatória por processo; configure segredo estável para auditoria durável. |

Veja [testes](../docs/testing/README.md), [arquitetura](../docs/architecture/README.md) e
[observabilidade](../docs/observability/README.md).
