# Auditoria, métricas e tracing

## Auditoria

`LoggingAuditAdapter` registra categorias de evento/decisão e referências HMAC-SHA256
para evento, titular, representante, operação, política e correlação. O mesmo formato
protegido é persistido em `audit_events`. `details` fica nulo: texto livre não é emitido.
Mensagens, causas e parâmetros de exceções JDBC também não são registrados.

As colunas de identificação da tabela agora contêm referências protegidas, não os IDs
originais. Para localizar eventos por um identificador conhecido, um componente autorizado
pode calcular `AuditPrivacyPolicy.reference(id)` com a mesma chave. Não há endpoint público
de resolução. Pseudonimização não equivale a anonimização.

Configure **`MFA_AUDIT_PSEUDONYM_KEY`** com segredo Base64 de pelo menos 32 bytes.
Ela deve ser estável e mantida fora do repositório para correlacionar eventos entre reinícios.
Sem configuração, o modo local gera uma chave aleatória por processo; referências anteriores
não podem ser recalculadas com uma chave diferente. Rotação exige gestão das versões das chaves.

A auditoria usa transação independente para preservar decisões quando a autorização sofre
rollback. Se a persistência falhar, é emitida falha genérica com referência protegida;
permissões de banco, retenção e coleta externa são responsabilidades da implantação.

## Métricas

Endpoints locais: `/actuator/health`, `/actuator/metrics` e `/actuator/prometheus`.

| Micrometer | Prometheus | Uso |
|---|---|---|
| `authorization.decision` | `authorization_decision_total` | Respostas do caso de uso, incluindo replay. |
| `authorization.duration` | `authorization_duration_seconds_*` | Contagem e tempo das respostas do caso de uso. |

Rótulos próprios: `decision` e `operation`. Decisões usam 11 categorias fixas; operação é
`TRANSFER`, `PIX_TRANSFER`, `FUNDS_TRANSFER` ou `OTHER`. Um `MeterFilter` rejeita categorias
ou rótulos extras para essas métricas. IDs, chaves, fingerprints e correlação não viram labels.
O rótulo comum `application` vem da configuração do serviço.

O contador mede respostas, não quantidade de escritas no negócio. Uma resposta reproduzida
incrementa o contador sem executar novamente. Validações HTTP anteriores ao caso de uso
podem ser acompanhadas pelas métricas HTTP padrão.

## Tracing e erros

O bridge Micrometer/OpenTelemetry cria spans `operation.authorization` com categorias
limitadas. A instrumentação HTTP usa nome fixo, omite atributos de alta cardinalidade/URL
e substitui erros por uma exceção genérica sem causa ou stacktrace antes de registrá-los.
Baggage está desabilitado; corpos e cabeçalhos não são adicionados aos spans.

Handlers retornam detalhes genéricos, limitam metadados de política e não registram mensagens
de provedores ou causas. Logs SQL e bind de parâmetros estão desabilitados na configuração.
Não há exportador externo de tracing configurado. Os testes usam SDK real e exportador em memória.

[Testes de privacidade e observabilidade](../testing/README.md) ·
[Código da configuração](../../application/src/main/java/com/mfa/stepup/infrastructure/config/MetricsConfig.java)
