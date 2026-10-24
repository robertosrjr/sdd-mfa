# Modelo de negócio — Aprovação de operações sensíveis

Data: 24/10/2026. Elaborado com a skill `service-modeling-skill` do projeto.
Este documento descreve o negócio pretendido; não declara disponibilidade em produção.

## 1. Contexto Estratégico & Objetivo de Negócio

O titular de um recurso precisa manter controle sobre operações sensíveis, financeiras ou
não, mesmo quando um representante prepara a solicitação. Verificar sua identidade não
demonstra, por si só, que ele tem permissão ou concordou com os dados da operação.

O objetivo é permitir que somente uma operação permitida e confirmada nas condições
exigidas seja executada, preservando evidências da decisão e evitando efeitos duplicados
quando houver repetição ou interrupção de comunicação.

| Parte interessada | Valor esperado |
|---|---|
| Titular ou responsável pelo recurso | Conhecer e controlar o efeito exato da operação. |
| Representante, inclusive agente de IA | Preparar operações com atribuição clara, respeitando os limites da delegação. |
| Área responsável pela operação | Receber uma decisão verificável antes de aplicar o efeito de negócio. |
| Risco e segurança | Aplicar exigências proporcionais à operação e impedir consentimento indevido. |
| Atendimento e auditoria | Esclarecer autoria, decisões e resultado sem exposição de credenciais. |

**Fluxo de valor:** expressar intenção → verificar identidade → avaliar permissão →
revisar e confirmar dados → executar → informar e, se necessário, esclarecer o resultado.
As exigências concretas de autenticação e aprovação são determinadas pela política.

O escopo inclui aprovação direta, aprovação de solicitação delegada e recuperação segura
do resultado. Não inclui cadastro, recuperação de conta, definição das políticas de risco
ou execução dos processos centrais de cada domínio de negócio.

## 2. Especificação do Serviço de Negócio (Business Service)

**Nome: Autorizar operações sensíveis.**

**Transformação de valor:** converter uma intenção de operação em uma decisão verificável
de prosseguir ou recusar, sustentada por identidade, permissão e consentimento específico
quando exigido. A execução pertence ao domínio responsável pela operação.

**Estabilidade:** a necessidade de controlar ações sensíveis permanece mesmo que mudem
os canais, os métodos de autenticação, os fornecedores ou os tipos de operação.

| Ator ou capacidade | Responsabilidade de negócio |
|---|---|
| Titular | Revisar os dados e aprovar, rejeitar ou cancelar quando sua decisão for exigida. |
| Representante | Preparar a solicitação e identificar em nome de quem atua. |
| Gestão de identidade | Fornecer evidência de identidade com força e atualidade suficientes. |
| Gestão de políticas | Determinar exigências e avaliar permissão para a ação e o recurso. |
| Gestão de consentimento | Vincular a decisão humana aos dados exatos, com validade e uso único. |
| Domínio executor | Aplicar o efeito autorizado e permitir esclarecer o resultado. |
| Operação e auditoria | Preservar evidências e tratar resultados incertos. |

Esses papéis descrevem responsabilidades; os responsáveis organizacionais nominais ainda
precisam ser definidos.

### Regras de negócio

| ID | Regra | Origem |
|---|---|---|
| RN-01 | Identidade, permissão e aprovação são decisões independentes; nenhuma substitui outra. | FR-001–FR-003 |
| RN-02 | O titular revisa os dados que alteram o efeito da operação; mudanças exigem nova aprovação. | FR-004–FR-006 |
| RN-03 | Cada aprovação vale somente para a operação correspondente, por prazo definido pela política e com um único consumo. | FR-007 |
| RN-04 | Recusa, cancelamento ou ausência do titular encerram o fluxo sem execução; não há aprovação pendente para outro momento. | FR-008 |
| RN-05 | A delegação identifica representante e titular; a decisão do representante não substitui a aprovação humana exigida. | FR-009–FR-010 |
| RN-06 | Repetir a mesma solicitação preserva o resultado original sem produzir novo efeito; resultado incerto exige verificação antes de qualquer nova execução. | FR-011–FR-012 |
| RN-07 | Evidências identificam os envolvidos e as decisões sem registrar credenciais, tokens ou dados pessoais desnecessários. | FR-013–FR-014 |
| RN-08 | Falhas repetidas limitam temporariamente as tentativas da operação, sem bloqueio automático da conta inteira. | FR-015 |

## 3. Detalhamento das Ofertas de Serviço (Service Offers)

O mesmo serviço sustenta as duas ofertas propostas abaixo. A recuperação de resultado
integra ambas. A nomenclatura das ofertas é uma proposta documental para validação de negócio.

| Parâmetro | Confirmação de operação pelo titular | Confirmação de operação preparada por representante |
|---|---|---|
| Público-alvo | Titular que inicia sua própria operação. | Titular auxiliado por representante ou agente de IA. |
| Serviço vinculado | Autorizar operações sensíveis. | Autorizar operações sensíveis. |
| Experiência | Revisar os dados, atender à verificação adicional quando exigida e decidir. | Identificar quem preparou a operação, revisar os dados e decidir pessoalmente quando exigido. |
| Canal de consumo pretendido | Canal confiável do titular, como aplicativo ou portal; escolha e implementação pendentes. | Canal do representante integrado ao canal confiável do titular, no mesmo fluxo interativo; implementação pendente. |
| Disponibilização atual | Backend de integração local; não inclui interface humana. | Backend local com atribuição de delegação; não inclui interface humana. |
| Parâmetros operacionais | Validade da aprovação, força e atualidade da autenticação e limite de tentativas definidos pela política. | Mesmos parâmetros, com verificação da atribuição e cancelamento se o titular não puder decidir no fluxo. |
| Garantia funcional requerida | Aprovação específica, de uso único; repetição segura e esclarecimento de resultado incerto. | Mesma garantia, sem autoaprovação do representante em lugar do titular. |
| SLA | Disponibilidade, latência, prazo de reconciliação e atendimento ainda não acordados. | Mesmos acordos pendentes; não há prazo de fila de aprovação porque não existe aprovação posterior. |
| Modelo comercial/financeiro | Não definido; documentar custeio e eventual cobrança antes da oferta operacional. | Não definido; avaliar também custos de integração e suporte à delegação. |

Indicadores propostos: taxa de conclusão, abandono durante autenticação/confirmação,
recusas por motivo, incidência e tempo de esclarecimento de resultados incertos e
ocorrências de efeitos duplicados. Metas operacionais e responsáveis ainda precisam de
acordo; os critérios SC-001–SC-007 da especificação são critérios de aceitação em testes,
não um SLA medido em produção.

## 4. Relatório de Obstáculos e Riscos (Obstacle Report)

| Obstáculo ou risco | Impacto | Mitigação ou decisão necessária |
|---|---|---|
| Tratar autenticação como aprovação | Executar algo que o titular não confirmou. | Preservar as três decisões e apresentar os dados materiais antes da confirmação. |
| Confundir canal técnico com serviço de negócio | Vincular o modelo a uma implementação. | Manter o serviço independente de canal e colocar canais, SLA e custos nas ofertas. |
| Artigo original contém aprovação pendente | Criar expectativa de retomada posterior incompatível com a especificação. | Adotar a especificação vigente: decisão no mesmo fluxo ou cancelamento. |
| Interface humana e emissão confiável de consentimento ausentes | Jornada completa ainda não demonstrada. | Implementar e validar apresentação, atribuição e coleta da decisão com o titular. |
| Identidade, política e execução simuladas | Validação local não comprova operação real. | Integrar fontes confiáveis e testar a jornada completa. |
| Resultado externo incerto | Dúvida do titular e risco de duplicação. | Consultar o resultado anterior; definir atendimento, responsáveis e prazo de esclarecimento. |
| Parâmetros e responsáveis não acordados | Oferta sem compromisso operacional claro. | Validar política, SLA, custeio, responsáveis e comunicação ao usuário antes da disponibilização. |
| Limites operacionais ainda abertos | Comportamento distribuído e persistência sem validação completa. | Tratar as pendências de banco, limitação de tentativas e operação da auditoria registradas na auditoria do projeto. |

Fontes: [especificação e critérios de aceite](../../specs/001-step-up-transaction-approval/spec.md),
[arquitetura e limites atuais](../architecture/README.md),
[auditoria da implementação](../../specs/001-step-up-transaction-approval/analyze.md).
Complemento: [jornada do usuário](../journey/jornada-do-usuario.md).
