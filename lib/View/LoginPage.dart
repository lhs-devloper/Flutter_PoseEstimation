import 'package:flutter/material.dart';

class LoginPage extends StatelessWidget {
  const LoginPage({super.key});

  @override
  Widget build(BuildContext context) {
    final TextEditingController usernameController = TextEditingController();
    final TextEditingController passwordController = TextEditingController();

    return Padding(
      padding: const EdgeInsets.all(16.0),
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: <Widget>[
          TextField(
            controller: usernameController,
            decoration: const InputDecoration(
              labelText: '사용자 이름',
              border: OutlineInputBorder(),
            ),
          ),
          const SizedBox(height: 16.0),
          TextField(
            controller: passwordController,
            obscureText: true,
            decoration: const InputDecoration(
              labelText: '비밀번호',
              border: OutlineInputBorder(),
            ),
          ),
          const SizedBox(height: 24.0),
          ElevatedButton(
            onPressed: () {
              // 실제 인증 로직은 없습니다.
              final username = usernameController.text;
              print('로그인 시도: 사용자 이름 - $username');
              ScaffoldMessenger.of(context).showSnackBar(
                SnackBar(content: Text('$username 님, 환영합니다!')),
              );
            },
            child: const Text('로그인'),
          ),
        ],
      ),
    );
  }
}