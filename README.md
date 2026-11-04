# zebra

Plugin Flutter para integrar apps com o **Zebra DataWedge** (TC21 e demais coletores Zebra).

## Garantias

- **Isolamento entre apps:** cada app usa a ação de intent `<packageName>.zebra.SCAN`. Quando possível, a entrega também é explícita por pacote e assinatura (`intent_component_info`). Assim, um app nunca recebe as leituras de outro.
- **Só em primeiro plano:** o receiver nativo só fica registrado enquanto há ouvinte no Dart **e** a activity está `RESUMED`. Um app em segundo plano não recebe nenhuma leitura, nem mesmo enfileirada (comportamento do Android 14).
- **Sem digitação acidental:** a saída por teclado (keystroke) do perfil é desligada.
- **Leituras limpas:** leituras vazias são descartadas, e `ZebraScan.data` já vem com `trim()`.
- **Toda chamada termina:** os comandos ao DataWedge sempre retornam sucesso, `ZebraException` ou `TIMEOUT`.

## Requisitos

- Android `minSdk 24`. O app pode usar `compileSdk`/`targetSdk` até 36 e AGP 8.x ou 9.x.
- DataWedge 7.1+ (o TC21 com Android 10 já vem com o 8.1). A entrega segura exige o DataWedge 8.0+.
- Em aparelhos sem DataWedge (emulador, celular comum), os métodos lançam `ZebraException(DATAWEDGE_NOT_AVAILABLE)` e o stream fica vazio.

## Instalação

Sempre aponte para uma **tag**, nunca para `master`:

```yaml
dependencies:
  zebra:
    git:
      url: https://github.com/ArgentonFelipe/zebra.git
      ref: v2.0.0
```

## Uso

```dart
const zebra = Zebra();

// 1. Configure o perfil uma vez na inicialização. É idempotente: só reaplica se algo mudou.
try {
  await zebra.configureProfile(
    const ZebraProfileConfig(
      profileName: 'MeuApp', // não troque o nome entre versões
      // Opcional: habilita só estas simbologias e desliga as demais.
      enabledSymbologies: {ZebraSymbology.ean13, ZebraSymbology.code128},
    ),
  );
} on ZebraException catch (e) {
  log('Falha ao configurar DataWedge: ${e.code} ${e.message}');
}

// 2. Escute as leituras. O stream é broadcast e aceita vários ouvintes.
final sub = zebra.scans.listen((scan) {
  print('${scan.data} (${scan.symbology.name})');
});

// 3. Cancele quando a tela sair.
await sub.cancel();
```

Outros recursos:

| Método | Descrição |
|---|---|
| `softTrigger([ZebraTrigger])` | Aciona o gatilho por software (`start`, `stop` ou `toggle`). |
| `suspendScanner()` / `resumeScanner()` | Bloqueia ou libera o scanner, por exemplo enquanto um diálogo está aberto. Esse estado vale só em runtime: o DataWedge o descarta ao trocar de perfil ou de app. |
| `getInfo()` | Informa se o aparelho é Zebra, a versão do DataWedge e se ele está habilitado. |

### `ZebraProfileConfig`

| Campo | Padrão | Descrição |
|---|---|---|
| `profileName` | obrigatório | Nome do perfil no DataWedge. |
| `enabledSymbologies` | `null` (padrões do DataWedge) | Habilita apenas as simbologias listadas. |
| `sameBarcodeTimeout` | `null` | Intervalo para aceitar o mesmo código de novo (0 a 5000 ms; vale no modo de leitura contínua). |
| `hapticFeedback` | `null` | Vibração do DataWedge ao decodificar. |
| `secureDelivery` | `true` | Entrega explícita por pacote e assinatura. |
| `force` | `false` | Reaplica o perfil mesmo sem mudanças. |

## Migração 1.x → 2.0

| 1.x | 2.0 |
|---|---|
| `createProfile(profileName: 'X')` | `await configureProfile(const ZebraProfileConfig(profileName: 'X'))` |
| `scanBarcode()` → `Stream` (dynamic) | `scans` → `Stream<ZebraScan>`; use `scan.data` |
| `startBarcodeScanning()` | `softTrigger()` |

- **Mantenha o mesmo `profileName`.** O perfil existente é corrigido no lugar. Um nome novo falharia com `APP_ALREADY_ASSOCIATED`, porque o pacote continua associado ao perfil antigo.
- Não é mais preciso criar `StreamController.broadcast()`/`asBroadcastStream()` nem tratar `AppLifecycleState` só para pausar o scanner: o plugin já faz isso.
- O valor `"Nothing"` não é mais emitido.

## Desenvolvimento

```bash
flutter test                                    # testes Dart
cd example && flutter build apk --debug         # gera o projeto Android
cd example/android && ./gradlew :zebra:testDebugUnitTest   # testes Kotlin (Robolectric)
```

O app `example/` serve para validar no aparelho: mostra o status do perfil, um campo de texto para testar o keystroke, soft trigger, suspender/retomar e a lista de leituras.
