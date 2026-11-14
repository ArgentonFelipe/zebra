// Gera os bindings Dart <-> Kotlin (JNIgen) em lib/src/jni/zebra_bindings.dart.
//
// O JNIgen lê as classes compiladas do plugin. Rode, nesta ordem:
//   cd example && fvm flutter pub get
//   cd example/android && ./gradlew :zebra:compileReleaseKotlin
//   fvm dart run tool/jnigen.dart
//
// Rode de novo sempre que mudar a API pública de ZebraBridge ou dos callbacks.
import 'package:jnigen/jnigen.dart';

Future<void> main() async {
  const package = 'br.com.srssistemas.zebra';
  await JniGenerator(
    input: Input(
      classes: [
        '$package.ZebraBridge',
        '$package.ProfileCallback',
        '$package.InfoCallback',
        '$package.CommandCallback',
        '$package.ScanListener',
      ],
      backend: SummarizerBackend.asm,
      androidSdk: AndroidSdk(
        addGradleDeps: true,
        androidExample: Uri.directory('example/'),
      ),
    ),
    output: Output(
      dart: DartOutput(
        path: Uri.file('lib/src/jni/zebra_bindings.dart'),
        structure: OutputStructure.singleFile,
      ),
    ),
  ).generate();
}
