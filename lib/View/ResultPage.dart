// View/ResultPage.dart

import 'package:flutter/material.dart';

class ResultPage extends StatelessWidget {
  // 생성자를 통해 poseData를 받습니다.
  final Map<String, dynamic> poseData;

  const ResultPage({Key? key, required this.poseData}) : super(key: key);

  @override
  Widget build(BuildContext context) {
    // poseData['keyPoints']는 List<dynamic> 형태입니다.
    final List<dynamic> keyPoints = poseData['keyPoints'] ?? [];

    return Scaffold(
      appBar: AppBar(
        title: Text("분석 결과"),
        // 뒤로가기 버튼 자동 생성
      ),
      body: ListView.builder(
        itemCount: keyPoints.length,
        itemBuilder: (context, index) {
          final keyPoint = keyPoints[index];
          print("keypoint");
          print(keyPoint);
          print(keyPoint.runtimeType);

          final bodyPart = keyPoint['bodyPart'] ?? 'Unknown';
          final double score = keyPoint['score'] ?? 0.0;

          return ListTile(
            title: Text(bodyPart),
            subtitle: Text('신뢰도 점수: ${(score * 100).toStringAsFixed(1)}%'),
          );
        },
      ),
    );
  }
}