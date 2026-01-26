import 'dart:async';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'painters/holistic_painter.dart';
import 'settings_screen.dart';

class MainScreen extends StatefulWidget {
  const MainScreen({Key? key}) : super(key: key);

  @override
  _MainScreenState createState() => _MainScreenState();
}

class _MainScreenState extends State<MainScreen> {
  // Canales de comunicación con Nativo
  static const MethodChannel _methodChannel = MethodChannel('com.example.sign_language_app/camera');
  static const EventChannel _eventChannel = EventChannel('com.example.sign_language_app/landmarks');

  // Estado de la UI
  int? _textureId;
  String _currentEmotion = "NEUTRO";
  double _fps = 0.0;
  Map<String, dynamic>? _currentLandmarks;
  
  // Helpers para FPS
  int _frameCount = 0;
  DateTime _lastFrameTime = DateTime.now();

  // Configuración actual (Default)
  int _width = 1920;
  int _height = 1080;
  int _targetFps = 30;

  @override
  void initState() {
    super.initState();
    _connectToCamera();
    _startListeningToLandmarks();
  }

  Future<void> _connectToCamera() async {
    try {
      // 1. Escanear (Simple auto-connect logic para demo)
      // O pedir permiso manualmente si no hay.
      // Asumimos flujo: Solicitar Permiso -> Nativo Conecta -> Retorna TextureID
      
      // Nota: En la implementación anterior, el usuario pulsaba "Conectar".
      // Aquí intentamos automatizar o pedir al inicio.
      await _methodChannel.invokeMethod('requestUsbPermission');
    } catch (e) {
      debugPrint("Error conectando cámara: $e");
    }
  }
  
  void _startListeningToLandmarks() {
    _eventChannel.receiveBroadcastStream().listen((dynamic event) {
      // Event estructura esperada: { "emotion": "FELIZ", "landmarks": { ... } }
      if (event is Map) {
         setState(() {
           _currentEmotion = event['emotion'] ?? "NEUTRO";
           _currentLandmarks = Map<String, dynamic>.from(event['landmarks'] ?? {});
           if (_frameCount % 30 == 0) { // Log once per second approx
             debugPrint("Dart Landmarks keys: ${_currentLandmarks?.keys.toList()}");
           }
           
           // Cálculo de FPS basado en frames recibidos
           _frameCount++;
           final now = DateTime.now();
           final diff = now.difference(_lastFrameTime).inMilliseconds;
           if (diff > 1000) {
             _fps = _frameCount * 1000 / diff;
             _frameCount = 0;
             _lastFrameTime = now;
           }
         });
      }
    }, onError: (e) {
      debugPrint("Error recibiendo landmarks: $e");
    });
    
    // Escuchar cambios de TextureID si el nativo lo enviara (opcional)
    // Por ahora, asumimos que 'onTextureChange' podría venir por MethodChannel o callback
    // Simplemente consultamos o esperamos que el nativo configure la view.
    // ACTUALIZACIÓN: Renderizado de Texture requiere un ID. 
    // Vamos a implementar un listener de MethodCallHandler desde Nativo -> Flutter
    _methodChannel.setMethodCallHandler((call) async {
       if (call.method == 'onTextureId') {
         setState(() {
           _textureId = call.arguments as int;
         });
       }
    });
  }

  Future<void> _openSettings() async {
    final result = await Navigator.push(
      context,
      MaterialPageRoute(
        builder: (context) => SettingsScreen(
          currentWidth: _width,
          currentHeight: _height,
          currentFps: _targetFps,
        ),
      ),
    );

    if (result != null && result is Map) {
      setState(() {
        _width = result['width'];
        _height = result['height'];
        _targetFps = result['fps'];
      });
      
      // Enviar nueva configuración al nativo
      await _methodChannel.invokeMethod('updateConfig', {
        'width': _width,
        'height': _height,
        'fps': _targetFps
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Colors.black,
      body: Stack(
        fit: StackFit.expand,
        children: [
          // 1. Capa de Video
          Center(
            child: _textureId == null
                ? const Text("Esperando Cámara...", style: TextStyle(color: Colors.white))
                : AspectRatio(
                    aspectRatio: _width / _height,
                    child: Texture(textureId: _textureId!),
                  ),
          ),
          
          // 2. Capa de Overlay (Esqueleto)
          CustomPaint(
            painter: HolisticPainter(_currentLandmarks),
            child: Container(),
          ),

          // 3. HUD Superior
          Positioned(
            top: 40, 
            left: 20, 
            right: 20,
            child: Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                // Botón Ajustes
                IconButton(
                  icon: const Icon(Icons.settings, color: Colors.white, size: 32),
                  onPressed: _openSettings,
                ),
                
                // Emoción Central
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 8),
                  decoration: BoxDecoration(
                    color: Colors.black54,
                    borderRadius: BorderRadius.circular(20),
                  ),
                  child: Text(
                    _currentEmotion,
                    style: const TextStyle(
                      color: Colors.yellowAccent, 
                      fontSize: 32, 
                      fontWeight: FontWeight.bold,
                      letterSpacing: 2.0
                    ),
                  ),
                ),
                
                // Contador FPS
                Container(
                  padding: const EdgeInsets.all(8),
                  decoration: BoxDecoration(
                    color: Colors.black54,
                    borderRadius: BorderRadius.circular(8),
                  ),
                  child: Text(
                    "FPS: ${_fps.toStringAsFixed(1)}",
                    style: const TextStyle(
                      color: Colors.white, 
                      fontSize: 16,
                      fontFamily: 'Monospace'
                    ),
                  ),
                ),
              ],
            ),
          ),
          
          // Botón flotante para reconectar manualmente si falla
          if (_textureId == null)
            Positioned(
              bottom: 40,
              right: 20,
              child: FloatingActionButton(
                onPressed: _connectToCamera,
                child: const Icon(Icons.usb),
              ),
            )
        ],
      ),
    );
  }
}
