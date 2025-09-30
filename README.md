# 🚀 LungExercise (Wear OS)

**LungExercise**는 Wear OS(안드로이드 워치)에서 동작하는 휴대폰 폐장도인운동법 앱 보조 애플리케이션입니다.

---

## 📱 앱 정보
- **Namespace**: `kr.daejeonuinversity.lungexercise`
- **Min SDK**: 30 (Wear OS 최소 지원 버전)
- **Target SDK**: 34
- **버전**: 1.0

---

## ⚙️ 주요 기술 스택
- **언어**: Kotlin
- **아키텍처**: Jetpack Compose
- **UI 프레임워크**: Compose for Wear OS
- **주요 라이브러리**:
    - `androidx.wear.compose:compose-material`
    - `androidx.wear.compose:compose-foundation`
    - `com.google.android.gms:play-services-wearable`
    - `androidx.core:core-splashscreen`

---

## 🔗 휴대폰 앱과 연동 조건
워치 앱과 휴대폰 앱(`pulmoguide`)이 정상적으로 연동되려면 아래 조건이 반드시 충족되어야 합니다:

1. **Application ID 동일**
    - 휴대폰 앱(`pulmoguide`)과 워치 앱의 `applicationId`는 동일해야 합니다.
    - 현재 값: `kr.daejeonuinversity.lungexercise`

2. **Signing Key 동일**
    - 두 앱은 동일한 **서명 키(Signing Key)**로 빌드되어야 합니다.
    - 서명 키가 다르면 페어링 및 데이터 연동이 불가능합니다.

✅ 위 두 조건이 만족되어야 Wear OS 앱과 Android 휴대폰 앱 간의 데이터 통신 및 연동이 정상적으로 동작합니다.

---

## 🛠️ Gradle 설정 예시
```kotlin
android {
    namespace = "kr.daejeonuinversity.lungexercise"
    compileSdkPreview = "UpsideDownCake"

    defaultConfig {
        applicationId = "kr.daejeonuinversity.lungexercise"
        minSdk = 30
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.1"
    }
}
```

## ⚙️ 갤럭시 워치 필수 설정

**LungExercise (Wear OS)** 앱과 휴대폰 앱(**pulmoguide**)을 정상적으로 연동하려면, 워치에서 다음 설정을 반드시 확인하세요:

---

### 1️⃣ 앱 권한
- 워치에서:
    - **설정 → 애플리케이션 → 앱 목록 → 해당 앱 (pulmoguide 또는 LungExerciseWatch)**
- 권한:
    - 센서: ✅ 허용
    - 신체 활동: ✅ 허용

---

### 2️⃣ 근처 기기 권한
- 워치에서:  
  **설정 → 애플리케이션 → 권한 관리자 → 근처 기기**
- 권한:
    - Samsung Health Monitor: ✅ 허용

---

### 3️⃣ 센서 권한
- 워치에서:  
  **설정 → 애플리케이션 → 권한 관리자 → 센서**
- 권한:
    - 해당 앱: ✅ 허용

---

### 4️⃣ 신체 활동 권한
- 워치에서:  
  **설정 → 애플리케이션 → 권한 관리자 → 신체 활동**
- 권한:
    - 해당 앱: ✅ 허용

---

⚠️ **위 모든 권한이 활성화되어야 워치 앱과 휴대폰 앱 간의 데이터 연동 및 센서 측정이 정상적으로 동작합니다.**
