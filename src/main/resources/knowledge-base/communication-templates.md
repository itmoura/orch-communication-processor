---
documentType: TEMPLATE_CATALOG
productType: ALL
channel: ALL
status: APPROVED
documentVersion: "1.0"
---
# Catalogo de Templates Aprovados

Somente os templates listados abaixo estao homologados pelo compliance bancario. E proibido inventar novos IDs de template.

## 1. Template `BOLETO_REMINDER`
- **Status**: `APPROVED`
- **Finalidade**: Lembrete preventivo quando um novo boleto e gerado ou para lembrar o vencimento futuro com cordialidade.
- **Canais permitidos**: `WHATSAPP`, `EMAIL`, `SMS`
- **Produtos aplicaveis**: `PERSONAL_LOAN`, `CREDIT_CARD`
- **Variaveis obrigatorias**:
  - `firstName` (primeiro nome do cliente)
  - `dueDate` (data de vencimento no formato `dd/MM/yyyy`)
- **Motivos compativeis (`reasonCode`)**: `DUE_DATE_REMINDER`, `PREVENTIVE_REMINDER`
- **Exemplo conceitual**: "Ola, {{firstName}}! Seu boleto com vencimento em {{dueDate}} ja esta disponivel nos canais oficiais do banco."

## 2. Template `PAYMENT_GUIDANCE_EMAIL`
- **Status**: `APPROVED`
- **Finalidade**: Orientacao detalhada e educativa de pagamento por e-mail para clientes com historico de atrasos recorrentes, facilitando o planejamento financeiro.
- **Canais permitidos**: `EMAIL`
- **Produtos aplicaveis**: `PERSONAL_LOAN`, `CREDIT_CARD`
- **Variaveis obrigatorias**:
  - `firstName`
  - `dueDate` (`dd/MM/yyyy`)
- **Motivos compativeis (`reasonCode`)**: `PAYMENT_GUIDANCE`, `DUE_DATE_REMINDER`
- **Exemplo conceitual**: "Ola, {{firstName}}. Compartilhamos as orientacoes para pagamento do seu boleto com vencimento em {{dueDate}} pelos nossos canais oficiais."

## 3. Template `NEAR_DUE_DATE_ALERT`
- **Status**: `APPROVED`
- **Finalidade**: Comunicacao objetiva de vencimento proximo (ate 3 dias antes do vencimento) para evitar atrasos involuntarios.
- **Canais permitidos**: `WHATSAPP`, `EMAIL`, `SMS`
- **Produtos aplicaveis**: `PERSONAL_LOAN`, `CREDIT_CARD`
- **Variaveis obrigatorias**:
  - `firstName`
  - `dueDate` (`dd/MM/yyyy`)
- **Motivos compativeis (`reasonCode`)**: `DUE_DATE_REMINDER`, `PREVENTIVE_REMINDER`

## 4. Template `OVERDUE_SUPPORT_NOTICE`
- **Status**: `APPROVED`
- **Finalidade**: Comunicacao cordial para boletos em atraso, oferecendo orientacao para regularizacao nos canais oficiais sem linguagem intimidadora.
- **Canais permitidos**: `EMAIL`, `WHATSAPP`
- **Produtos aplicaveis**: `PERSONAL_LOAN`, `CREDIT_CARD`
- **Variaveis obrigatorias**:
  - `firstName`
  - `dueDate` (`dd/MM/yyyy`)
- **Motivos compativeis (`reasonCode`)**: `OVERDUE_GUIDANCE`
