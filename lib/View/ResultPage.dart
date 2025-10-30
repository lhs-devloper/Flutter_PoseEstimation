import 'package:flutter/material.dart';

class ResultPage extends StatefulWidget {
  final Map<String, dynamic> poseData;

  const ResultPage({Key? key, required this.poseData}) : super(key: key);

  @override
  State<ResultPage> createState() => _ResultPageState();
}

class _ResultPageState extends State<ResultPage> {
  String selectedCategory = 'FRONT'; // 기본 선택 카테고리

  @override
  Widget build(BuildContext context) {
    // poseData에서 데이터 가져오기
    final frontKeyPoints = widget.poseData['FRONT']?['keyPoints'] ?? [];
    final leftKeyPoints = widget.poseData['LEFT_SIDE']?['keyPoints'] ?? [];
    final rightKeyPoints = widget.poseData['RIGHT_SIDE']?['keyPoints'] ?? [];

    // 선택된 카테고리에 따라 표시할 keyPoints 결정
    List<dynamic> keyPoints = [];
    switch (selectedCategory) {
      case 'FRONT':
        keyPoints = frontKeyPoints;
        break;
      case 'LEFT_SIDE':
        keyPoints = leftKeyPoints;
        break;
      case 'RIGHT_SIDE':
        keyPoints = rightKeyPoints;
        break;
    }

    return Scaffold(
      appBar: AppBar(
        title: const Text("분석 결과"),
        centerTitle: true,
      ),
      body: Column(
        children: [
          // 🔹 상단 카테고리 선택 버튼 (ChoiceChip)
          Padding(
            padding: const EdgeInsets.symmetric(vertical: 8, horizontal: 12),
            child: Wrap(
              spacing: 8,
              children: [
                ChoiceChip(
                  label: const Text('FRONT'),
                  selected: selectedCategory == 'FRONT',
                  onSelected: (_) => setState(() => selectedCategory = 'FRONT'),
                ),
                ChoiceChip(
                  label: const Text('LEFT_SIDE'),
                  selected: selectedCategory == 'LEFT_SIDE',
                  onSelected: (_) => setState(() => selectedCategory = 'LEFT_SIDE'),
                ),
                ChoiceChip(
                  label: const Text('RIGHT_SIDE'),
                  selected: selectedCategory == 'RIGHT_SIDE',
                  onSelected: (_) => setState(() => selectedCategory = 'RIGHT_SIDE'),
                ),
              ],
            ),
          ),
          const Divider(),
          // 🔹 선택된 keyPoints 표시
          Expanded(
            child: ListView.builder(
              itemCount: keyPoints.length,
              itemBuilder: (context, index) {
                final keyPoint = keyPoints[index];
                final bodyPart = keyPoint['bodyPart'] ?? 'Unknown';
                final score = (keyPoint['score'] ?? 0.0) as double;

                return ListTile(
                  title: Text(bodyPart),
                  subtitle: Text('신뢰도 점수: ${(score * 100).toStringAsFixed(1)}%'),
                );
              },
            ),
          ),
        ],
      ),
    );
  }
}
