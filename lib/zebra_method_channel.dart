import 'package:flutter/services.dart';

import 'src/models/zebra_profile_config.dart';
import 'src/models/zebra_results.dart';
import 'src/models/zebra_scan.dart';
import 'zebra_platform_interface.dart';

class MethodChannelZebra extends ZebraPlatform {
  static const methodChannel = MethodChannel('br.com.srssistemas/zebra');
  static const scanChannel = EventChannel('br.com.srssistemas/scan');

  /// Criado uma única vez: cada `receiveBroadcastStream()` substitui o handler
  /// nativo, então múltiplas chamadas derrubariam ouvintes anteriores.
  /// O stream é broadcast — a escuta nativa começa no primeiro ouvinte e
  /// termina quando o último cancela.
  @override
  late final Stream<ZebraScan> scans = scanChannel
      .receiveBroadcastStream()
      .map(ZebraScan.tryParse)
      .where((scan) => scan != null)
      .cast<ZebraScan>();

  @override
  Future<ZebraProfileResult> configureProfile(ZebraProfileConfig config) async {
    config.validate();
    final result = await _invoke<Map<Object?, Object?>>(
      'configureProfile',
      config.toMap(),
    );
    return ZebraProfileResult.fromMap(result ?? const {});
  }

  @override
  Future<void> softTrigger(ZebraTrigger action) =>
      _invoke<void>('softTrigger', {'action': action.name});

  @override
  Future<void> suspendScanner() => _invoke<void>('suspendScanner');

  @override
  Future<void> resumeScanner() => _invoke<void>('resumeScanner');

  @override
  Future<ZebraInfo> getInfo() async {
    final result = await _invoke<Map<Object?, Object?>>('getInfo');
    return ZebraInfo.fromMap(result ?? const {});
  }

  Future<T?> _invoke<T>(String method, [Object? arguments]) async {
    try {
      return await methodChannel.invokeMethod<T>(method, arguments);
    } on PlatformException catch (e) {
      throw ZebraException(e.code, e.message ?? e.code);
    } on MissingPluginException {
      // Plataforma sem implementação (iOS, web, testes sem mock).
      throw const ZebraException(
        ZebraException.dataWedgeNotAvailable,
        'Plugin Zebra indisponível nesta plataforma',
      );
    }
  }
}
