---
name: sdd-creator-validator
description: "Skill de criação e validação rigorosa de artefatos de Spec-Driven Development (SDD) — constitution.md, spec.md, plan.md, tasks.md e analyze.md. Ativada para estruturar especificações ou auditar a qualidade e o alinhamento de projetos AI-native."
version: 1.0.0
tags: [sdd, spec-driven-development, validation, github-spec-kit, claude-code, aidlc]
---

# SDD Creator & Validator Skill

Esta skill instrui o agente a criar, estruturar, refinar e validar a suíte completa de artefatos do **Spec-Driven Development (SDD)**. Ela garante que a intenção do negócio seja traduzida em especificações executáveis, planos arquiteturais sólidos e tarefas atômicas testáveis, prevenindo *context drift*, fragmentação de código e sobrecarga de *prompting*.

---

## 🛠️ Quando Usar Esta Skill

Ative esta skill quando a tarefa envolver:
1. **Inicializar ou criar** especificações do zero (`constitution.md`, `spec.md`, `plan.md`, `tasks.md`).
2. **Validar e auditar** artefatos SDD existentes quanto a inconsistências, lacunas, violações de governança ou detalhes técnicos prematuros.
3. **Migrar um projeto** de *vibe coding* desgovernadado para *agentic engineering* ancorado em especificações.

---

## 📐 Estrutura de Artefatos & Fluxo de Trabalho (The Workflow)

O fluxo segue a progressão sistemática do SDD e do GitHub Spec Kit:

```
[ constitution.md ]  ──►  [ spec.md ]  ──►  [ plan.md ]  ──►  [ tasks.md ]  ──►  [ analyze.md ]
   (Constituição)          (Especificar)       (Planejar)        (Tarefas)          (Auditoria)
```

### 1. Fase 0: Constituição (`constitution.md` / `AGENTS.md`)
- **Foco:** Leis fundamentais, restrições inegociáveis, padrões de acessibilidade, escolhas de stack e políticas de segurança.
- **Regra:** Nenhum artefato subsequente pode violar a Constituição.

### 2. Fase 1: Especificar (`spec.md`)
- **Foco:** **O QUE** e **PARA QUEM** (Intenção de Negócio).
- **Conteúdo Obrigatório:** Visão Geral, Histórias de Usuário, Critérios de Aceite (*GIVEN... WHEN... THEN...*), **Fora de Escopo (*Out of Scope*)** e Casos de Borda (*Edge Cases*).
- **Proibição:** Não incluir escolhas prematuras de implementação (ex.: bibliotecas específicas ou nomes de tabelas).

### 3. Fase 2: Planejar (`plan.md`)
- **Foco:** **COMO** construir.
- **Conteúdo Obrigatório:** Visão geral da arquitetura, decisões tecnológicas, esquema de dados, contratos de API, sequência de implementação e verificação de conformidade com a Constituição.

### 4. Fase 3: Tarefas (`tasks.md`)
- **Foco:** **AÇÕES ATÔMICAS** e ordenadas.
- **Conteúdo Obrigatório:** Checklist de tarefas testáveis, independentes e limitadas no tempo, organizadas por fases (Fundação, Core, UI/API, Segurança, Testes).

### 5. Fase 4: Analisar e Auditar (`analyze.md`)
- **Foco:** **QUALITY GATE** e rastreabilidade bidirecional.
- **Regra:** Checar se 100% dos Requisitos da Spec possuem Tarefas e se nenhuma Tarefa introduz escopo não especificado.

---

## 📋 Procedimento de Execução Passo a Passo

Quando instruído a **CRIAR** ou **VALIDAR** especificações:

### Passo 1: Leitura do Contexto e Carregamento Dinâmico
1. Verifique a presença dos arquivos de regra do repositório (`CLAUDE.md`, `AGENTS.md`, `.kiro/steering/`, `.amazonq/rules/`).
2. Se necessário aprofundar em conceitos ou schemas, consulte os arquivos de referência desta skill via *progressive disclosure*:
   - `references/sdd-spectrum-and-principles.md` (Fundamentos e Espectro SDD)
   - `references/artifact-templates-and-schemas.md` (Templates exatos em Markdown)
   - `references/validation-rules-and-quality-gates.md` (Checklists e regras de validação)

### Passo 2: Execução de Criação (Se o objetivo for Gerar)
- **Não faça suposições críticas:** Se a intenção for ambígua, formule perguntas de esclarecimento pontuais.
- **Respeite a separação de responsabilidades:** Não misture decisões técnicas no `spec.md`; mantenha a arquitetura no `plan.md`.
- **Preencha rigorosamente o "Fora de Escopo":** Delimitar o que **não** será feito é o principal fator de controle de custo de tokens e prevenção de *scope creep*.

### Passo 3: Execução de Validação (Se o objetivo for Auditar)
Aplique os 5 Testes de Qualidade:
1. **Teste de Autonomia do Requisito:** Cada Critério de Aceite no `spec.md` pode ser testado por uma asserção booleana (Verdadeiro/Falso)?
2. **Teste de Acoplamento Prematuro:** O `spec.md` menciona frameworks, bancos de dados ou sintaxe específica? (Se sim -> REPROVADO).
3. **Teste de Alinhamento Constitucional:** Alguma decisão no `plan.md` viola o `constitution.md`?.
4. **Teste de Cobertura de Tarefas:** Cada critério da Spec tem pelo menos uma tarefa correspondente no `tasks.md`?.
5. **Teste de Limite de Complexidade:** A Spec evita a armadilha do *over-engineering*? (Refinar a Spec deve custar menos do que corrigir código errado).
