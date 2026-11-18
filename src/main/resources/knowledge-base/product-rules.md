---
documentType: PRODUCT_RULE
productType: ALL
channel: ALL
status: APPROVED
documentVersion: "1.0"
---
# Regras de Produtos Bancarios - Credito Pessoal e Cartao de Credito

## 1. Credito Pessoal (`PERSONAL_LOAN`)
- **Caracteristicas**: Parcelas mensais fixas com vencimento definido em contrato. Atrasos geram encargos contratuais proporcionais aos dias de atraso e podem impactar futuras renovacoes de credito.
- **Orientacoes de comunicacao**:
  - Incentivar o pagamento ate a data de vencimento (`dueDate`) para evitar juros de mora e multa contratual.
  - Para clientes com historico de atrasos em `PERSONAL_LOAN`, priorizar comunicacao orientativa por `EMAIL` quando esse for o canal preferido ou quando houver necessidade de explicar condicoes de pagamento com clareza.
  - Para clientes adimplentes e preventivos, utilizar `BOLETO_REMINDER` no canal preferido elegivel (como `WHATSAPP`).
- **Restricoes**:
  - Nunca mencionar valores de juros nao calculados pelo sistema de cobranca.
  - Nunca enviar codigo de barras ou linha digitavel completa dentro de variaveis de prompt ou payloads de IA.

## 2. Cartao de Credito (`CREDIT_CARD`)
- **Caracteristicas**: Fatura mensal com possibilidade de pagamento total ou pagamento minimo ate o vencimento.
- **Orientacoes de comunicacao**:
  - Destacar a data de vencimento da fatura (`dueDate`) e orientar que o pagamento integral evita encargos do credito rotativo.
  - Para clientes sem canal `WHATSAPP` elegivel, utilizar `EMAIL` com linguagem objetiva e clara.
- **Restricoes**:
  - Nao sugerir parcelamento automatico de fatura sem oferta aprovada em sistema.
  - Nunca exibir limite total, numero do cartao ou dados sensiveis na comunicacao.
