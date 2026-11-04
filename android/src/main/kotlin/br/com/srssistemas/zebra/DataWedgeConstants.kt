package br.com.srssistemas.zebra

/**
 * Chaves e valores da API de intents do DataWedge.
 * Referência: https://techdocs.zebra.com/datawedge/latest/guide/api/
 */
internal object DW {
    const val PACKAGE = "com.symbol.datawedge"

    // Envio de comandos
    const val ACTION = "com.symbol.datawedge.api.ACTION"
    const val SET_CONFIG = "com.symbol.datawedge.api.SET_CONFIG"
    const val GET_PROFILES_LIST = "com.symbol.datawedge.api.GET_PROFILES_LIST"
    const val GET_VERSION_INFO = "com.symbol.datawedge.api.GET_VERSION_INFO"
    const val GET_DATAWEDGE_STATUS = "com.symbol.datawedge.api.GET_DATAWEDGE_STATUS"
    const val SOFT_SCAN_TRIGGER = "com.symbol.datawedge.api.SOFT_SCAN_TRIGGER"
    const val SCANNER_INPUT_PLUGIN = "com.symbol.datawedge.api.SCANNER_INPUT_PLUGIN"
    const val SEND_RESULT = "SEND_RESULT"
    const val COMMAND_IDENTIFIER = "COMMAND_IDENTIFIER"
    const val SEND_RESULT_COMPLETE = "COMPLETE_RESULT"
    const val SEND_RESULT_LAST = "LAST_RESULT"

    // Respostas
    const val RESULT_ACTION = "com.symbol.datawedge.api.RESULT_ACTION"
    const val RESULT = "RESULT"
    const val RESULT_SUCCESS = "SUCCESS"
    const val RESULT_FAILURE = "FAILURE"
    const val RESULT_INFO = "RESULT_INFO"
    const val RESULT_CODE = "RESULT_CODE"
    const val RESULT_LIST = "RESULT_LIST"
    const val RESULT_GET_PROFILES_LIST = "com.symbol.datawedge.api.RESULT_GET_PROFILES_LIST"
    const val RESULT_GET_VERSION_INFO = "com.symbol.datawedge.api.RESULT_GET_VERSION_INFO"
    const val RESULT_GET_DATAWEDGE_STATUS = "com.symbol.datawedge.api.RESULT_GET_DATAWEDGE_STATUS"
    const val VERSION_DATAWEDGE = "DATAWEDGE"

    // Perfil (SET_CONFIG)
    const val PROFILE_NAME = "PROFILE_NAME"
    const val PROFILE_ENABLED = "PROFILE_ENABLED"
    const val CONFIG_MODE = "CONFIG_MODE"
    const val CONFIG_MODE_CREATE_IF_NOT_EXIST = "CREATE_IF_NOT_EXIST"
    const val PLUGIN_CONFIG = "PLUGIN_CONFIG"
    const val PLUGIN_NAME = "PLUGIN_NAME"
    const val RESET_CONFIG = "RESET_CONFIG"
    const val PARAM_LIST = "PARAM_LIST"
    const val APP_LIST = "APP_LIST"
    const val PACKAGE_NAME = "PACKAGE_NAME"
    const val ACTIVITY_LIST = "ACTIVITY_LIST"
    const val SIGNATURE = "SIGNATURE"

    const val PLUGIN_BARCODE = "BARCODE"
    const val PLUGIN_INTENT = "INTENT"
    const val PLUGIN_KEYSTROKE = "KEYSTROKE"

    // BARCODE params
    const val SCANNER_SELECTION = "scanner_selection"
    const val SCANNER_INPUT_ENABLED = "scanner_input_enabled"
    const val SAME_BARCODE_TIMEOUT = "same_barcode_timeout"
    const val DECODE_AUDIO_FEEDBACK_URI = "decode_audio_feedback_uri"
    const val DECODE_HAPTIC_FEEDBACK = "decode_haptic_feedback"

    // INTENT params
    const val INTENT_OUTPUT_ENABLED = "intent_output_enabled"
    const val INTENT_ACTION = "intent_action"
    const val INTENT_CATEGORY = "intent_category"
    const val INTENT_DELIVERY = "intent_delivery"
    const val INTENT_DELIVERY_BROADCAST = "2"
    const val INTENT_COMPONENT_INFO = "intent_component_info"

    // KEYSTROKE params
    const val KEYSTROKE_OUTPUT_ENABLED = "keystroke_output_enabled"

    // SCANNER_INPUT_PLUGIN / SOFT_SCAN_TRIGGER valores
    const val SUSPEND_PLUGIN = "SUSPEND_PLUGIN"
    const val RESUME_PLUGIN = "RESUME_PLUGIN"
    const val START_SCANNING = "START_SCANNING"
    const val STOP_SCANNING = "STOP_SCANNING"
    const val TOGGLE_SCANNING = "TOGGLE_SCANNING"

    // Extras da intent de leitura
    const val DATA_STRING = "com.symbol.datawedge.data_string"
    const val LABEL_TYPE = "com.symbol.datawedge.label_type"
    const val SOURCE = "com.symbol.datawedge.source"
    const val DECODED_MODE = "com.symbol.datawedge.decoded_mode"

    const val CATEGORY_DEFAULT = "android.intent.category.DEFAULT"
}

/** Códigos de erro expostos ao Dart (ZebraException.code). */
internal object ErrorCodes {
    const val DATAWEDGE_NOT_AVAILABLE = "DATAWEDGE_NOT_AVAILABLE"
    const val TIMEOUT = "TIMEOUT"
    const val COMMAND_FAILED = "COMMAND_FAILED"
    const val INVALID_ARGUMENT = "INVALID_ARGUMENT"
}
