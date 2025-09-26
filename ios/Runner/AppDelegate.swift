import UIKit
import Flutter

@UIApplicationMain
@objc class AppDelegate: FlutterAppDelegate {
  override func application(
    _ application: UIApplication,
    didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]?
  ) -> Bool {

    guard let controller = window?.rootViewController as? FlutterViewController else {
      fatalError("rootViewController is not type FlutterViewController")
    }

    let poseChannel = FlutterMethodChannel(name: "com.example.pose_analysis_app/pose",
                                           binaryMessenger: controller.binaryMessenger)

    poseChannel.setMethodCallHandler({
      (call: FlutterMethodCall, result: @escaping FlutterResult) -> Void in
      guard call.method == "openPoseEstimation" else {
        result(FlutterMethodNotImplemented)
        return
      }
      
      // "Main" 스토리보드에서 "ViewController" ID를 가진 뷰 컨트롤러를 로드합니다.
      // 만약 스토리보드 이름이나 ID가 다르다면 이 부분을 수정해야 합니다.
      let storyboard = UIStoryboard(name: "PoseEstimationMain", bundle: nil)
      let poseViewController = storyboard.instantiateViewController(withIdentifier: "ViewController")
      
      controller.present(poseViewController, animated: true, completion: nil)
      result(nil)
    })

    GeneratedPluginRegistrant.register(with: self)
    return super.application(application, didFinishLaunchingWithOptions: launchOptions)
  }
}
