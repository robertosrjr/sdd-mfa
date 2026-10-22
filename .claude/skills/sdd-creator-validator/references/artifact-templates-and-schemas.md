# Templates & Schemas dos Artefatos SDD

Estes templates fornecem a estrutura padronizada em Markdown exigida para projetos SDD e suporte ao GitHub Spec Kit / Claude Code / AI-DLC workflows.

---

## 1. Template: `constitution.md` (A Constituição do Projeto)

```markdown
# Projeto: [Nome do Projeto]
# Documento: constitution.md
# Versão: 1.0.0

## 1. Visão Geral e Princípios Fundamentais
[Descrição em 2 parágrafos do propósito do sistema e dos valores inegociáveis de engenharia.]

## 2. Guardrails Inegociáveis (Immutable Rules)
1. **Segurança & Privacidade:** [ex.: Nenhuma credencial em texto limpo; conformidade LGPD/GDPR].
2. **Arquitetura & Padrões:** [ex.: Padrão REST/GraphQL, isolamento por camadas, DDD].
3. **Qualidade & Testes:** [ex.: Cobertura mínima de 80% de testes de integração].
4. **Desempenho & NFRs:** [ex.: Tempo máximo de resposta de API < 200ms].

## 3. Tech Stack Autorizada
- **Language / Runtime:** [ex.: Python 3.12 / Node.js 22]
- **Frameworks:** [ex.: FastAPI, React]
- **Database / Cache:** [ex.: PostgreSQL, Redis]
- **Infraestrutura / IaC:** [ex.: Terraform, AWS Lambda]

## 4. Convenções de Código e Repositório
- Estrutura de pastas, padronização de commits e regras de PR.
```

---

## 2. Template: `spec.md` (Especificação Funcional)

```markdown
# Feature: [Nome da Funcionalidade]
# Documento: spec.md
# Módulo: [Nome do Módulo]

## 1. Visão Geral & Problema de Negócio
[Resumo de 2-3 frases explicando O QUE o recurso faz e QUAL dor do usuário resolve.]

## 2. Histórias de Usuário (User Stories)
- **US-01:** Como [perfil do usuário], quero [ação desejada] para que [benefício de negócio].
- **US-02:** Como [perfil do usuário], quero [ação desejada] para que [benefício de negócio].

## 3. Critérios de Aceite (Acceptance Criteria)

### Critérios da US-01: [Título]
- **GIVEN** [contexto ou estado inicial do sistema],
- **WHEN** [ação realizada pelo usuário ou evento acionado],
- **THEN** [resultado observável e testável].

- **GIVEN** [outro contexto],
- **WHEN** [outra ação],
- **THEN** [resultado].

## 4. Fora de Escopo (Out of Scope) - OBLIGATÓRIO
- [Item 1 que NÃO será construído nesta iteração]
- [Item 2 que NÃO será construído nesta iteração]

## 5. Casos de Borda (Edge Cases) & Tratamento de Erros
- **Cenário de Falha 1:** [ex.: Entrada inválida ou API indisponível] -> **Comportamento Esperado:** [Ação do sistema].
- **Cenário de Falha 2:** [ex.: Timeout de conexão] -> **Comportamento Esperado:** [Ação do sistema].

## 6. Requisitos Não-Funcionais (NFRs)
- **Desempenho:** [Latência aceitável]
- **Segurança:** [Validações e sanitização]
```

---

## 3. Template: `plan.md` (Plano Técnico de Implementação)

```markdown
# Plano Técnico: [Nome da Funcionalidade]
# Documento: plan.md

## 1. Visão Geral da Arquitetura
[Descrição do fluxo de dados e interação entre componentes técnicos.]

## 2. Modelagem de Dados (Data Schema)
```json
{
  "Entidade": "Transacao",
  "Campos": {
    "id": "UUID",
    "valor": "Decimal(10,2)",
    "data": "Timestamp",
    "status": "Enum[PENDENTE, CONFIRMADO]"
  }
}
```

## 3. Contratos de API / Interfaces
- `POST /api/v1/recurso`: [Descrição de entrada e saída]

## 4. Sequência de Implementação
1. Camada de Dados (Migrations & Modelos)
2. Camada de Serviços & Lógica de Negócio
3. Camada de API / Endpoints
4. Interface de Usuário / Integração

## 5. Verificação Constitucional
- [x] Respeita o padrão de segurança da `constitution.md`
- [x] Respeita a stack tecnológica aprovada
```

---

## 4. Template: `tasks.md` (Checklist de Tarefas Atômicas)

```markdown
# Lista de Tarefas: [Nome da Funcionalidade]
# Documento: tasks.md

## Fase 1: Fundação & Dados
- [ ] **Task 1.1:** Criar migração e modelo de dados para a entidade `[Nome]`.
- [ ] **Task 1.2:** Escrever testes unitários da camada de persistência.

## Fase 2: Lógica de Negócio Core
- [ ] **Task 2.1:** Implementar serviço de `[Funcionalidade]`.
- [ ] **Task 2.2:** Adicionar tratamento para os Casos de Borda descritos na spec.

## Fase 3: API & Integrações
- [ ] **Task 3.1:** Criar endpoint `POST /api/v1/...` com validações.
- [ ] **Task 3.2:** Integrar testes de aceitação automatizados.

## Fase 4: Validação & Quality Gate
- [ ] **Task 4.1:** Executar suíte completa de testes e verificar cobertura.
- [ ] **Task 4.2:** Atualizar documentação e registrar métricas no `analyze.md`.
```
