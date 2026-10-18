# CasaHub

App Android para controlar seus dispositivos SmartThings (Samsung e compatíveis) pela API oficial, num painel mais rápido e direto que o app original.

## O que ele faz
- Todos os dispositivos da casa num painel em grade, agrupados por cômodo
- Liga/desliga direto no card; toque no card para os controles completos
- Brilho, temperatura de cor, cor, volume, mudo, persianas, fechaduras, termostato e ar-condicionado
- Cenas da SmartThings em um toque
- Favoritos (segure um card) que ficam no topo
- Busca por nome ou cômodo e filtro por cômodo
- Resumo da casa: quantos ligados, temperatura média, aparelhos offline
- Alertas: porta aberta, fechadura destrancada, vazamento, bateria fraca
- Botão "Desligar tudo" (na casa inteira ou só na lista filtrada)
- Atualização automática enquanto o app está aberto + puxar para atualizar
- Vários locais (casas) na mesma conta
- Modo demo com uma casa simulada, para testar sem conta
- Tema claro/escuro e cores do Material You

## Como gerar o APK

### Opção A — GitHub (sem instalar nada)
1. Crie um repositório no GitHub e envie esta pasta inteira (pelo site: "Add file" → "Upload files", arrastando o conteúdo do zip).
2. Abra a aba **Actions**: o fluxo "Gerar APK" roda sozinho (ou clique em "Run workflow").
3. Quando terminar (~5 min), baixe o artefato **CasaHub-apk**. Dentro vem o `CasaHub.apk`.

### Opção B — Android Studio
1. Abra esta pasta no Android Studio (File → Open).
2. Espere o Gradle sincronizar.
3. Menu **Build → Build App Bundle(s) / APK(s) → Build APK(s)**.
4. O APK fica em `app/build/outputs/apk/debug/app-debug.apk`.

### Opção C — Linha de comando (com Android SDK instalado)
```
./gradlew assembleRelease
```
APK em `app/build/outputs/apk/release/app-release.apk`.

## Instalar no celular
Copie o APK para o celular, abra e permita "instalar apps desconhecidos" quando o Android pedir.

## Como conectar sua conta
1. Acesse https://account.smartthings.com/tokens e entre com a conta Samsung.
2. "Gerar novo token", marque todas as permissões, copie o código.
3. Cole no app.

Tokens pessoais novos da SmartThings valem 24 horas. Quando expirar, o app volta para a tela do token. Para acesso permanente é preciso registrar um app OAuth na SmartThings (próximo passo, se quiser).

## Estrutura
- `data/SmartThingsRepository.kt` — cliente da API REST da SmartThings
- `data/DemoRepository.kt` — casa simulada
- `data/HomeStore.kt` — estado do app e regras (filtros, favoritos, comandos otimistas)
- `ui/screens/` — telas em Jetpack Compose (login, painel, detalhes do dispositivo)

Requisitos: Android 8.0 ou superior.
