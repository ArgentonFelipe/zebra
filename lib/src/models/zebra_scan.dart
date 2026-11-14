import 'zebra_symbology.dart';

/// Uma leitura entregue pelo DataWedge para o app em primeiro plano.
class ZebraScan {
  const ZebraScan({
    required this.data,
    required this.rawData,
    required this.symbology,
    required this.receivedAt,
    this.labelType,
    this.source,
  });

  /// Conteúdo lido, sem espaços/quebras de linha nas pontas. Nunca vazio.
  final String data;

  /// Conteúdo exatamente como enviado pelo DataWedge.
  final String rawData;

  final ZebraSymbology symbology;

  /// Valor bruto de `label_type` (ex.: `LABEL-TYPE-EAN13`).
  final String? labelType;

  /// Origem da leitura (`scanner`, `msr`, `serial`...).
  final String? source;

  final DateTime receivedAt;

  /// Retorna nulo quando o mapa não contém uma leitura válida (ausente ou vazia).
  static ZebraScan? tryParse(Object? event) {
    if (event is! Map) return null;
    final raw = event['data'];
    if (raw is! String) return null;
    final timestamp = event['timestamp'];
    return fromFields(
      rawData: raw,
      labelType: event['labelType'] as String?,
      source: event['source'] as String?,
      timestampMs: timestamp is int ? timestamp : null,
    );
  }

  /// Monta a leitura a partir dos campos entregues pelo nativo. Retorna nulo
  /// quando [rawData] é vazio depois do trim. Sem [timestampMs], usa o agora.
  static ZebraScan? fromFields({
    required String rawData,
    String? labelType,
    String? source,
    int? timestampMs,
  }) {
    final data = rawData.trim();
    if (data.isEmpty) return null;
    return ZebraScan(
      data: data,
      rawData: rawData,
      symbology: ZebraSymbology.fromLabelType(labelType),
      labelType: labelType,
      source: source,
      receivedAt: timestampMs != null
          ? DateTime.fromMillisecondsSinceEpoch(timestampMs)
          : DateTime.now(),
    );
  }

  @override
  String toString() => 'ZebraScan($data, ${symbology.name})';
}
