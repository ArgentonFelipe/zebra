import '../models/zebra_profile_config.dart';
import '../models/zebra_results.dart';

/// Recebe uma leitura crua do nativo.
typedef ZebraNativeScanCallback = void Function(
  String rawData,
  String? labelType,
  String? source,
  int timestampMs,
);

/// Ponte de baixo nível com o Kotlin (`ZebraBridge`).
///
/// A implementação real é `JniZebraNative` (JNIgen). Os testes usam um fake,
/// já que os bindings JNI só rodam dentro do app Android.
///
/// Erros chegam como [ZebraException].
abstract interface class ZebraNative {
  Future<ZebraProfileResult> configureProfile(
    ZebraNativeProfileRequest request,
  );

  /// [action] é o nome de `ZebraTrigger` (`start`, `stop`, `toggle`).
  Future<void> softTrigger(String action);

  Future<void> suspendScanner();

  Future<void> resumeScanner();

  Future<ZebraInfo> getInfo();

  /// Liga ([onScan] não nulo) ou desliga (nulo) a escuta nativa de leituras.
  void setScanListener(ZebraNativeScanCallback? onScan);
}

/// [ZebraProfileConfig] no formato aceito pelo `ZebraBridge.configureProfile`
/// do Kotlin: só tipos primitivos e arrays, com `-1` para "padrão do DataWedge".
final class ZebraNativeProfileRequest {
  const ZebraNativeProfileRequest({
    required this.profileName,
    required this.enabledDecoders,
    required this.disabledDecoders,
    required this.sameBarcodeTimeoutMs,
    required this.hapticFeedback,
    required this.secureDelivery,
    required this.force,
  });

  factory ZebraNativeProfileRequest.fromConfig(ZebraProfileConfig config) {
    final decoders = config.toMap()['decoders'] as Map<String, bool>?;
    final haptic = config.hapticFeedback;
    return ZebraNativeProfileRequest(
      profileName: config.profileName.trim(),
      enabledDecoders: decoders?.entries
          .where((entry) => entry.value)
          .map((entry) => entry.key)
          .toList(),
      disabledDecoders: decoders?.entries
          .where((entry) => !entry.value)
          .map((entry) => entry.key)
          .toList(),
      sameBarcodeTimeoutMs: config.sameBarcodeTimeout?.inMilliseconds ?? -1,
      hapticFeedback: haptic == null ? -1 : (haptic ? 1 : 0),
      secureDelivery: config.secureDelivery,
      force: config.force,
    );
  }

  final String profileName;

  /// Nulo junto com [disabledDecoders]: mantém os padrões do DataWedge.
  final List<String>? enabledDecoders;
  final List<String>? disabledDecoders;

  /// `-1` mantém o padrão do DataWedge.
  final int sameBarcodeTimeoutMs;

  /// `1` liga, `0` desliga, `-1` mantém o padrão do DataWedge.
  final int hapticFeedback;

  final bool secureDelivery;
  final bool force;
}
