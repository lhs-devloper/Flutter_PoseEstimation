import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:pose_analysis_app/View/ResultPage.dart';

class PoseEstimationPage extends StatelessWidget {
  const PoseEstimationPage({super.key});

  static const platform = MethodChannel('com.example.pose_analysis_app/pose');

  Future<void> _openNativePoseEstimation(BuildContext context) async {
    try {
      final String? resultJson = await platform.invokeMethod('openPoseEstimation');
      if (resultJson != null) {
        // 데이터를 현재 페이지의 상태로 저장하는 대신,
        // 바로 파싱하여 ResultPage로 전달하며 화면을 이동합니다.
        final Map<String, dynamic> poseData = jsonDecode(resultJson);
        print("poseData");
        print(poseData);

        // Navigator.push를 사용하여 ResultPage로 이동합니다.
        // MaterialPageRoute의 builder에서 ResultPage 위젯을 생성하고,
        // 생성자의 파라미터로 파싱된 데이터를 넘겨줍니다.
        Navigator.push(
          context,
          MaterialPageRoute(
            builder: (context) => ResultPage(poseData: poseData),
          ),
        );

      } else {
        print("Pose estimation was cancelled or failed.");
        // (선택) 사용자에게 취소되었음을 알리는 Snackbar 등을 표시할 수 있습니다.
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('자세 측정이 취소되었습니다.')),
        );
      }
    } on PlatformException catch (e) {
      print("Failed to open pose estimation: '${e.message}'.");
    }
  }

  @override
  Widget build(BuildContext context) {
    return Center(
      child: ElevatedButton(
        onPressed: () => _openNativePoseEstimation(context),
        child: const Text('네이티브 자세촬영 실행'),
      ),
    );
  }
}


