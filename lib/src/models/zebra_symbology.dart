/// Simbologias de código de barras mais comuns no DataWedge.
///
/// [decoderKey] é o parâmetro `decoder_*` do plugin BARCODE (nulo quando a
/// simbologia não tem decoder próprio, ex.: GS1-128 é controlado pelo Code 128).
enum ZebraSymbology {
  ean13('decoder_ean13'),
  ean8('decoder_ean8'),
  upca('decoder_upca'),
  upce('decoder_upce0'),
  code128('decoder_code128'),
  gs1128(null),
  code39('decoder_code39'),
  interleaved2of5('decoder_i2of5'),
  qrCode('decoder_qrcode'),
  dataMatrix('decoder_datamatrix'),
  pdf417('decoder_pdf417'),
  gs1DataBar('decoder_gs1_databar'),
  aztec('decoder_aztec'),
  unknown(null);

  const ZebraSymbology(this.decoderKey);

  final String? decoderKey;

  /// Converte o `com.symbol.datawedge.label_type` (ex.: `LABEL-TYPE-EAN13`).
  static ZebraSymbology fromLabelType(String? labelType) {
    final type =
        labelType?.trim().toUpperCase().replaceFirst('LABEL-TYPE-', '');
    return switch (type) {
      'EAN13' => ean13,
      'EAN8' => ean8,
      'UPCA' => upca,
      'UPCE0' || 'UPCE1' || 'UPCE' => upce,
      'CODE128' => code128,
      'EAN128' || 'GS1-128' => gs1128,
      'CODE39' => code39,
      'I2OF5' => interleaved2of5,
      'QRCODE' || 'GS1-QRCODE' => qrCode,
      'DATAMATRIX' || 'GS1-DATAMATRIX' => dataMatrix,
      'PDF417' => pdf417,
      'GS1-DATABAR' || 'GS1-DATABAR-LIM' || 'GS1-DATABAR-EXP' => gs1DataBar,
      'AZTEC' => aztec,
      _ => unknown,
    };
  }
}
