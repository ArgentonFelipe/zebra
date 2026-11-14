import 'dart:async';
import 'dart:io';

import 'package:jni/jni.dart';

import '../jni/zebra_bindings.dart';
import '../models/zebra_results.dart';
import 'zebra_native.dart';

/// [ZebraNative] via JNIgen: o Dart chama o `ZebraBridge` do Kotlin direto por
/// JNI, sem platform channel (interop nativa estável desde o Flutter 3.50).
///
/// Os callbacks são implementados em modo listener (`$async: true`): o Kotlin
/// não espera o Dart responder, então a main thread do Android nunca bloqueia.
final class JniZebraNative implements ZebraNative {
  JniZebraNative();

  static const _commandFailed = 'COMMAND_FAILED';

  ScanListener? _scanListener;

  @override
  Future<ZebraProfileResult> configureProfile(
    ZebraNativeProfileRequest request,
  ) {
    final completer = Completer<ZebraProfileResult>();
    _call(completer, (arena) {
      final callback = ProfileCallback.implement(
        $ProfileCallback(
          onApplied: (applied, scanAction, secureDelivery) =>
              completer.complete(
                ZebraProfileResult(
                  applied: applied,
                  scanAction: scanAction.toDartString(releaseOriginal: true),
                  secureDelivery: secureDelivery,
                ),
              ),
          onApplied$async: true,
          onError: (code, message) =>
              completer.completeError(_exception(code, message)),
          onError$async: true,
        ),
      )..releasedBy(arena);
      ZebraBridge.configureProfile(
        request.profileName.toJString()..releasedBy(arena),
        _stringArray(request.enabledDecoders, arena),
        _stringArray(request.disabledDecoders, arena),
        request.sameBarcodeTimeoutMs,
        request.hapticFeedback,
        request.secureDelivery,
        request.force,
        callback,
      );
    });
    return completer.future;
  }

  @override
  Future<void> softTrigger(String action) => _command(
    (callback, arena) => ZebraBridge.softTrigger(
      action.toJString()..releasedBy(arena),
      callback,
    ),
  );

  @override
  Future<void> suspendScanner() =>
      _command((callback, _) => ZebraBridge.suspendScanner(callback));

  @override
  Future<void> resumeScanner() =>
      _command((callback, _) => ZebraBridge.resumeScanner(callback));

  @override
  Future<ZebraInfo> getInfo() {
    final completer = Completer<ZebraInfo>();
    _call(completer, (arena) {
      final callback = InfoCallback.implement(
        $InfoCallback(
          onInfo: (isZebra, installed, version, enabled, scanAction) =>
              completer.complete(
                ZebraInfo(
                  isZebraDevice: isZebra,
                  dataWedgeInstalled: installed,
                  dataWedgeVersion: version?.toDartString(
                    releaseOriginal: true,
                  ),
                  dataWedgeEnabled: enabled < 0 ? null : enabled == 1,
                  scanAction: scanAction.toDartString(releaseOriginal: true),
                ),
              ),
          onInfo$async: true,
        ),
      )..releasedBy(arena);
      ZebraBridge.getInfo(callback);
    });
    return completer.future;
  }

  @override
  void setScanListener(ZebraNativeScanCallback? onScan) {
    if (!Platform.isAndroid) return;
    final previous = _scanListener;
    _scanListener = onScan == null
        ? null
        : ScanListener.implement(
            $ScanListener(
              onScan: (data, labelType, source, timestampMs) => onScan(
                data.toDartString(releaseOriginal: true),
                labelType?.toDartString(releaseOriginal: true),
                source?.toDartString(releaseOriginal: true),
                timestampMs,
              ),
              onScan$async: true,
            ),
          );
    ZebraBridge.scanListener = _scanListener;
    previous?.release();
  }

  Future<void> _command(
    void Function(CommandCallback callback, Arena arena) send,
  ) {
    final completer = Completer<void>();
    _call(completer, (arena) {
      final callback = CommandCallback.implement(
        $CommandCallback(
          onDone: completer.complete,
          onDone$async: true,
          onError: (code, message) =>
              completer.completeError(_exception(code, message)),
          onError$async: true,
        ),
      )..releasedBy(arena);
      send(callback, arena);
    });
    return completer.future;
  }

  /// Executa a chamada JNI. O callback já foi entregue ao Kotlin, que o mantém
  /// vivo até responder; a referência local do Dart é liberada pela [Arena].
  void _call<T>(Completer<T> completer, void Function(Arena arena) send) {
    if (!Platform.isAndroid) {
      // Mesmo comportamento do MissingPluginException da versão com canais.
      completer.completeError(
        const ZebraException(
          ZebraException.dataWedgeNotAvailable,
          'Plugin Zebra indisponível nesta plataforma',
        ),
      );
      return;
    }
    try {
      using(send);
    } on JThrowable catch (throwable) {
      if (!completer.isCompleted) {
        completer.completeError(
          ZebraException(_commandFailed, throwable.message),
        );
      }
    }
  }

  static JArray<JString>? _stringArray(List<String>? values, Arena arena) {
    if (values == null) return null;
    final strings = [for (final value in values) value.toJString()];
    for (final string in strings) {
      string.releasedBy(arena);
    }
    return JArray.of(JString.type, strings)..releasedBy(arena);
  }

  static ZebraException _exception(JString code, JString message) =>
      ZebraException(
        code.toDartString(releaseOriginal: true),
        message.toDartString(releaseOriginal: true),
      );
}
