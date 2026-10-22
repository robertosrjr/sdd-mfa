# SDD Spectrum & Core Principles

## 1. O que é Spec-Driven Development (SDD)?

O **Spec-Driven Development (SDD)** é uma metodologia de engenharia de software na qual uma especificação minuciosa e estruturada é elaborada em linguagem natural/testável e acordada **antes** da geração de código. A especificação torna-se a **Fonte Única da Verdade (Single Source of Truth)** do sistema, invertendo a relação tradicional onde a documentação era subordinada ao código.

---

## 2. O Espectro da Especificação

O SDD opera ao longo de um espectro de maturidade e autoridade de especificação:

```
[ Spec-First ]  ────────►  [ Spec-Anchored ]  ────────►  [ Spec-as-Source ]
(Ponto de Entrada)          (Pragmático & Escalável)       (Nativo de IA Total)
```

### A. Spec-First (Desenvolvimento que Prioriza a Especificação)
- **Definição:** A especificação é escrita antes da geração de código para dar clareza inicial (User Stories, Critérios de Aceite, NFRs).
- **Manutenção:** Após a geração inicial do código, a especificação não é necessariamente mantida ou sincronizada.
- **Uso ideal:** Protótipos rápidos, MVPs, provas de conceito ou tarefas isoladas. Ponto de entrada no SDD.

### B. Spec-Anchored (Desenvolvimento Ancorado em Especificações)
- **Definição:** A especificação evolui continuamente junto com o software ao longo de todo o seu ciclo de vida.
- **Mecanismo:** Testes automatizados e suítes de avaliação (*evals*) atuam como a ponte executável entre a especificação em Markdown e a implementação no repositório.
- **Uso ideal:** Sistemas de produção, aplicações corporativas e trabalho em equipe escalável. É a abordagem mais prática e equilibrada para a maioria dos times de engenharia.

### C. Spec-as-Source (Desenvolvimento da Especificação como Fonte)
- **Definição:** A especificação é o código-fonte primário. Alterações na especificação disparam automaticamente regenerações no código (`// GENERATED FROM SPEC - DO NOT EDIT`).
- **Desafio:** Requer alto grau de confiança nos pipelines de IA e forte supervisão humana (*Human-in-the-loop*), pois LLMs permanecem não determinísticos.

---

## 3. Comparativo de Metodologias: SDD vs. Outras Abordagens

| Dimensão | Cascata (Waterfall) | Agile / Scrum | TDD | BDD | SDD (Spec-Driven Development) |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Artefato Primário** | Documento de Requisitos | Software Funcionando | Testes Unitários | Cenários Comportamentais | **Specification (Especificação Viva)** |
| **Definição de Requisitos** | Fase única inicial | Incremental em Sprints | Por função/unidade | Por comportamento | **Por funcionalidade, continuamente refinada** |
| **Documentação** | Extensa e Estática | Mínima ("Código é o doc") | Testes como documentação | Cenários como documentação | **Especificações executáveis que geram código** |
| **Colaboração com IA** | Não nativo | Não nativo | Não nativo | Não nativo | **Nativo por design (Linguagem para Agentes)** |
| **Prevenção Principal** | Desvio de Projeto | Lentidão de Entrega | Regressão de Código | Falha de Comunicação | **Context Drift, Fragmentação e Token Waste** |

---

## 4. Os 3 Grandes Perigos no Desenvolvimento com IA (E Como o SDD Resolve)

Sem especificações formally ancoradas, o desenvolvimento agêntico sofre de três patologias graves:

1. **Context Drift (Desvio de Contexto):** Ocorre quando o agente corrige um bug local ou adiciona uma funcionalidade, mas quebra regras globais da aplicação por falta de visão holística.
2. **Fragmentação Arquitetural:** O código novo não segue as convenções existentes, gerando código macarrônico (*AI spaghetti*) e aumentando a Dívida Técnica OpEx.
3. **Operational Token Waste (Desperdício de Tokens):** Desenvolvedores ficam presos em longos ciclos de prompts e tentativas e erros, consumindo milhares de tokens para resolver ambiguidades que uma especificação simples teria evitado.
