/// Integração Flutter <-> Zebra DataWedge (TC21 e demais coletores Zebra).
library;

import 'src/models/zebra_profile_config.dart';
import 'src/models/zebra_results.dart';
import 'src/models/zebra_scan.dart';
import 'zebra_platform_interface.dart';

export 'src/models/zebra_profile_config.dart';
export 'src/models/zebra_results.dart';
export 'src/models/zebra_scan.dart';
export 'src/models/zebra_symbology.dart';

/// Ponto de entrada do plugin.
///
/// Garantias de isolamento entre apps:
/// - cada app recebe somente as leituras do **seu** perfil (ação de intent exclusiva);
/// - leituras só são entregues com o app em primeiro plano.
class Zebra {
  const Zebra();

  ZebraPlatform get _platform => ZebraPlatform.instance;

  /// Cria/atualiza o perfil DataWedge do app (um único `SET_CONFIG`
  /// `CREATE_IF_NOT_EXIST`). Idempotente: não reaplica se nada mudou.
  ///
  /// Lança [ZebraException] se o DataWedge recusar a configuração, não responder
  /// (`TIMEOUT`) ou não existir no aparelho (`DATAWEDGE_NOT_AVAILABLE`), e
  /// [ArgumentError] se [config] for inválida.
  Future<ZebraProfileResult> configureProfile(ZebraProfileConfig config) =>
      _platform.configureProfile(config);

  /// Leituras do scanner. Stream broadcast: pode ter vários ouvintes.
  /// Leituras vazias são descartadas e [ZebraScan.data] já vem com trim.
  Stream<ZebraScan> get scans => _platform.scans;

  /// Aciona o gatilho por software (equivalente ao botão físico).
  Future<void> softTrigger([ZebraTrigger action = ZebraTrigger.start]) =>
      _platform.softTrigger(action);

  /// Bloqueia temporariamente o scanner (o gatilho não lê nada).
  /// Estado de runtime: o DataWedge o descarta ao trocar de perfil/app.
  Future<void> suspendScanner() => _platform.suspendScanner();

  /// Desfaz [suspendScanner].
  Future<void> resumeScanner() => _platform.resumeScanner();

  /// Dados do aparelho e do DataWedge (nunca lança por ausência do DataWedge).
  Future<ZebraInfo> getInfo() => _platform.getInfo();
}
