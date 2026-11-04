import 'package:flutter_test/flutter_test.dart';
import 'package:zebra/zebra.dart';

void main() {
  group('ZebraSymbology.fromLabelType', () {
    test('mapeia os label types do DataWedge', () {
      expect(
        ZebraSymbology.fromLabelType('LABEL-TYPE-EAN13'),
        ZebraSymbology.ean13,
      );
      expect(
        ZebraSymbology.fromLabelType('LABEL-TYPE-EAN128'),
        ZebraSymbology.gs1128,
      );
      expect(
        ZebraSymbology.fromLabelType('LABEL-TYPE-GS1-DATAMATRIX'),
        ZebraSymbology.dataMatrix,
      );
      expect(
        ZebraSymbology.fromLabelType('LABEL-TYPE-GS1-DATABAR-EXP'),
        ZebraSymbology.gs1DataBar,
      );
      expect(
        ZebraSymbology.fromLabelType('label-type-qrcode'),
        ZebraSymbology.qrCode,
      );
    });

    test('desconhecido ou nulo vira unknown', () {
      expect(ZebraSymbology.fromLabelType(null), ZebraSymbology.unknown);
      expect(
        ZebraSymbology.fromLabelType('LABEL-TYPE-MAXICODE'),
        ZebraSymbology.unknown,
      );
    });
  });

  group('ZebraScan.tryParse', () {
    test('converte o mapa nativo e aplica trim preservando o bruto', () {
      final scan = ZebraScan.tryParse({
        'data': ' 7891234567895\n',
        'labelType': 'LABEL-TYPE-EAN13',
        'source': 'scanner',
        'timestamp': 1700000000000,
      })!;

      expect(scan.data, '7891234567895');
      expect(scan.rawData, ' 7891234567895\n');
      expect(scan.symbology, ZebraSymbology.ean13);
      expect(scan.source, 'scanner');
      expect(
        scan.receivedAt,
        DateTime.fromMillisecondsSinceEpoch(1700000000000),
      );
    });

    test('preserva o separador GS (FNC1) dentro de códigos GS1', () {
      final scan =
          ZebraScan.tryParse({'data': '0107891234567895\u001D10LOTE1'})!;
      expect(scan.data, '0107891234567895\u001D10LOTE1');
    });

    test('descarta eventos inválidos ou vazios', () {
      expect(ZebraScan.tryParse(null), isNull);
      expect(ZebraScan.tryParse('Nothing'), isNull);
      expect(ZebraScan.tryParse({'data': null}), isNull);
      expect(ZebraScan.tryParse({'data': '   \n'}), isNull);
    });
  });

  group('ZebraProfileConfig', () {
    test('toMap sem opções mantém padrões do DataWedge', () {
      final map = const ZebraProfileConfig(profileName: ' Perfil ').toMap();
      expect(map['profileName'], 'Perfil');
      expect(map['decoders'], isNull);
      expect(map['sameBarcodeTimeoutMs'], isNull);
      expect(map['secureDelivery'], isTrue);
      expect(map['force'], isFalse);
    });

    test('enabledSymbologies habilita as listadas e desabilita as demais', () {
      final decoders = const ZebraProfileConfig(
        profileName: 'P',
        enabledSymbologies: {ZebraSymbology.ean13, ZebraSymbology.code128},
      ).toMap()['decoders']! as Map<String, bool>;

      expect(decoders['decoder_ean13'], isTrue);
      expect(decoders['decoder_code128'], isTrue);
      expect(decoders['decoder_qrcode'], isFalse);
      expect(decoders.keys, everyElement(startsWith('decoder_')));
    });

    test('validate rejeita valores inválidos', () {
      expect(
        () => const ZebraProfileConfig(profileName: ' ').validate(),
        throwsArgumentError,
      );
      expect(
        () => const ZebraProfileConfig(
          profileName: 'P',
          sameBarcodeTimeout: Duration(seconds: 6),
        ).validate(),
        throwsArgumentError,
      );
      expect(
        () => const ZebraProfileConfig(
          profileName: 'P',
          enabledSymbologies: {ZebraSymbology.gs1128},
        ).validate(),
        throwsArgumentError,
      );
      expect(
        () => const ZebraProfileConfig(
          profileName: 'P',
          sameBarcodeTimeout: Duration(milliseconds: 500),
          enabledSymbologies: {ZebraSymbology.ean13},
        ).validate(),
        returnsNormally,
      );
    });
  });
}
