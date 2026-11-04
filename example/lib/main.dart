import 'dart:async';

import 'package:flutter/material.dart';
import 'package:zebra/zebra.dart';

void main() => runApp(const ZebraExampleApp());

class ZebraExampleApp extends StatelessWidget {
  const ZebraExampleApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Zebra Example',
      theme: ThemeData(colorSchemeSeed: Colors.indigo),
      home: const ScanPage(),
    );
  }
}

class ScanPage extends StatefulWidget {
  const ScanPage({super.key});

  @override
  State<ScanPage> createState() => _ScanPageState();
}

class _ScanPageState extends State<ScanPage> {
  final _zebra = const Zebra();
  final _scans = <ZebraScan>[];
  StreamSubscription<ZebraScan>? _subscription;
  String _status = 'Configurando perfil...';
  bool _suspended = false;

  @override
  void initState() {
    super.initState();
    _subscription = _zebra.scans.listen(
      (scan) => setState(() => _scans.insert(0, scan)),
    );
    _setup();
  }

  Future<void> _setup({bool force = false}) async {
    try {
      final result = await _zebra.configureProfile(
        ZebraProfileConfig(profileName: 'ZebraExample', force: force),
      );
      final info = await _zebra.getInfo();
      _setStatus(
        'Perfil ${result.applied ? 'aplicado' : 'já atualizado'}\n'
        'Ação: ${result.scanAction} (segura: ${result.secureDelivery})\n'
        'DataWedge ${info.dataWedgeVersion ?? '?'} - '
        'habilitado: ${info.dataWedgeEnabled ?? '?'}',
      );
    } on ZebraException catch (e) {
      _setStatus('Erro: ${e.code} - ${e.message}');
    }
  }

  Future<void> _run(Future<void> Function() action) async {
    try {
      await action();
    } on ZebraException catch (e) {
      _setStatus('Erro: ${e.code} - ${e.message}');
    }
  }

  void _setStatus(String status) {
    if (mounted) setState(() => _status = status);
  }

  @override
  void dispose() {
    _subscription?.cancel();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Zebra Example'),
        actions: [
          IconButton(
            tooltip: 'Reaplicar perfil',
            icon: const Icon(Icons.refresh),
            onPressed: () => _setup(force: true),
          ),
        ],
      ),
      body: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Padding(padding: const EdgeInsets.all(16), child: Text(_status)),
          // Campo para validar que o keystroke está desabilitado:
          // ao bipar com ele focado, nada deve ser digitado.
          const Padding(
            padding: EdgeInsets.symmetric(horizontal: 16),
            child: TextField(
              decoration: InputDecoration(labelText: 'Teste de keystroke'),
            ),
          ),
          Padding(
            padding: const EdgeInsets.all(16),
            child: Wrap(
              spacing: 8,
              runSpacing: 8,
              children: [
                FilledButton(
                  onPressed: () => _run(() => _zebra.softTrigger()),
                  child: const Text('Soft trigger'),
                ),
                OutlinedButton(
                  onPressed: () => _run(() async {
                    _suspended
                        ? await _zebra.resumeScanner()
                        : await _zebra.suspendScanner();
                    setState(() => _suspended = !_suspended);
                  }),
                  child: Text(_suspended ? 'Retomar scanner' : 'Suspender'),
                ),
                TextButton(
                  onPressed: () => setState(_scans.clear),
                  child: const Text('Limpar'),
                ),
              ],
            ),
          ),
          const Divider(height: 1),
          Expanded(
            child: ListView.builder(
              itemCount: _scans.length,
              itemBuilder: (context, index) {
                final scan = _scans[index];
                return ListTile(
                  title: Text(scan.data),
                  subtitle: Text(
                    '${scan.symbology.name} (${scan.labelType}) - '
                    '${scan.source} - ${scan.receivedAt.toIso8601String()}',
                  ),
                );
              },
            ),
          ),
        ],
      ),
    );
  }
}
