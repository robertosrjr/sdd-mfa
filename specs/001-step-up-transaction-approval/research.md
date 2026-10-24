# Pesquisa e Decisões Técnicas: Aprovação de Operações Sensíveis com Step-Up

**Data**: 2026-10-22
**Escopo**: decisões de arquitetura e integração para o plano; nenhuma implementação foi
encontrada neste workspace.

## Contexto de Stack e Arquitetura

### Java e Spring

- **Decisão**: Usar Java 25, conforme solicitado pelo usuário, e Spring Boot 4.1.1 como
  baseline de planejamento. Preferir
  Spring MVC e processamento síncrono, sem adotar WebFlux sem evidência de necessidade
  de I/O altamente concorrente.
- **Racional**: As skills específicas do projeto orientam Spring Boot 4.1.1, REST, MVC e a
  estrutura arquitetural. A versão Java 25 foi definida pelo usuário, substituindo o
  exemplo Java 21 da skill de arquitetura. O workspace não contém manifesto de build ou
  código para verificar compatibilidade efetiva entre Java 25 e Spring Boot 4.1.1.
- **Alternativas consideradas**: Manter Java 21 conforme exemplo da skill ou adiar a versão;
  usar WebFlux por padrão. Java 25 foi escolhido por solicitação explícita. WebFlux não tem
  requisito de carga que justifique a complexidade adicional.
- **Verificação futura**: Confirmar runtime, versão efetiva do Spring Boot, build tool e
  compatibilidade de dependências no primeiro módulo de aplicação; confirmar ainda que CI
  e ambientes de execução suportam Java 25.

### Limites hexagonais

- **Decisão**: Isolar domínio, aplicação e infraestrutura. Modelos de domínio não dependem
  de Spring, HTTP ou persistência. Regras ficam em use cases e portas; adaptadores fornecem
  REST, persistência e integrações.
- **Racional**: Segue a skill local de arquitetura e mantém a lógica de autorização
  testável sem infraestrutura.
- **Alternativas consideradas**: Colocar regras em controllers ou em entidades JPA; isso
  acoplaria decisões de segurança à borda HTTP ou ao mecanismo de armazenamento.

## Decisões da Jornada de Segurança

### Step-up separado da autorização e do consentimento

- **Decisão**: Tratar força e atualidade da autenticação como evidência distinta da
  autorização de negócio e da aprovação do conteúdo da operação.
- **Racional**: OAuth Step-Up RFC 9470 define o desafio para a resource server expressar
  requisitos de autenticação, incluindo força (`acr_values`) e recência (`max_age`). O RFC
  descreve níveis como específicos à avaliação da resource server; não estabelece uma
  hierarquia universal de métodos nem substitui autorização de negócio ou consentimento.
- **Alternativas consideradas**: Considerar a conclusão do MFA como aprovação suficiente;
  isso permite reutilizar uma sessão autenticada para uma operação que o titular não
  aprovou.
- **Fonte**: [RFC 9470, seção 2](https://www.rfc-editor.org/rfc/rfc9470#section-2).

### Integridade da intenção aprovada

- **Decisão**: Canonicalizar os dados materiais da operação, versionar a intenção e
  vincular a evidência de aprovação ao digest da intenção. Revalidar a mesma versão
  imediatamente antes da execução e exigir nova aprovação caso haja mudança.
- **Racional**: A orientação OWASP para autorização transacional recomenda que os dados
  sejam apresentados ao usuário, que o fluxo não permita sua alteração sem nova
  autorização, e que a execução permaneça ligada à operação autorizada. A canonicalização
  evita que diferenças de ordenação ou representação alterem o significado comparado.
- **Alternativas consideradas**: Vincular aprovação somente ao usuário ou à sessão; isso
  não identifica o que foi aprovado. Armazenar a cópia completa de todo payload em logs ou
  evidência; isso aumenta a exposição de dados sem ser necessário para provar integridade.
- **Fonte**: [OWASP Transaction Authorization Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Transaction_Authorization_Cheat_Sheet.html).

### Aprovação síncrona, uso único e delegação

- **Decisão**: Recolher a decisão humana durante o fluxo interativo e não manter operação
  aguardando aprovação posterior. Registrar sujeito e agente separadamente, e consumir a
  evidência de aprovação uma única vez.
- **Racional**: Reflete a decisão esclarecida pelo usuário e os princípios da constituição
  para autorização explícita, delegação atribuível e aprovação ligada à intenção exata.
- **Alternativas consideradas**: Aprovação assíncrona ou uma fila de aprovação; isso
  contraria o requisito esclarecido e amplia estados e comportamento de retomada.

### Idempotência e resultado externo incerto

- **Decisão**: Persistir a identidade e o estado de cada tentativa com restrição de
  unicidade, fingerprint da requisição e resultado reproduzível. Para operações externas,
  exigir suporte idempotente ou de consulta/reconciliação do serviço de negócio. Não
  repetir uma escrita externa quando seu resultado é desconhecido.
- **Racional**: As skills locais de idempotência e resiliência prescrevem lock/registro
  persistente para concorrência, comparação de fingerprint e proíbem retry cego de
  escrita financeira. Uma transação local não torna atômica uma operação distribuída:
  portanto o sistema não promete "exactly once" entre serviços; ele precisa coordenar
  repetição segura e reconciliação com o serviço que aplica o efeito.
- **Alternativas consideradas**: Estado apenas em memória/ cache volátil; perde unicidade
  entre instâncias ou reinícios. Retry automático em timeout; pode duplicar o efeito
  externo. Garantia "exactly once" sem cooperação downstream; não é demonstrável sob falha
  de rede.
- **Fonte de protocolo**: [RFC 9110, seção 9.2.2](https://www.rfc-editor.org/rfc/rfc9110#section-9.2.2)
  define idempotência semântica para métodos HTTP, mas não fornece, por si só, o protocolo
  de chave de idempotência da aplicação.

### Erros, observabilidade e privacidade

- **Decisão**: Usar Problem Details (RFC 9457) para erros HTTP; aplicar timeouts nas
  fronteiras externas; repetir apenas operações que sejam seguras/idempotentes; registrar
  decisões com campos técnicos limitados e omitir payloads, segredos e identificadores
  pessoais de logs, métricas e spans.
- **Racional**: Segue as skills locais de API, resiliência, observabilidade, logging,
  métricas e LGPD. O desenho mantém a resposta de erro consistente sem revelar detalhes
  internos ou credenciais.
- **Alternativas consideradas**: Expor exceções ou detalhes do provedor ao cliente;
  registrar payload para diagnóstico; essas opções aumentam vazamento de dados e
  acoplamento do contrato.
- **Fontes**: [RFC 9457](https://www.rfc-editor.org/rfc/rfc9457.html);
  `.claude/skills/resilience-checker-skill/SKILL.md`;
  `.claude/skills/spring-logging-skill/SKILL.md`;
  `.claude/skills/spring-metrics-skill/SKILL.md`;
  `.claude/skills/spring-tracing-skill/SKILL.md`;
  `.claude/skills/lgpd-sre-compliance-skill/SKILL.md`.

## Decisões de Persistência e Operação

- **Decisão**: Usar persistência relacional durável como baseline para consumo atômico da
  aprovação, controle de concorrência e registro de tentativas. Banco, isolamento,
  política de retenção e migração ficam para serem confirmados quando o módulo real existir.
- **Racional**: As invariantes exigem unicidade, transições condicionais e sobrevivência a
  reinício. A orientação de arquitetura pede ports para isolar a tecnologia de persistência.
- **Alternativas consideradas**: Cache/in-memory como fonte de verdade; não fornece
  durabilidade nem coordenação suficiente entre instâncias. Redis isolado; pode ser uma
  solução futura se demonstrada como store durável e consistente, mas não há evidência de
  que exista no projeto.

- **Decisão**: Definir métricas e SLOs como trabalho de operação antes de produção, após
  medir carga e consultar requisitos do serviço. Não inventar latência, disponibilidade,
  retenção ou volume alvo.
- **Racional**: O spec e o workspace não dão carga, disponibilidade ou orçamento de
  latência. Um alvo numérico não medido seria uma falsa garantia.
- **Alternativas consideradas**: Definir limites arbitrários nesta fase; não há base para
  comprovar que seriam úteis ou alcançáveis.

## Referências do Projeto

- `.claude/skills/architecture-guidance/SKILL.md`
- `.claude/skills/api-design-guidance/SKILL.md`
- `.claude/skills/testing-strategy-guidance/SKILL.md`
- `.claude/skills/idempotency-pattern-skill/idempotency-pattern-skill.md`
- `.claude/skills/resilience-checker-skill/SKILL.md`
- `.claude/skills/platform-practices-guidance/SKILL.md`
