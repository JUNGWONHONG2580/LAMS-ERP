# 램스 ERP 앱 APK 빌드 방법

## 방법 1: GitHub Actions (권장, 5분 소요)

1. **GitHub 계정** 만들기: https://github.com
2. **새 repository** 생성 (private 가능)
3. 이 폴더 내용 전체를 업로드
   ```bash
   git init
   git add .
   git commit -m "initial"
   git remote add origin https://github.com/[계정명]/[저장소명].git
   git push -u origin main
   ```
4. GitHub → Actions 탭 → **Build APK** 워크플로우 자동 실행
5. 완료 후 **Artifacts** 에서 `RamsERP-APK.zip` 다운로드
6. ZIP 안의 `app-debug.apk`를 폰에 설치

## 방법 2: Android Studio (로컬 빌드)

1. Android Studio 설치: https://developer.android.com/studio
2. 이 폴더를 Android Studio로 열기
3. `Build > Build APK(s)`
4. `app/build/outputs/apk/debug/app-debug.apk` 설치

## APK 폰에 설치하기

1. 폰 `설정 > 보안 > 알 수 없는 앱 설치` 허용
2. APK 파일을 폰으로 전송 (카카오톡, USB, 이메일 등)
3. 파일 앱에서 APK 탭 → 설치
