import 'package:flutter/services.dart';

import 'zebra_platform_interface.dart';

class MethodChannelZebra extends ZebraPlatform {
  static const _methodChannelName = 'br.com.srssistemas/zebra';
  static const _channel = MethodChannel(_methodChannelName);

  static const _scanChannelName = 'br.com.srssistemas/scan';
  static const _scanChannel = EventChannel(_scanChannelName);

  @override
  Future<void> createProfile({required String profileName}) async {
    await _channel.invokeMethod('createProfile', profileName);
  }

  @override
  Stream scanBarcode() {
    return _scanChannel.receiveBroadcastStream();
  }

  @override
  Future<void> startBarcodeScanning() async {
    await _channel.invokeMethod('startBarcodeScanning');
  }
}
