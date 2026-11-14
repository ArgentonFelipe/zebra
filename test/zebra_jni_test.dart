import 'package:flutter_test/flutter_test.dart';
import 'package:zebra/src/native/jni_zebra_native.dart';
import 'package:zebra/src/native/zebra_native.dart';
import 'package:zebra/zebra.dart';
import 'package:zebra/zebra_jni.dart';

class FakeZebraNative implements ZebraNative {
  final calls = <String>[];
  ZebraNativeProfileRequest? lastRequest;
  String? lastTrigger;
  Object? error;
  ZebraNativeScanCallback? listener;
  int listenCount = 0;
  int cancelCount = 0;

  Future<T> _reply<T>(String method, T value) {
    calls.add(method);
    final failure = error;
    return failure == null ? Future.value(value) : Future.error(failure);
  }

  @override
  Future<ZebraProfileResult> configureProfile(
    ZebraNativeProfileRequest request,
  ) {
    lastRequest = request;
    return _reply(
      'configureProfile',
      const ZebraProfileResult(
        applied: true,
        scanAction: 'br.com.app.zebra.SCAN',
        secureDelivery: true,
      ),
    );
  }

  @override
  Future<void> softTrigger(String action) {
    lastTrigger = action;
    return _reply('softTrigger', null);
  }

  @override
  Future<void> suspendScanner() => _reply('suspendScanner', null);

  @override
  Future<void> resumeScanner() => _reply('resumeScanner', null);

  @override
  Future<ZebraInfo> getInfo() => _reply(
    'getInfo',
    const ZebraInfo(
      isZebraDevice: true,
      dataWedgeInstalled: true,
      dataWedgeVersion: '15.0.28',
      dataWedgeEnabled: true,
      scanAction: 'a.zebra.SCAN',
    ),
  );

  @override
  void setScanListener(ZebraNativeScanCallback? onScan) {
    if (onScan == null) {
      cancelCount++;
    } else {
      listenCount++;
    }
    listener = onScan;
  }

  void emit(String data, {String? labelType}) =>
      listener?.call(data, labelType, 'scanner', 1700000000000);
}

void main() {
  late FakeZebraNative native;
  late JniZebra platform;

  setUp(() {
    native = FakeZebraNative();
    platform = JniZebra(native: native);
  });

  group('configureProfile', () {
    test('envia a configuração e devolve o resultado', () async {
      final result = await platform.configureProfile(
        const ZebraProfileConfig(profileName: ' TDRConfereEntrada '),
      );

      expect(native.calls, ['configureProfile']);
      expect(native.lastRequest!.profileName, 'TDRConfereEntrada');
      expect(result.applied, isTrue);
      expect(result.scanAction, 'br.com.app.zebra.SCAN');
    });

    test('propaga a ZebraException do nativo com o código', () async {
      native.error = const ZebraException('APP_ALREADY_ASSOCIATED', 'x');

      await expectLater(
        platform.configureProfile(const ZebraProfileConfig(profileName: 'P')),
        throwsA(
          isA<ZebraException>().having(
            (e) => e.code,
            'code',
            'APP_ALREADY_ASSOCIATED',
          ),
        ),
      );
    });

    test('config inválida falha antes de chamar o nativo', () async {
      expect(
        () => platform.configureProfile(
          const ZebraProfileConfig(profileName: ''),
        ),
        throwsArgumentError,
      );
      expect(native.calls, isEmpty);
    });
  });

  group('ZebraNativeProfileRequest.fromConfig', () {
    test('sem opções usa -1 e decoders nulos (padrões do DataWedge)', () {
      final request = ZebraNativeProfileRequest.fromConfig(
        const ZebraProfileConfig(profileName: 'P'),
      );

      expect(request.enabledDecoders, isNull);
      expect(request.disabledDecoders, isNull);
      expect(request.sameBarcodeTimeoutMs, -1);
      expect(request.hapticFeedback, -1);
      expect(request.secureDelivery, isTrue);
      expect(request.force, isFalse);
    });

    test('separa decoders habilitados e desabilitados e converte opções', () {
      final request = ZebraNativeProfileRequest.fromConfig(
        const ZebraProfileConfig(
          profileName: 'P',
          enabledSymbologies: {ZebraSymbology.ean13, ZebraSymbology.code128},
          sameBarcodeTimeout: Duration(milliseconds: 500),
          hapticFeedback: false,
          secureDelivery: false,
          force: true,
        ),
      );

      expect(
        request.enabledDecoders,
        unorderedEquals(['decoder_ean13', 'decoder_code128']),
      );
      expect(request.disabledDecoders, contains('decoder_qrcode'));
      expect(request.disabledDecoders, isNot(contains('decoder_ean13')));
      expect(request.sameBarcodeTimeoutMs, 500);
      expect(request.hapticFeedback, 0);
      expect(request.secureDelivery, isFalse);
      expect(request.force, isTrue);
    });
  });

  test('softTrigger, suspend e resume chamam os métodos corretos', () async {
    await platform.softTrigger(ZebraTrigger.toggle);
    await platform.suspendScanner();
    await platform.resumeScanner();

    expect(native.calls, ['softTrigger', 'suspendScanner', 'resumeScanner']);
    expect(native.lastTrigger, 'toggle');
  });

  test('getInfo devolve as informações do nativo', () async {
    final info = await platform.getInfo();

    expect(info.isZebraDevice, isTrue);
    expect(info.dataWedgeVersion, '15.0.28');
    expect(info.dataWedgeEnabled, isTrue);
  });

  group('scans', () {
    test('vários ouvintes compartilham uma única escuta nativa', () async {
      final a = <String>[];
      final b = <String>[];
      final subA = platform.scans.listen((s) => a.add(s.data));
      final subB = platform.scans.listen((s) => b.add(s.data));

      native.emit('123', labelType: 'LABEL-TYPE-CODE128');
      await pumpEventQueue();

      expect(native.listenCount, 1);
      expect(a, ['123']);
      expect(b, ['123']);

      await subA.cancel();
      expect(native.cancelCount, 0, reason: 'ainda há um ouvinte');

      await subB.cancel();
      expect(native.cancelCount, 1);
      expect(native.listener, isNull);
    });

    test('descarta leituras vazias e aplica trim', () async {
      final received = <ZebraScan>[];
      final sub = platform.scans.listen(received.add);

      native.emit('  ');
      native.emit(' ABC ', labelType: 'LABEL-TYPE-EAN13');
      await pumpEventQueue();

      expect(received.map((s) => s.data), ['ABC']);
      expect(received.single.rawData, ' ABC ');
      expect(received.single.symbology, ZebraSymbology.ean13);
      expect(
        received.single.receivedAt,
        DateTime.fromMillisecondsSinceEpoch(1700000000000),
      );
      await sub.cancel();
    });

    test('volta a escutar o nativo depois de todos cancelarem', () async {
      await platform.scans.listen((_) {}).cancel();
      final sub = platform.scans.listen((_) {});

      expect(native.listenCount, 2);
      await sub.cancel();
    });

    test('scans devolve sempre o mesmo stream', () {
      expect(identical(platform.scans, platform.scans), isTrue);
    });
  });

  group('JniZebraNative fora do Android', () {
    final real = JniZebraNative();
    final notAvailable = throwsA(
      isA<ZebraException>().having(
        (e) => e.code,
        'code',
        ZebraException.dataWedgeNotAvailable,
      ),
    );

    test('os comandos lançam DATAWEDGE_NOT_AVAILABLE', () async {
      await expectLater(real.softTrigger('start'), notAvailable);
      await expectLater(real.getInfo(), notAvailable);
      await expectLater(
        real.configureProfile(
          ZebraNativeProfileRequest.fromConfig(
            const ZebraProfileConfig(profileName: 'P'),
          ),
        ),
        notAvailable,
      );
    });

    test('setScanListener não faz nada (stream fica vazio)', () {
      expect(() => real.setScanListener((_, _, _, _) {}), returnsNormally);
      expect(() => real.setScanListener(null), returnsNormally);
    });
  });
}
