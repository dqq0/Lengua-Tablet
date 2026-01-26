import 'package:flutter/material.dart';

class SettingsScreen extends StatefulWidget {
  final int currentWidth;
  final int currentHeight;
  final int currentFps;

  const SettingsScreen({
    Key? key,
    required this.currentWidth,
    required this.currentHeight,
    required this.currentFps,
  }) : super(key: key);

  @override
  _SettingsScreenState createState() => _SettingsScreenState();
}

class _SettingsScreenState extends State<SettingsScreen> {
  late int _selectedWidth;
  late int _selectedHeight;
  late int _selectedFps;

  final List<List<int>> _resolutions = [
    [1920, 1080],
    [1280, 720],
    [640, 480],
  ];

  final List<int> _fpsOptions = [30, 60];

  @override
  void initState() {
    super.initState();
    _selectedWidth = widget.currentWidth;
    _selectedHeight = widget.currentHeight;
    _selectedFps = widget.currentFps;
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Configuración de Cámara'),
        backgroundColor: Colors.black,
      ),
      body: Padding(
        padding: const EdgeInsets.all(24.0),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text(
              'Resolución',
              style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold),
            ),
            const SizedBox(height: 10),
            DropdownButton<String>(
              isExpanded: true,
              value: '$_selectedWidth x $_selectedHeight',
              items: _resolutions.map((res) {
                return DropdownMenuItem<String>(
                  value: '${res[0]} x ${res[1]}',
                  child: Text('${res[0]} x ${res[1]}'),
                );
              }).toList(),
              onChanged: (value) {
                if (value != null) {
                  final parts = value.split(' x ');
                  setState(() {
                    _selectedWidth = int.parse(parts[0]);
                    _selectedHeight = int.parse(parts[1]);
                  });
                }
              },
            ),
            const SizedBox(height: 30),
            const Text(
              'FPS Objetivo',
              style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold),
            ),
            const SizedBox(height: 10),
            DropdownButton<int>(
              isExpanded: true,
              value: _selectedFps,
              items: _fpsOptions.map((fps) {
                return DropdownMenuItem<int>(
                  value: fps,
                  child: Text('$fps FPS'),
                );
              }).toList(),
              onChanged: (value) {
                if (value != null) {
                  setState(() {
                    _selectedFps = value;
                  });
                }
              },
            ),
            const Spacer(),
            SizedBox(
              width: double.infinity,
              height: 50,
              child: ElevatedButton(
                style: ElevatedButton.styleFrom(
                  backgroundColor: Colors.blueAccent,
                ),
                onPressed: () {
                  // Retornar los nuevos valores
                  Navigator.pop(context, {
                    'width': _selectedWidth,
                    'height': _selectedHeight,
                    'fps': _selectedFps,
                  });
                },
                child: const Text(
                  'APLICAR CAMBIOS',
                  style: TextStyle(color: Colors.white, fontSize: 16),
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
