import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:pose_analysis_app/View/ResultPage.dart';

class PoseEstimationPage extends StatelessWidget {
  const PoseEstimationPage({super.key});

  static const platform = MethodChannel('com.example.pose_analysis_app/pose');

  Future<void> _openNativePoseEstimation() async {
    try {
      await platform.invokeMethod('openPoseEstimation');
      // Navigator.push(
      //   context,
      //   MaterialPageRoute(
      //     builder: (context) => ResultPage(result: result),
      //   ),
      // );
    } on PlatformException catch (e) {
      print("Failed to open pose estimation: '${e.message}'.");
    }
  }

  @override
  Widget build(BuildContext context) {
    return Center(
      child: ElevatedButton(
        onPressed: _openNativePoseEstimation,
        child: const Text('네이티브 자세촬영 실행'),
      ),
    );
  }
}

