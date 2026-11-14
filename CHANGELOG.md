## 2.1.0

### Build
- Exige Flutter `>=3.50.0` e Dart `>=3.14.0`. Nenhuma API muda.
- O app `example/` usa o Material do package `material_ui`.
- O app `example/` sobe para AGP 9.3.1, KGP 2.4.20 e Gradle 9.5.0: o Flutter 3.50
  recusa o build com AGP abaixo de 9.1.1 ou KGP abaixo de 2.4.10.

## 2.0.0

**Breaking:** a API foi redesenhada. Veja "Migração 1.x → 2.0" no README.

### Correções
- **Leituras vazavam entre apps.** Com dois apps abertos, a leitura feita em um era processada também pelo outro. Causa: todos os apps usavam a mesma ação de intent (`br.com.srssistemas.SCAN`) e o receiver ficava ativo em segundo plano. Agora:
  - cada app usa uma ação exclusiva (`<packageName>.zebra.SCAN`), com entrega explícita por pacote e assinatura (`intent_component_info`);
  - o receiver só fica registrado com a activity em primeiro plano.
- As chamadas ao método nativo nunca completavam (faltava `result.success`).
- O valor `"Nothing"` era emitido no stream para intents que não eram de leitura.
- Leituras vazias deixaram de ser emitidas.
- A saída por teclado (keystroke) do perfil passou a ser desabilitada.
- O perfil passou a ser criado com um único `SET_CONFIG` `CREATE_IF_NOT_EXIST`, que traz `scanner_selection` e valida o resultado. Antes eram `CREATE_PROFILE` + 2× `UPDATE`, sujeitos a corrida.
- O `println` que imprimia cada leitura foi removido.

### Novidades
- `configureProfile(ZebraProfileConfig)`: idempotente, com simbologias, timeout de duplicata e vibração opcionais. Lança `ZebraException` se o DataWedge recusar.
- `scans`: `Stream<ZebraScan>` broadcast (uma só escuta nativa), com `data`, `symbology`, `labelType`, `source` e `receivedAt`.
- `softTrigger(start|stop|toggle)`, `suspendScanner()`, `resumeScanner()` e `getInfo()`.
- Comandos ao DataWedge passam por uma fila serial, com timeout e `sendOrderedBroadcast` (Android 14).
- Funciona em aparelhos sem DataWedge: retorna o erro `DATAWEDGE_NOT_AVAILABLE`, sem crash.

### Build
- AGP 8/9, Kotlin 2.x, Java 17, `compileSdk 36`, `minSdk 24` e `namespace`.
- Testes Dart e Kotlin (Robolectric) e um app `example/`.

## 1.0.7
- Última versão da série 1.x.
