import 'package:plugin_platform_interface/plugin_platform_interface.dart';

import 'src/models/zebra_profile_config.dart';
import 'src/models/zebra_results.dart';
import 'src/models/zebra_scan.dart';
import 'zebra_jni.dart';

abstract class ZebraPlatform extends PlatformInterface {
  ZebraPlatform() : super(token: _token);

  static final Object _token = Object();

  static ZebraPlatform _instance = JniZebra();

  /// Instância padrão: [JniZebra] (JNIgen).
  static ZebraPlatform get instance => _instance;

  static set instance(ZebraPlatform instance) {
    PlatformInterface.verifyToken(instance, _token);
    _instance = instance;
  }

  Future<ZebraProfileResult> configureProfile(ZebraProfileConfig config);

  /// Stream broadcast único de leituras (uma só escuta nativa).
  Stream<ZebraScan> get scans;

  Future<void> softTrigger(ZebraTrigger action);

  Future<void> suspendScanner();

  Future<void> resumeScanner();

  Future<ZebraInfo> getInfo();
}
