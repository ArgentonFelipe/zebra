import 'package:flutter_test/flutter_test.dart';
import 'package:zebra/zebra.dart';
import 'package:zebra/zebra_platform_interface.dart';
import 'package:zebra/zebra_method_channel.dart';
import 'package:plugin_platform_interface/plugin_platform_interface.dart';

class MockZebraPlatform
    with MockPlatformInterfaceMixin
    implements ZebraPlatform {
  @override
  Future<String?> getPlatformVersion() => Future.value('42');
}

void main() {
  final ZebraPlatform initialPlatform = ZebraPlatform.instance;

  test('$MethodChannelZebra is the default instance', () {
    expect(initialPlatform, isInstanceOf<MethodChannelZebra>());
  });

  test('getPlatformVersion', () async {
    Zebra zebraPlugin = const Zebra();
    MockZebraPlatform fakePlatform = MockZebraPlatform();
    ZebraPlatform.instance = fakePlatform;

    expect(await zebraPlugin.getPlatformVersion(), '42');
  });
}
