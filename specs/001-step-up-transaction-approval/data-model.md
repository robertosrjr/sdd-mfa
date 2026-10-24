# Modelo de Dados: Aprovação de Operações Sensíveis com Step-Up

Este é um modelo conceitual. Os adaptadores devem mapear os conceitos para o domínio
existente da operação; este recurso não passa a ser dono dos dados de negócio.

## Entidades

### Autorização de Operação

Representa uma tentativa de autorizar uma operação sensível específica.

| Campo | Significado | Regra |
|---|---|---|
| `authorizationId` | Identidade da tentativa | Única; não reutilizada entre operações |
| `operationId` | Referência à operação no domínio de negócio | Obrigatória; o serviço de negócio permanece dono do recurso |
| `operationType` | Tipo estável da operação | Obrigatório; determina como os dados materiais são descritos |
| `subjectId` | Titular ou responsável em cujo nome a operação ocorre | Derivado da identidade autenticada; não aceito como identidade confiável apenas do payload |
| `actorId` | Agente/representante iniciador | Opcional; obrigatório quando a operação é delegada |
| `intentVersion` | Versão/concorrência dos dados materiais | Deve continuar igual até o consumo da aprovação |
| `intentDigest` | Digest da representação canônica dos dados aprovados | Vinculado à aprovação; recalculado ou verificado antes da execução |
| `policyVersion` | Versão da decisão de política aplicável | Preservada para auditoria e revalidação |
| `state` | Estado da jornada de autorização/execução | Transições definidas abaixo; sem estado de aprovação humana pendente |
| `createdAt`, `expiresAt`, `completedAt` | Tempos da tentativa | UTC; validade determinada pela política |

**Relações**: Uma autorização referencia um titular, pode referenciar um agente, contém
um contexto de autenticação, uma avaliação de política, uma decisão de aprovação e uma
ou mais tentativas de execução. Referências a identidades são identificadores técnicos
minimizados e protegidos; detalhes completos permanecem nos sistemas proprietários.

### Evidência de Autenticação

Evidência de autenticação usada para avaliar a solicitação. Contém a classe/força e o
instante de autenticação, ou uma referência verificável ao evento. Não armazena credenciais,
tokens ou segredo de sessão. A evidência precisa satisfazer os requisitos de força e
atualidade definidos pela política para o tipo de operação.

### Decisão de Autorização

Resultado independente da avaliação de permissões do titular para a ação e o recurso.
Inclui decisão (`ALLOW`/`DENY`), referência/versão da política, categoria de motivo e
instante. `ALLOW` não substitui aprovação transacional; `DENY` impede execução.

### Aprovação Transacional

Registra a decisão explícita do titular sobre uma intenção específica.

| Campo | Significado | Regra |
|---|---|---|
| `authorizationId` | Autorização a que pertence | Obrigatório |
| `subjectId` | Titular que decidiu | Deve corresponder ao sujeito autorizado |
| `actorId` | Representante apresentado ao titular | Deve coincidir com a atribuição da autorização; ausente apenas em operação direta |
| `approverId` | Identidade verificada de quem decidiu | Deve ser o titular, nunca o agente |
| `humanConfirmed` | Evidência de consentimento humano na interação confiável | Obrigatória; não aceita como prova no payload de execução |
| `intentDigest` | Intenção exata apresentada | Deve coincidir no instante da execução |
| `decision` | `APPROVED`, `REJECTED` ou `CANCELLED` | Final no fluxo interativo |
| `decidedAt`, `expiresAt` | Tempos da decisão | Uma aprovação expirada não pode ser consumida |
| `consumedAt` | Instante de consumo da aprovação | Atualização atômica; no máximo uma execução |

Não existe aprovação com estado durável `PENDING_USER`. Indisponibilidade, recusa ou
timeout do titular encerra a tentativa como `CANCELLED` ou `REJECTED`.

### Tentativa de Execução / Registro de Idempotência

Controla requisições repetidas, concorrentes e resultado do serviço de negócio.

| Campo | Significado | Regra |
|---|---|---|
| `scope` | Cliente/sujeito e tipo de operação | Isola chaves entre consumidores |
| `keyHash` | Representação protegida da chave de idempotência | Única dentro do escopo |
| `requestFingerprint` | Digest da requisição canônica | Reuso com fingerprint diferente é conflito |
| `state` | `IN_PROGRESS`, `SUCCEEDED`, `FAILED`, `OUTCOME_UNKNOWN` ou `RECONCILING` | Estado persistente e coordenado entre instâncias |
| `operationId` | Operação de negócio associada | Obrigatória |
| `businessResultRef` | Referência ao resultado confirmado no sistema de negócio | Não duplicar payload sensível |
| `createdAt`, `updatedAt`, `expiresAt` | Ciclo de vida da chave | Retenção definida por política operacional/negocial |
| `authorizationId` | Tentativa apresentada ao titular | Preservada para recuperação da execução |
| `ownerToken` | Identifica o executor/reconciliador atual | Atualização condicional impede sobrescrita por respostas antigas |
| `leaseUntil` | Prazo de propriedade ativa | Após vencimento permite somente reconciliação, nunca nova execução |

Uma chave no estado `IN_PROGRESS` não autoriza iniciar uma segunda execução. Em `SUCCEEDED`
ou `FAILED`, uma repetição equivalente recebe o resultado terminal associado. Em
`OUTCOME_UNKNOWN`, consulta ou reconciliação precede qualquer nova escrita.

### Evento de Auditoria

Evento append-only para a decisão ou transição relevante: autorização iniciada,
autenticação satisfeita/recusada, política permitiu/negou, aprovação/rejeição, consumo,
execução confirmada ou resultado incerto/reconciliado. Registra timestamp, referências
técnicas minimizadas para sujeito/agente/operação, tipo de decisão, estado, versão de
política e correlação. Não registra credenciais, tokens, payload integral, valores
financeiros, nem PII desnecessária.

O adaptador persiste referências HMAC-SHA256 nos campos de identificação de evento,
titular, ator, operação, política e correlação. Tipo e decisão usam vocabulário limitado;
`details` permanece nulo. A chave estável é fornecida por `MFA_AUDIT_PSEUDONYM_KEY`;
sem configuração há somente correlação local por processo. Os objetos de aplicação
podem transportar identificadores técnicos transitoriamente; os logs e a tabela de
auditoria recebem as referências protegidas.

## Ciclo de Vida

```text
REQUESTED
  ├── AUTHENTICATION_REQUIRED ──> AUTHENTICATED
  ├── DENIED
  └── CANCELLED
AUTHENTICATED
  ├── POLICY_AUTHORIZED
  ├── DENIED
  └── CANCELLED
POLICY_AUTHORIZED
  ├── USER_APPROVED
  ├── REJECTED
  └── CANCELLED
USER_APPROVED
  ├── EXECUTING
  ├── CANCELLED                 (intenção mudou antes do consumo)
  └── EXPIRED
EXECUTING
  ├── EXECUTED
  ├── EXECUTION_FAILED
  └── OUTCOME_UNKNOWN ──> RECONCILING ──> EXECUTED | EXECUTION_FAILED
```

O desenho não mantém uma operação aguardando uma resposta humana posterior. `EXECUTING`,
`OUTCOME_UNKNOWN` e `RECONCILING` são estados técnicos da execução, não estados de aprovação
pendente. Transições para `EXECUTED` exigem aprovação válida, autorização atual e consumo
atômico da evidência.

## Invariantes e Validação

1. O titular, o agente opcional e a operação permanecem identificáveis sem confundir ator
   com sujeito.
2. Força/atualidade de autenticação, decisão de autorização e aprovação transacional são
   evidências separadas.
3. A aprovação só vale para a operação e o digest/versionamento da intenção apresentados.
4. Revalidação de intenção e consumo da aprovação devem ser atômicos relativamente à
   mudança concorrente do recurso.
5. A unicidade da chave de idempotência é globalmente consistente no seu escopo durante
   toda a janela de retenção.
6. Nenhum retry externo ocorre enquanto o resultado anterior estiver incerto, a menos que
   o serviço de negócio prove idempotência da mesma operação/chave.
7. O encerramento por recusa, cancelamento, expiração, bloqueio temporário ou falha de
   autorização nunca pode produzir efeito no serviço de negócio.
8. Retenção de evidência e idempotência deve respeitar a política de privacidade e auditoria
   aplicável, definida antes da implantação.
