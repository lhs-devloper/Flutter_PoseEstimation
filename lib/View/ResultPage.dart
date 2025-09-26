import 'package:flutter/material.dart';

class ResultPage extends StatelessWidget{
  final String result;
  const ResultPage({Key? key, required this.result}) : super(key: key);

  @override
  Widget build(BuildContext context) {
    return Center(
      child: const Text('네이티브 자세촬영 완료')
    );
  }
}
