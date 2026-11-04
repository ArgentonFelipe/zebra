import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:zebra/zebra.dart';
import 'package:zebra/zebra_method_channel.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  final messenger =
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
  const codec = StandardMethodCodec();
  late MethodChannelZebra platform;
  late List<MethodCall> calls;

  setUp(() {
    platform = MethodChannelZebra();
    calls = [];
  });

  tearDown(() {
    messenger.setMockMethodCallHandler(MethodChannelZebra.methodChannel, null);
    messenger.setMockMessageHandler(MethodChannelZebra.scanChannel.name, null);
  });

  void mockMethods(Future<Object?> Function(MethodCall call) handler) {
    messenger.setMockMethodCallHandler(MethodChannelZebra.methodChannel,
        (call) {
      calls.add(call);
      return handler(call);
    });
  }

  group('configureProfile', () {
    test('envia a configuração e converte o resultado', () async {
      mockMethods(
        (_) async => {
          'applied': true,
          'scanAction': 'br.com.app.zebra.SCAN',
          'secureDelivery': true,
        },
      );

      final result = await platform.configureProfile(
        const ZebraProfileConfig(profileName: 'TDRConfereEntrada'),
      );

      expect(calls.single.method, 'configureProfile');
      expect(
        (calls.single.arguments as Map)['profileName'],
        'TDRConfereEntrada',
      );
      expect(result.applied, isTrue);
      expect(result.scanAction, 'br.com.app.zebra.SCAN');
    });

    test('converte PlatformException em ZebraException com o código', () async {
      mockMethods(
        (_) async => throw PlatformException(
          code: 'APP_ALREADY_ASSOCIATED',
          message: 'x',
        ),
      );

      await expectLater(
        platform.configureProfile(const ZebraProfileConfig(profileName: 'P')),
        throwsA(
          isA<ZebraException>()
              .having((e) => e.code, 'code', 'APP_ALREADY_ASSOCIATED'),
        ),
      );
    });

    test('config inválida falha antes de chamar o nativo', () async {
      mockMethods((_) async => null);
      await expectLater(
        platform.configureProfile(const ZebraProfileConfig(profileName: '')),
        throwsArgumentError,
      );
      expect(calls, isEmpty);
    });

    test('sem implementação nativa lança DATAWEDGE_NOT_AVAILABLE', () async {
      await expectLater(
        platform.softTrigger(ZebraTrigger.start),
        throwsA(
          isA<ZebraException>().having(
            (e) => e.code,
            'code',
            ZebraException.dataWedgeNotAvailable,
          ),
        ),
      );
    });
  });

  test('softTrigger, suspend e resume chamam os métodos corretos', () async {
    mockMethods((_) async => null);

    await platform.softTrigger(ZebraTrigger.toggle);
    await platform.suspendScanner();
    await platform.resumeScanner();

    expect(
      calls.map((c) => c.method),
      ['softTrigger', 'suspendScanner', 'resumeScanner'],
    );
    expect(calls.first.arguments, {'action': 'toggle'});
  });

  test('getInfo converte o mapa', () async {
    mockMethods(
      (_) async => {
        'isZebraDevice': true,
        'dataWedgeInstalled': true,
        'dataWedgeVersion': '15.0.28',
        'dataWedgeEnabled': true,
        'scanAction': 'a.zebra.SCAN',
      },
    );

    final info = await platform.getInfo();
    expect(info.isZebraDevice, isTrue);
    expect(info.dataWedgeVersion, '15.0.28');
    expect(info.dataWedgeEnabled, isTrue);
  });

  group('scans', () {
    late int listenCount;
    late int cancelCount;

    setUp(() {
      listenCount = 0;
      cancelCount = 0;
      messenger.setMockMessageHandler(MethodChannelZebra.scanChannel.name,
          (message) async {
        final call = codec.decodeMethodCall(message);
        if (call.method == 'listen') listenCount++;
        if (call.method == 'cancel') cancelCount++;
        return codec.encodeSuccessEnvelope(null);
      });
    });

    Future<void> emit(Object? event) => messenger.handlePlatformMessage(
          MethodChannelZebra.scanChannel.name,
          codec.encodeSuccessEnvelope(event),
          (_) {},
        );

    test('vários ouvintes compartilham uma única escuta nativa', () async {
      final a = <String>[];
      final b = <String>[];
      final subA = platform.scans.listen((s) => a.add(s.data));
      final subB = platform.scans.listen((s) => b.add(s.data));
      await pumpEventQueue();

      await emit({'data': '123', 'labelType': 'LABEL-TYPE-CODE128'});
      await pumpEventQueue();

      expect(listenCount, 1);
      expect(a, ['123']);
      expect(b, ['123']);

      await subA.cancel();
      await pumpEventQueue();
      expect(cancelCount, 0, reason: 'ainda há um ouvinte');

      await subB.cancel();
      await pumpEventQueue();
      expect(cancelCount, 1);
    });

    test('descarta eventos vazios ou inválidos', () async {
      final received = <ZebraScan>[];
      final sub = platform.scans.listen(received.add);
      await pumpEventQueue();

      await emit('Nothing');
      await emit({'data': '  '});
      await emit({'data': ' ABC '});
      await pumpEventQueue();

      expect(received.map((s) => s.data), ['ABC']);
      await sub.cancel();
    });

    test('Zebra().scans usa a mesma instância de stream', () {
      expect(identical(platform.scans, platform.scans), isTrue);
    });
  });
}
