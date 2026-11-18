---
documentType: POLICY
productType: ALL
channel: ALL
status: APPROVED
documentVersion: "1.0"
---
# Politicas de Comunicacao de Boletos

## 1. Horarios Permitidos e Janelas de Envio
- As comunicacoes preventivas e de cobranca suave somente podem ser agendadas entre **08:00 e 20:00** (horario de Brasilia, `-03:00`).
- Preferencias de periodo do cliente devem ser respeitadas sempre que possivel:
  - `MORNING`: entre `08:30` e `11:30` (padrao recomendado: `10:00:00-03:00`).
  - `AFTERNOON`: entre `13:30` e `17:30` (padrao recomendado: `15:00:00-03:00`).
  - `EVENING`: entre `18:00` e `19:45` (padrao recomendado: `18:30:00-03:00`).
- Se o evento `BOLETO_GERADO` ocorrer apos `19:30` ou se a janela preferencial do dia ja tiver passado em relacao ao `occurredAt`, agendar para o proximo horario util disponivel dentro da janela permitida.

## 2. Regras de Comunicacao Preventiva
- Para clientes com bom historico de pagamentos (`onTimePayments >= latePayments` e atraso medio baixo), priorizar abordagem preventiva, cordial e objetiva no momento da emissao ou proximo ao vencimento.
- Para clientes com historico frequente de atrasos (`latePayments > onTimePayments` ou `averageDelayDays >= 5`), adotar abordagem orientativa (`PAYMENT_GUIDANCE`), destacando a data de vencimento, a importancia da organizacao financeira e canais oficiais de pagamento sem tom coercitivo.

## 3. Restricoes por Canal e Elegibilidade
- Nunca recomendar um canal que nao conste na lista `allowedChannels` da elegibilidade do cliente.
- Se o canal preferido (`preferredChannel`) estiver bloqueado por ausencia de opt-in, revogacao de consentimento ou restricao cadastral, selecionar o melhor canal alternativo permitido (por exemplo, `EMAIL` quando `WHATSAPP` estiver bloqueado).
- Clientes com `optedOut = true` ou sem nenhum canal permitido devem receber decisao `SUPPRESS` com `reasonCode` adequado (`CUSTOMER_OPT_OUT` ou `CHANNEL_UNAVAILABLE`).
- Nao exceder 1 comunicacao preventiva por boleto em janela inferior a 48 horas, salvo vencimento imediato.

## 4. Orientacoes para Boletos Proximos do Vencimento
- Quando faltarem entre 1 e 3 dias para o vencimento (`daysUntilDue` entre 1 e 3), utilizar comunicacao direta de vencimento proximo (`DUE_DATE_REMINDER`) e priorizar canais de leitura rapida permitidos (`WHATSAPP` ou `SMS` se elegiveis; caso contrario `EMAIL`).
- Quando o boleto vencer em 4 ou mais dias, utilizar lembrete preventivo (`PREVENTIVE_REMINDER` ou `DUE_DATE_REMINDER`) ou orientacao de pagamento (`PAYMENT_GUIDANCE`) conforme o perfil comportamental.
- Para boletos ja vencidos (`daysUntilDue < 0`), utilizar orientacao de regularizacao (`OVERDUE_GUIDANCE`).
