import 'package:flutter/material.dart';

class HolisticPainter extends CustomPainter {
  final Map<String, dynamic>? holisticData;

  HolisticPainter(this.holisticData);

  // --- Paints ---
  static final Paint paintFace = Paint()
    ..color = Colors.greenAccent
    ..strokeWidth = 1.0
    ..style = PaintingStyle.stroke;

  static final Paint paintBodyLine = Paint()
    ..color = Colors.blueAccent
    ..strokeWidth = 3.0
    ..style = PaintingStyle.stroke
    ..strokeCap = StrokeCap.round;

  static final Paint paintBodyJoint = Paint()
    ..color = Colors.blue
    ..strokeWidth = 4.0
    ..style = PaintingStyle.fill;

  static final Paint paintHandLine = Paint()
    ..color = Colors.redAccent
    ..strokeWidth = 2.0
    ..style = PaintingStyle.stroke
    ..strokeCap = StrokeCap.round;

  static final Paint paintHandKnuckle = Paint()
    ..color = Colors.white
    ..strokeWidth = 3.0
    ..style = PaintingStyle.fill;

  @override
  void paint(Canvas canvas, Size size) {
    if (holisticData == null) return;

    // Helper: Normalized (0..1) -> Pixels
    Offset toPixel(dynamic point) {
      final p = point as Map; // Duck typing
      double x = (p['x'] as num).toDouble();
      double y = (p['y'] as num).toDouble();
      return Offset(x * size.width, y * size.height);
    }

    // --- 1. Face Mesh (Contours Only) ---
    if (holisticData!.containsKey('faceLandmarks')) {
      final face = holisticData!['faceLandmarks'] as List;
      _drawFaceContours(canvas, face, toPixel);
    }

    // --- 2. Pose (Upper Body Only) ---
    if (holisticData!.containsKey('poseLandmarks')) {
      final pose = holisticData!['poseLandmarks'] as List;
      _drawPose(canvas, pose, toPixel);
    }

    // --- 3. Hands (Detailed) ---
    if (holisticData!.containsKey('leftHandLandmarks')) {
      _drawHand(canvas, holisticData!['leftHandLandmarks'] as List, toPixel);
    }
    if (holisticData!.containsKey('rightHandLandmarks')) {
      _drawHand(canvas, holisticData!['rightHandLandmarks'] as List, toPixel);
    }
  }

  void _drawFaceContours(Canvas canvas, List landmarks, Offset Function(dynamic) toPixel) {
    // Indices for specific features (Face Mesh 468)
    // Lips
    final lipsUpper = [61, 185, 40, 39, 37, 0, 267, 269, 270, 409, 291];
    final lipsLower = [61, 146, 91, 181, 84, 17, 314, 405, 321, 375, 291];
    
    // Left Eye
    final leftEye = [33, 160, 158, 133, 153, 144, 33]; 
    // Right Eye
    final rightEye = [362, 385, 387, 263, 373, 380, 362]; 
    
    // Left Eyebrow
    final leftEyebrow = [70, 63, 105, 66, 107, 55, 65];
    // Right Eyebrow
    final rightEyebrow = [336, 296, 334, 293, 300, 276, 283];

    // Face Oval
    final faceOval = [10, 338, 297, 332, 284, 251, 389, 356, 454, 323, 361, 288, 397, 365, 379, 378, 400, 377, 152, 148, 176, 149, 150, 136, 172, 58, 132, 93, 234, 127, 162, 21, 54, 103, 67, 109, 10];
    
    _drawLoop(canvas, landmarks, lipsUpper, toPixel, paintFace);
    _drawLoop(canvas, landmarks, lipsLower, toPixel, paintFace);
    _drawLoop(canvas, landmarks, leftEye, toPixel, paintFace);
    _drawLoop(canvas, landmarks, rightEye, toPixel, paintFace);
    _drawLoop(canvas, landmarks, leftEyebrow, toPixel, paintFace);
    _drawLoop(canvas, landmarks, rightEyebrow, toPixel, paintFace);
    _drawLoop(canvas, landmarks, faceOval, toPixel, paintFace);
  }

  void _drawLoop(Canvas canvas, List landmarks, List<int> indices, Offset Function(dynamic) toPixel, Paint paint) {
    if (indices.isEmpty) return;
    
    final path = Path();
    bool first = true;
    
    for (int idx in indices) {
      if (idx >= landmarks.length) continue;
      final point = landmarks[idx];
      final offset = toPixel(point);
      
      if (first) {
        path.moveTo(offset.dx, offset.dy);
        first = false;
      } else {
        path.lineTo(offset.dx, offset.dy);
      }
    }
    canvas.drawPath(path, paint);
  }

  void _drawPose(Canvas canvas, List landmarks, Offset Function(dynamic) toPixel) {
    // Topología MediaPipe Pose (BlazePose)
    final connections = [
      [11, 12], // Hombros
      [11, 13], [13, 15], // Brazo Izquierdo
      [12, 14], [14, 16], // Brazo Derecho
      [11, 23], [12, 24], // Torso Vertical
      [23, 24], // Cadera (Base)
    ];

    for (var pair in connections) {
      final idx1 = pair[0];
      final idx2 = pair[1];
      
      // Filtros: Indice > 24 son piernas -> Ignorar
      if (idx1 > 24 || idx2 > 24) continue;
      
      if (idx1 < landmarks.length && idx2 < landmarks.length) {
        final p1 = landmarks[idx1];
        final p2 = landmarks[idx2];
        
        final o1 = toPixel(p1);
        final o2 = toPixel(p2);
        
        canvas.drawLine(o1, o2, paintBodyLine);
        
        // Dibujar articulaciones (Joints)
        canvas.drawCircle(o1, 4.0, paintBodyJoint);
        canvas.drawCircle(o2, 4.0, paintBodyJoint);
      }
    }
  }

  void _drawHand(Canvas canvas, List landmarks, Offset Function(dynamic) toPixel) {
    // Topología de Mano (21 Puntos)
    final connections = [
        [0, 1], [1, 2], [2, 3], [3, 4],       // Pulgar
        [0, 5], [5, 6], [6, 7], [7, 8],       // Índice
        [5, 9], [9, 10], [10, 11], [11, 12],  // Medio
        [9, 13], [13, 14], [14, 15], [15, 16], // Anular
        [13, 17], [17, 18], [18, 19], [19, 20], // Meñique
        [0, 17] // Cierre Palma
    ];

    // Dibujar Conexiones
    for (var pair in connections) {
       final idx1 = pair[0];
       final idx2 = pair[1];
       if (idx1 < landmarks.length && idx2 < landmarks.length) {
         canvas.drawLine(toPixel(landmarks[idx1]), toPixel(landmarks[idx2]), paintHandLine);
       }
    }
    
    // Dibujar Nudillos (Puntos)
    for (var point in landmarks) {
      canvas.drawCircle(toPixel(point), 3.0, paintHandKnuckle);
    }
  }

  @override
  bool shouldRepaint(covariant CustomPainter oldDelegate) => true;
}
