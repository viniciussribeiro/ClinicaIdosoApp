# Carinhosos — Sistema de Monitoramento de Medicamentos para Idosos

> **Projeto Aplicado de Extensão Universitária — Universidade Paulista (UNIP)**

Aplicativo Android nativo para auxiliar cuidadores no acompanhamento de idosos,
medicamentos, aplicações e estoque. O projeto busca reduzir esquecimentos,
duplicidades e interrupções de tratamento por falta de medicamentos.

## Tecnologias

- Kotlin
- Jetpack Compose
- SQLite
- AlarmManager e notificações nativas do Android
- Gradle

## Funcionalidades implementadas

- Login e sessão persistente
- Painel com resumo, pendências e busca de residentes
- Cadastro de idosos, responsáveis e fotos de perfil
- Inclusão, edição e remoção de medicamentos
- Tratamento diário ou dose única
- Alteração da data inicial e do horário
- Notificações com nome do idoso, medicamento e horário
- Configuração geral e individual dos lembretes
- Histórico de doses aplicadas com data e hora
- Renovação automática dos medicamentos diários
- Estoque com quantidade mínima e alerta de nível baixo
- Seleção do medicamento diretamente do estoque
- Baixa de uma unidade ao registrar cada aplicação
- Bloqueio da aplicação quando o item está ausente ou zerado
- Validação detalhada dos campos obrigatórios, datas e horários
- Persistência local em banco de dados SQLite

## Executar o projeto

### Requisitos

- Android Studio
- Android SDK 36
- JDK 17 ou posterior
- Android 7.0 ou posterior no emulador ou aparelho

### Passos

1. Clone o repositório:

   ```bash
   git clone git@github.com:viniciussribeiro/ClinicaIdosoApp.git
   ```

2. Abra a pasta clonada no Android Studio.
3. Aguarde a sincronização do Gradle.
4. Selecione um emulador ou aparelho Android.
5. Execute o módulo `app`.

Para compilar pelo terminal no Windows:

```powershell
.\gradlew.bat assembleDebug
```

O APK será criado em `app/build/outputs/apk/debug/app-debug.apk`.

## Arquitetura

1. **Interface:** telas nativas construídas com Jetpack Compose.
2. **Banco local:** SQLite para idosos, medicamentos, histórico, estoque e
   configurações.
3. **Notificações:** alarmes diários restaurados após a reinicialização do
   aparelho.

O SQLite mantém os dados somente no aparelho. Sincronização entre vários
celulares exigirá um serviço remoto em uma etapa futura.

## Informações acadêmicas

- **Instituição:** Universidade Paulista (UNIP)
- **Curso:** Ciência da Computação
- **Disciplina:** Projeto de Extensão Universitária
- **Semestre/Ano:** 2026

## Licença

Projeto desenvolvido para fins acadêmicos e sociais.
