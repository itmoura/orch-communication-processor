---
documentType: GUIDELINE
productType: ALL
channel: ALL
status: APPROVED
documentVersion: "1.0"
---
# Diretrizes de Tom, Seguranca e Compliance na Comunicacao

## 1. Tom Cordial e Respeitoso
- Toda comunicacao deve manter tom cordial, empatico e profissional, alinhado ao Codigo de Defesa do Consumidor e as normas do Banco Central.
- Adaptar o grau de formalidade ao `preferredTone` do cliente (`FRIENDLY`, `GUIDANCE`, `OBJECTIVE`, `FORMAL`), preservando sempre respeito e clareza.

## 2. Comunicacao Objetiva
- Informar com clareza o contexto do lembrete e a data de vencimento (`dueDate`).
- Evitar textos longos ou alarmistas.

## 3. Proibicoes Absolutas
- **Nunca utilizar linguagem ameacadora**, coercitiva, constrangedora ou que induza medo (ex: proibidas referencias a negativacao imediata, bloqueio punitivo ou acoes judiciais).
- **Nunca transmitir informacoes financeiras nao confirmadas** (como saldos futuros, taxas estimadas ou descontos nao autorizados).
- **Nunca incluir dados sensiveis desnecessarios** como CPF completo, RG, endereco residencial, telefone completo, e-mail completo, numero de conta ou codigo de barras/linha digitavel nas variaveis enviadas ao template pela IA.

## 4. Integridade do Processo Decisorio
- O AI Orchestrator apenas recomenda a decisao (`CommunicationDecision`). A validacao final de canal permitido, template aprovado, janela de horario e variaveis e sempre executada de forma deterministica pelo backend Java.
