package br.com.srssistemas.zebra

import androidx.annotation.NonNull

import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result

class ZebraPlugin: FlutterPlugin, MethodCallHandler {
  private lateinit var channel : MethodChannel
  private lateinit var scanChannel: EventChannel
  private lateinit var context: Context
  private val START_SCAN_CHANNEL = "be.com.srssistemas/start_scan"
  private val SCAN_CHANNEL = "br.com.srssistemas/scan"
  private val CHANNEL = "br.com.srssistemas/zebra"

  override fun onAttachedToEngine(@NonNull flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
    context = flutterPluginBinding.applicationContext

    channel = MethodChannel(flutterPluginBinding.binaryMessenger, CHANNEL)
    channel.setMethodCallHandler(this)

    scanChannel = EventChannel(flutterPluginBinding.binaryMessenger, SCAN_CHANNEL)

    scanChannel.setStreamHandler(object: StreamHandler {
      private var scanBroadcast : BroadcastReceiver? = null
      override fun onListen(arguments: Any?, events: EventSink?) {
        scanBroadcast = createBroadcastReceiver(events)
        val scanIntent = IntentFilter()
        scanIntent.addAction(Helpers.SCAN_ACTION)
        scanIntent.addAction(Helpers.RETURN_ACTION_COMMAND)
        scanIntent.addCategory(Helpers.INTENT_CATEGORY_DEFAULT)
        context.registerReceiver(scanBroadcast, scanIntent)
      }

      override fun onCancel(arguments: Any?) {
        context.unregisterReceiver(scanBroadcast)
        scanBroadcast = null
      }
    })
  }

  override fun onMethodCall(@NonNull call: MethodCall, @NonNull result: Result) {
    if (call.method == "getPlatformVersion") {
      result.success("Android ${android.os.Build.VERSION.RELEASE}")
    } else if(call.method == "createProfile"){
      val profileName = call.arguments.toString()
      createProfile(profileName)
    }
    else if(call.method == "startBarcodeScanning"){
      startBarcodeScanning()
    }
    else{
      result.notImplemented()
    }
  }

  override fun onDetachedFromEngine(@NonNull binding: FlutterPlugin.FlutterPluginBinding) {
    channel.setMethodCallHandler(null)
    scanChannel.setStreamHandler(null)
  }
  
}
