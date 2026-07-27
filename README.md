# Carinhosos — aplicativo Android

Aplicativo Android nativo em Kotlin e Jetpack Compose para acompanhamento de
residentes de uma clínica de cuidados para idosos.

## Funcionalidades

- Login com a credencial definida para o projeto
- Sessão persistente: o login é mantido até o usuário escolher sair
- Painel com resumo de residentes, pendências e busca por nome
- Cadastro de idosos, responsável e medicamentos
- Foto de perfil escolhida na galeria e exibida no painel e na ficha
- Ficha individual com medicamentos e registro de doses aplicadas no dia
- Inclusão, edição e remoção de medicamentos na ficha do residente
- Alteração da data de início e do horário de cada medicamento
- Medicamento selecionado diretamente do estoque
- Baixa automática de uma unidade do estoque ao registrar a aplicação
- Bloqueio da aplicação quando o medicamento está zerado ou ausente
- Histórico de doses aplicadas com data e horário
- Tratamentos diários renovados automaticamente no dia seguinte
- Suporte a tratamento diário ou dose única
- Validação detalhada dos campos de medicamento, data e horário
- Área de notificações com controle geral e lembrete individual
- Notificações diárias com nome do idoso, medicamento e horário
- Estoque com quantidade mínima, alerta de nível baixo e ajuste de unidades
- Banco de dados SQLite local, persistido mesmo após fechar o aplicativo

## Abrir e executar

1. Abra esta pasta no Android Studio.
2. Aguarde a sincronização do projeto.
3. Selecione um emulador ou aparelho Android.
4. Execute o módulo `app`.

O APK de demonstração também está disponível em
`Carinhosos-v1.3.1.apk`.

## Requisitos

- Android 7.0 ou posterior
- Android Studio com SDK 36
- JDK 17 ou posterior

> Este projeto mantém os dados somente no aparelho. Para uso simultâneo em
> vários celulares, é necessário conectar um serviço de sincronização.
