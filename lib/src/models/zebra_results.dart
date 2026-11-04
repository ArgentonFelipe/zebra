/// Erro retornado pelo DataWedge ou pelo plugin.
class ZebraException implements Exception {
  const ZebraException(this.code, this.message);

  /// Ex.: `DATAWEDGE_NOT_AVAILABLE`, `TIMEOUT`, `APP_ALREADY_ASSOCIATED`,
  /// `PROFILE_NOT_FOUND`, `PARAMETER_INVALID`, `COMMAND_FAILED`.
  final String code;
  final String message;

  static const dataWedgeNotAvailable = 'DATAWEDGE_NOT_AVAILABLE';
  static const timeout = 'TIMEOUT';

  @override
  String toString() => 'ZebraException($code): $message';
}

/// Resultado de `Zebra.configureProfile`.
class ZebraProfileResult {
  const ZebraProfileResult({
    required this.applied,
    required this.scanAction,
    required this.secureDelivery,
  });

  factory ZebraProfileResult.fromMap(Map<Object?, Object?> map) =>
      ZebraProfileResult(
        applied: map['applied'] == true,
        scanAction: map['scanAction'] as String? ?? '',
        secureDelivery: map['secureDelivery'] == true,
      );

  /// `false` quando a mesma configuração já estava aplicada e o perfil existe.
  final bool applied;

  /// Ação de intent exclusiva usada pelo app.
  final String scanAction;

  /// Se a entrega explícita por pacote + assinatura foi configurada.
  final bool secureDelivery;
}

/// Informações do aparelho/DataWedge.
class ZebraInfo {
  const ZebraInfo({
    required this.isZebraDevice,
    required this.dataWedgeInstalled,
    required this.scanAction,
    this.dataWedgeVersion,
    this.dataWedgeEnabled,
  });

  factory ZebraInfo.fromMap(Map<Object?, Object?> map) => ZebraInfo(
        isZebraDevice: map['isZebraDevice'] == true,
        dataWedgeInstalled: map['dataWedgeInstalled'] == true,
        scanAction: map['scanAction'] as String? ?? '',
        dataWedgeVersion: map['dataWedgeVersion'] as String?,
        dataWedgeEnabled: map['dataWedgeEnabled'] as bool?,
      );

  final bool isZebraDevice;
  final bool dataWedgeInstalled;
  final String scanAction;

  /// Nulo quando o DataWedge não respondeu.
  final String? dataWedgeVersion;

  /// Nulo quando o DataWedge não respondeu.
  final bool? dataWedgeEnabled;

  @override
  String toString() =>
      'ZebraInfo(zebra: $isZebraDevice, dataWedge: $dataWedgeVersion, enabled: $dataWedgeEnabled)';
}

/// Ação do gatilho por software.
enum ZebraTrigger { start, stop, toggle }
