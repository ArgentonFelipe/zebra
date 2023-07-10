import 'package:flutter/services.dart';

import 'zebra_platform_interface.dart';

class Zebra {
  Future<String?> getPlatformVersion() {
    return ZebraPlatform.instance.getPlatformVersion();
  }

  static const _methodChannelName = 'br.com.srssistemas/zebra';
  static const _channel = MethodChannel(_methodChannelName);

  static const _scanChannelName = "br.com.srssistemas/scan";
  static const _scanChannel = EventChannel(_scanChannelName);

  const Zebra();

  Future<void> createProfile({required String profileName}) async {
    await _channel.invokeMethod('createProfile', profileName);
  }

  Stream scanBarcode() {
    return _scanChannel.receiveBroadcastStream();
  }

  Future<void> startBarcodeScanning() async {
    await _channel.invokeMethod('startBarcodeScanning');
  }
}
