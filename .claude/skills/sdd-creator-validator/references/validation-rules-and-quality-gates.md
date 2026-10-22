# Regras de Validação & Quality Gates (SDD Audit)

Este guia fornece os critérios formais para auditar e validar conjuntos de especificações SDD antes que o agente de IA inicie a fase de código.

---

## 1. Matriz de Auditoria da Especificação (`spec.md`)

Ao auditar uma `spec.md`, verifique os seguintes critérios:

| Item | Critério de Qualidade | Status Exigido | Ação em Caso de Falha |
| :--- | :--- | :--- | :--- |
| **1.1** | O escopo do problema está definido do ponto de vista do usuário final? | OBRIGATÓRIO | Reescrever focando na dor/valor do negócio. |
| **1.2** | Cada História de Usuário tem pelo menos 1 Critério de Aceite testável? | OBRIGATÓRIO | Adicionar estrutura GIVEN/WHEN/THEN. |
| **1.3** | O documento possui uma seção explícita de **Fora de Escopo (*Out of Scope*)**? | OBRIGATÓRIO | **REPROVADO**. Adicionar itens fora de escopo para evitar *scope creep*. |
| **1.4** | A especificação evita termos técnicos de implementação (banco de dados, frameworks)? | OBRIGATÓRIO | Mover detalhes técnicos para o `plan.md`. |
| **1.5** | Pelo menos 2 Casos de Borda (*Edge Cases*) foram mapeados? | OBRIGATÓRIO | Mapear cenários de erro e timeout. |

---

## 2. Matriz de Auditoria do Plano Técnico (`plan.md`)

| Item | Critério de Qualidade | Status Exigido | Ação em Caso de Falha |
| :--- | :--- | :--- | :--- |
| **2.1** | A solução proposta respeita 100% dos guardrails do `constitution.md`? | CRÍTICO | Interromper e alinhar arquitetura com a Constituição. |
| **2.2** | O esquema de dados cobre todos os campos necessários para atender a Spec? | OBRIGATÓRIO | Ajustar modelos de dados. |
| **2.3** | A sequência de implementação garante dependências corretas (Dados -> API -> UI)? | OBRIGATÓRIO | Reordenar etapas do plano. |

---

## 3. Matriz de Auditoria das Tarefas (`tasks.md`)

| Item | Critério de Qualidade | Status Exigido | Ação em Caso de Falha |
| :--- | :--- | :--- | :--- |
| **3.1** | Cada tarefa é atômica (executável em minutos/poucas horas)? | OBRIGATÓRIO | Decompor tarefas grandes em subtarefas. |
| **3.2** | Cada tarefa possui um critério claro de verificação/teste? | OBRIGATÓRIO | Incluir o teste/validação esperado na tarefa. |
| **3.3** | Existe rastreabilidade de 100% dos Critérios de Aceite na lista de tarefas? | CRÍTICO | Adicionar tarefas faltantes para garantir cobertura total. |

---

## 4. Anti-Patterns Comuns para Evitar no SDD

### ❌ Anti-Pattern 1: "The Sledgehammer for a Nut" (Over-Engineering)
- **Sintoma:** Criar dezenas de arquivos Markdown e modelos complexos para corrigir um bug simples de 2 linhas.
- **Solução:** Aplicar a regra de que o **custo de refinar a spec deve ser sempre menor do que o custo de corrigir erros de código**. Para mudanças triviais, use specs leves e diretas.

### ❌ Anti-Pattern 2: Ponderação de Código na Spec
- **Sintoma:** Escrever nomes de tabelas SQL ou código Python dentro do `spec.md`.
- **Solução:** Mantenha a `spec.md` limpa e funcional; a implementação pertence ao `plan.md` e ao agente de execução.

### ❌ Anti-Pattern 3: "Approval Fatigue" (Fadiga de Aprovação)
- **Sintoma:** O agente para a cada linha de código gerada para pedir aprovação humana.
- **Solução:** Concentre a aprovação humana nos documentos estruturados (`spec.md` e `plan.md`) no início da fase (*Mob Elaboration*). Uma vez aprovados, deixe a execução (*Mob Construction*) fluir autonomamente com testes automatizados.
