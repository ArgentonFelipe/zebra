import 'zebra_platform_interface.dart';

class Zebra {
  Future<void> createProfile({required String profileName}) {
    return ZebraPlatform.instance.createProfile(profileName: profileName);
  }

  Stream scanBarcode() {
    return ZebraPlatform.instance.scanBarcode();
  }

  Future<void> startBarcodeScanning() {
    return ZebraPlatform.instance.startBarcodeScanning();
  }
}
