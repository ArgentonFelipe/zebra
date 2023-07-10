import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

import 'zebra_platform_interface.dart';

/// An implementation of [ZebraPlatform] that uses method channels.
class MethodChannelZebra extends ZebraPlatform {
  /// The method channel used to interact with the native platform.
  @visibleForTesting
  final methodChannel = const MethodChannel('zebra');

  @override
  Future<String?> getPlatformVersion() async {
    final version = await methodChannel.invokeMethod<String>('getPlatformVersion');
    return version;
  }
}
