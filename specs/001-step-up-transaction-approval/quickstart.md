# Guia de Validação: Aprovação de Operações Sensíveis com Step-Up

O módulo executável está em `application/`, com Java 21 e Spring Boot 3.4.2 no `pom.xml`.
As Fases 1–6 possuem implementação e testes locais. O alinhamento à plataforma original
(T001: Java 25 / Spring Boot 4) permanece pendente. Há testes de integração com H2 e uma variante
de concorrência com PostgreSQL/Testcontainers, executada somente quando Docker está disponível.

## Pré-requisitos

- JDK compatível com a compilação `release 21` e Maven instalado.
- Banco relacional selecionado para as transições atômicas, chaves de idempotência e
  evidência durável.
- Adaptadores de teste ou ambientes controlados para identidade, política e serviço de
  negócio.
- Testcontainers ou tecnologia equivalente para testar concorrência e persistência com o
  mesmo mecanismo usado em produção.
- Decisão operacional documentada sobre retenção, limite de tentativas e métricas/SLO antes
  da produção.

## Estratégia de Teste

Siga a pirâmide indicada em
`.claude/skills/testing-strategy-guidance/SKILL.md`: maioria de testes unitários puros para
domínio e casos de uso, integração para persistência/adaptadores e poucos testes
end-to-end para jornadas críticas.

Execute na raiz do workspace:

```powershell
cd application
mvn test
# Apenas a Fase 4:
mvn "-Dtest=DelegationAttributionTest,DelegatedOperationContractTest,DelegatedOperationIntegrationTest" test
```

## Fluxo delegado implementado (T020–T025)

O fluxo confiável de aprovação deve apresentar ao titular `subjectId`, `actorId` e os
dados materiais antes da decisão. Ele registra a autorização e a aprovação no banco.
O endpoint de execução não cria consentimento nem recebe `approverId` ou
`humanConfirmed` como prova de aprovação.

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

Envie esse corpo a `POST /api/v1/operation-authorizations`, após registrar consentimento
confiável. `delegated` pode ser omitido para compatibilidade: nesse caso a presença de
`actorId` indica delegação. Quando informado, deve ser consistente com `actorId`.
Omissão ou troca do ator em relação ao registro persistido é recusada.

A migração `V2__delegated_approval_evidence.sql` adiciona à aprovação `actor_id`,
`approver_id` e `human_confirmed`. O aprovador deve ser o titular humano; o ator da
aprovação deve coincidir com o da autorização. Registros antigos sem essa evidência
precisam de nova aprovação. A evidência também é vinculada a operação, tipo, versão,
digest e validade, e consumida por atualização condicional atômica.

O digest da intenção agora usa a codificação `mfa-intent-v2`: valores tipados e tamanhos
explícitos, objetos ordenados recursivamente e listas com ordem preservada. Campos nulos,
espaços e diferenças de tipo são materiais. A interação confiável deve usar o mesmo
`IntentCanonicalizer`; aprovações produzidas pelo algoritmo antigo exigem nova aprovação.

O resultado apresenta `subjectId` e `actorId` provenientes do registro validado.
Falhas de validação não consomem consentimento nem chamam o serviço de negócio. A auditoria das
decisões usa uma transação independente para sobreviver ao rollback da solicitação.

O módulo ainda não fornece interface de apresentação/coleta de consentimento nem
integração real com provedor de identidade. `DefaultAdaptersConfig` contém adaptadores
locais simulados. Identidades do payload são alegações conferidas contra o registro;
a vinculação delas ao principal autenticado deve ser feita na integração real.

## Idempotência e reconciliação (Fase 5)

Envie `Idempotency-Key` com 16 a 255 caracteres ASCII visíveis, sem espaços, em todo
POST de autorização. Escolha uma chave aleatória de alta entropia e preserve-a nos retries.
Ausência ou formato inválido retorna HTTP 400. O escopo combina as identidades técnicas
do titular/ator e a operação/tipo; a integração real deve vinculá-las ao cliente autenticado.
O banco armazena hashes da chave e do escopo, fingerprint e resposta mínima, sem payload material.

- Mesmo escopo, chave e conteúdo: o resultado terminal é reproduzido sem nova execução.
- Mesma chave com conteúdo diferente: HTTP 409, inclusive mudança de aprovação ou versão.
- Uma execução ou reconciliação ainda ativa: HTTP 409.
- Timeout ou exceção após o envio ao serviço de negócio: HTTP 202 e `OUTCOME_UNKNOWN`.
- Retry desse resultado: consulta o serviço de negócio, sem chamar novamente a execução.
- Resultado externo ainda incerto: continua HTTP 202; resultado confirmado: HTTP 200.

Autenticação e política são verificadas novamente antes de replay ou reconciliação.
Falhas de autenticação/política/aprovação não reservam a chave nem consomem aprovação.
A reserva, a aprovação consumida e o estado `EXECUTING` são confirmados numa transação
curta antes do envio externo. A finalização usa outra transação. Uma interrupção entre
essas etapas mantém o registro recuperável, mesmo que o cliente não tenha recebido resposta.

Configuração em `application/src/main/resources/application.yml`:

```yaml
mfa:
  idempotency:
    retention: PT24H
    lease: PT30S
```

`lease` determina quando uma execução/reconciliação interrompida pode ser consultada por
outro solicitante. Um token de propriedade impede que uma resposta antiga sobrescreva o
resultado de um novo reconciliador. O vencimento da lease permite somente consulta,
nunca novo envio de execução. Configure a lease considerando os timeouts dos adaptadores.

`retention` estabelece o instante mínimo para uma futura política de limpeza de registros
terminais. Não há limpeza automática: registros expirados continuam protegendo o replay.
Registros ativos ou incertos não devem ser excluídos pela rotina operacional.

`BusinessOperationReconciliationPort` deve consultar a operação no sistema proprietário.
Somente uma confirmação definitiva permite retornar sucesso/falha terminal; operação ainda
ausente, pendente ou indisponível deve retornar resultado incerto. O adaptador local retorna
incerto por padrão. O fluxo não tenta reenviar uma execução para resolver incerteza.

```powershell
cd application
mvn "-Dtest=IdempotencyRecordTest,RequestFingerprinterTest,IdempotencyHeaderContractTest,ConcurrentExecutionTest,IdempotencyReconciliationIntegrationTest" test
# Com Docker em execução, verifica também PostgreSQL 16:
mvn "-Dtest=PostgreSQLConcurrentExecutionTest" test
```

Sem Docker, o teste PostgreSQL é marcado como ignorado; isso não equivale à validação no
banco de produção. Os testes H2 cobrem concorrência, aprovação confirmada antes do envio,
replay persistido, timeout, recuperação de interrupção e proteção contra respostas antigas.

## Cenários de Aceitação

### Autenticação, autorização e aprovação independentes

1. Envie uma operação que exige maior força ou recência de autenticação usando uma sessão
   insuficiente.
2. Verifique que a resposta solicita step-up segundo a política e que a operação não foi
   executada.
3. Repita com autenticação suficiente, mas política negada; confirme recusa sem execução.
4. Repita com política permitida e aprovação válida do titular, ligada à intenção apresentada;
   confirme uma única execução.

### Integridade da intenção e delegação

1. Prepare operação com titular, tipo, versão e campos materiais.
2. Confirme que a interface apresenta todos os campos materiais aplicáveis e a atribuição do
   ator quando a operação é delegada.
3. Aprove a intenção e altere um campo material antes do consumo.
4. Confirme que o digest/versão diverge, a aprovação é recusada e nenhuma execução ocorre.
5. Confirme que um agente não consegue fornecer aprovação em lugar do titular quando a
   política exige aprovação humana.

### Idempotência, concorrência e reconciliação

1. Envie a mesma execução com a mesma chave e o mesmo fingerprint duas vezes.
2. Confirme que a repetição retorna o resultado original sem novo efeito de negócio.
3. Reutilize a chave com conteúdo diferente e confirme rejeição de conflito.
4. Execute duas requisições concorrentes com a mesma aprovação/chave; confirme que no máximo
   uma chama o serviço de negócio.
5. Simule timeout depois do envio ao serviço de negócio, com resultado externo desconhecido.
6. Confirme que a próxima tentativa consulta/reconcilia primeiro e não repete uma escrita
   insegura.

### Limite de tentativas e privacidade

1. Ultrapasse o limite de falhas definido pela política; confirme limitação temporária de
   novas tentativas para a operação sem bloqueio automático da conta inteira.
2. Inspecione logs, métricas e spans dos cenários: não podem conter tokens, credenciais,
   dados materiais integrais, valores financeiros, identificadores pessoais ou labels de
   alta cardinalidade.
3. Confirme que os eventos de auditoria mantêm a decisão e as referências necessárias sem
   dados sensíveis desnecessários.

## Validação da Fase 6

```powershell
cd application
mvn "-Dtest=AuditPrivacyComplianceTest,AuthorizationObservabilityIntegrationTest,AuthorizationAcceptanceTest" test
```

`AuditPrivacyComplianceTest` captura logs e simula erro JDBC. `AuthorizationObservabilityIntegrationTest`
captura spans reais do SDK OpenTelemetry, lê Prometheus e verifica rótulos limitados, replay,
cabeçalhos, corpo, URL e mensagens/causas de erros. `AuthorizationAcceptanceTest` inicia um
servidor HTTP real, com dependências externas controladas, para verificar 200/202/401/403/409/429
e rejeição de intenção/aprovação inválida.

Última suíte completa: 100 aprovados, zero falhas/erros e 2 PostgreSQL ignorados sem Docker.
Limites e rastreabilidade: [analyze.md](analyze.md). A interface humana e os provedores reais
não são validados por essa suíte local.

A auditoria agora grava referências HMAC em vez de identificadores crus e omite texto livre.
Configure `MFA_AUDIT_PSEUDONYM_KEY` com segredo Base64 de pelo menos 32 bytes para manter
correlação entre reinícios. Sem configuração, a chave é aleatória por processo, para uso local.

## Resultado Esperado

Todos os caminhos sem evidência válida de autenticação exigida, autorização ou aprovação
explícita terminam sem executar a operação. Mudanças de intenção invalidam a aprovação,
repetições não duplicam efeitos e resultados externos incertos são reconciliados antes de
qualquer nova escrita.

Detalhes de estados e invariantes: [data-model.md](data-model.md). Contrato HTTP de
referência: [operation-authorization.openapi.yaml](contracts/operation-authorization.openapi.yaml).
