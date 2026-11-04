import 'zebra_symbology.dart';

/// Configuração do perfil DataWedge do app.
///
/// Sempre aplicado, independentemente das opções:
/// - perfil associado somente ao pacote do app (todas as activities);
/// - saída por intent com ação exclusiva do app (`<packageName>.zebra.SCAN`);
/// - saída por teclado (keystroke) desabilitada;
/// - scanner em seleção automática.
class ZebraProfileConfig {
  const ZebraProfileConfig({
    required this.profileName,
    this.enabledSymbologies,
    this.sameBarcodeTimeout,
    this.hapticFeedback,
    this.secureDelivery = true,
    this.force = false,
  });

  /// Nome do perfil no DataWedge. Mantenha o mesmo nome entre versões do app:
  /// trocar o nome cria um novo perfil e a associação do app falha
  /// (`APP_ALREADY_ASSOCIATED`) enquanto o antigo existir.
  final String profileName;

  /// Quando informado, habilita somente estas simbologias (dentre as de
  /// [ZebraSymbology]) e desabilita as demais. Nulo mantém os padrões do DataWedge.
  final Set<ZebraSymbology>? enabledSymbologies;

  /// Tempo mínimo para aceitar o mesmo código novamente (0–5000 ms).
  /// O DataWedge aplica no modo de leitura contínua.
  final Duration? sameBarcodeTimeout;

  /// Vibração do DataWedge ao decodificar. Nulo mantém o padrão.
  final bool? hapticFeedback;

  /// Entrega explícita (pacote + assinatura do app via `intent_component_info`,
  /// DataWedge 8.0+). Desligue apenas se o aparelho não entregar leituras.
  final bool secureDelivery;

  /// Reaplica o perfil mesmo que a configuração não tenha mudado.
  final bool force;

  /// Valida os campos e lança [ArgumentError] se algo for inválido.
  void validate() {
    if (profileName.trim().isEmpty) {
      throw ArgumentError.value(
        profileName,
        'profileName',
        'não pode ser vazio',
      );
    }
    final timeout = sameBarcodeTimeout?.inMilliseconds;
    if (timeout != null && (timeout < 0 || timeout > 5000)) {
      throw ArgumentError.value(
        sameBarcodeTimeout,
        'sameBarcodeTimeout',
        'deve estar entre 0 e 5000 ms',
      );
    }
    if (enabledSymbologies != null &&
        !enabledSymbologies!.any((s) => s.decoderKey != null)) {
      throw ArgumentError.value(
        enabledSymbologies,
        'enabledSymbologies',
        'informe ao menos uma simbologia com decoder (o scanner ficaria sem leitura)',
      );
    }
  }

  Map<String, Object?> toMap() => {
        'profileName': profileName.trim(),
        'decoders': _decoders(),
        'sameBarcodeTimeoutMs': sameBarcodeTimeout?.inMilliseconds,
        'hapticFeedback': hapticFeedback,
        'secureDelivery': secureDelivery,
        'force': force,
      };

  Map<String, bool>? _decoders() {
    final enabled = enabledSymbologies;
    if (enabled == null) return null;
    return {
      for (final symbology in ZebraSymbology.values)
        if (symbology.decoderKey != null)
          symbology.decoderKey!: enabled.contains(symbology),
    };
  }
}
