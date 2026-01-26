import 'package:flutter/material.dart';
import 'ui/main_screen.dart';

void main() {
  runApp(const SignLanguageApp());
}

class SignLanguageApp extends StatelessWidget {
  const SignLanguageApp({Key? key}) : super(key: key);

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      debugShowCheckedModeBanner: false,
      title: 'Zigna',
      theme: ThemeData(
        brightness: Brightness.dark,
        primaryColor: Colors.blueAccent,
        scaffoldBackgroundColor: Colors.black,
      ),
      home: const MainScreen(),
    );
  }
}
