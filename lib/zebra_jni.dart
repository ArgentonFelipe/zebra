import 'dart:async';

import 'src/models/zebra_profile_config.dart';
import 'src/models/zebra_results.dart';
import 'src/models/zebra_scan.dart';
import 'src/native/jni_zebra_native.dart';
import 'src/native/zebra_native.dart';
import 'zebra_platform_interface.dart';

/// [ZebraPlatform] padrão: fala com o Kotlin por JNIgen (interop nativa do
/// Flutter 3.50), sem MethodChannel/EventChannel.
class JniZebra extends ZebraPlatform {
  /// [native] existe para testes; o padrão é a ponte JNI real.
  JniZebra({ZebraNative? native}) : _native = native ?? JniZebraNative();

  final ZebraNative _native;

  /// Broadcast criado uma única vez: a escuta nativa começa no primeiro
  /// ouvinte e termina quando o último cancela.
  late final StreamController<ZebraScan> _scans =
      StreamController<ZebraScan>.broadcast(
        onListen: () => _native.setScanListener(_onScan),
        onCancel: () => _native.setScanListener(null),
      );

  /// Sempre a mesma instância (`.stream` do controller cria uma nova a cada acesso).
  @override
  late final Stream<ZebraScan> scans = _scans.stream;

  @override
  Future<ZebraProfileResult> configureProfile(ZebraProfileConfig config) {
    config.validate();
    return _native.configureProfile(
      ZebraNativeProfileRequest.fromConfig(config),
    );
  }

  @override
  Future<void> softTrigger(ZebraTrigger action) =>
      _native.softTrigger(action.name);

  @override
  Future<void> suspendScanner() => _native.suspendScanner();

  @override
  Future<void> resumeScanner() => _native.resumeScanner();

  @override
  Future<ZebraInfo> getInfo() => _native.getInfo();

  void _onScan(
    String rawData,
    String? labelType,
    String? source,
    int timestampMs,
  ) {
    final scan = ZebraScan.fromFields(
      rawData: rawData,
      labelType: labelType,
      source: source,
      timestampMs: timestampMs,
    );
    if (scan != null) _scans.add(scan);
  }
}
