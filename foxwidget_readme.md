<div align="center">
  <img src="./FoxWidget512.jpg" alt="FoxWidget Logo" width="210" style="border-radius: 36px;" />

  # 🦊 FoxWidget — Optical Liquid Glass & Dynamic Island

  **Ứng dụng Tiện ích Màn hình chính (AppWidget) & Dynamic Island phong cách Kính Lỏng Quang Học dành cho Android**

  ![Platform](https://img.shields.io/badge/Platform-Android%208.0%2B%20(API%2026--36)-3DDC84?style=for-the-badge&logo=android&logoColor=white)
  ![Language](https://img.shields.io/badge/Kotlin-1.9.24-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)
  ![Build](https://img.shields.io/badge/GitHub%20Actions-Auto%20APK-2088FF?style=for-the-badge&logo=github-actions&logoColor=white)
  ![Studio](https://img.shields.io/badge/Studio-FoxStudio-FF69B4?style=for-the-badge)
</div>

---

## ✨ Tính năng nổi bật

- **💎 Bộ dựng Kính Lỏng Quang Học (`LiquidGlassRenderer`):**
  - Tái tạo hiệu ứng thủy tinh chiều sâu với **tán sắc cầu vồng viền kính (Chromatic Dispersion)**, **tụ quang khúc xạ (Caustic Glow)** và **phản xạ vòm giọt nước (Specular Dome)**.
  - Hỗ trợ 3 chế độ giao diện (**Sáng**, **Tối**, **Kính Lỏng**) cùng 5 bộ chất liệu nền: *Obsidian Titan*, *Purple Dusk*, *Ocean Blue*, *Emerald Mist*, và *Prismatic Frost*.
  - Tùy chỉnh chi tiết theo thời gian thực với **Live Preview 1:1**: độ đặc khối kính, cường độ tán sắc, độ bóng bề mặt, độ dày viền, bo góc đồng bộ hệ điều hành và tỷ lệ cỡ chữ.
- **🏝️ Dynamic Island Overlay (`IslandOverlayManager`):**
  - Hiển thị viên thuốc nổi tương tác thông minh ngay trên thanh trạng thái với hiệu ứng chuyển động mượt mà.
- **📅 Lịch Âm Dương & Can Chi Việt Nam (`VietLunarCalendar`):**
  - Tự động quy đổi Dương lịch sang Âm lịch (hỗ trợ cả tháng nhuận), tính năm/ngày **Can Chi** và hiển thị dải lịch tuần 7 ngày trực quan ngay trên Widget.
- **🌦️ Thời tiết & Giám sát Thiết bị (`DeviceTelemetryHelper`):**
  - Cập nhật nhiệt độ và trạng thái thời tiết theo tọa độ GPS/mạng qua **Open-Meteo API** cùng tên địa phương tiếng Việt.
  - Theo dõi phần trăm pin, trạng thái sạc, nhiệt độ pin ($^\circ\text{C}$) và tên thiết bị theo thời gian thực.
- **⚙️ Tối ưu Chạy ngầm Đa hãng OEM (`OemOptimizationHelper`):**
  - Tự động nhận diện giao diện hệ thống (**HyperOS / MIUI**, **One UI**, **ColorOS**, **OriginOS / Funtouch OS**, **MagicOS**) và mở thẳng cài đặt *Tự khởi động nền* & *Pin không hạn chế* để Widget luôn hoạt động ổn định.

---

## 📂 Cấu trúc dự án

```text
FoxWidget/
├── FoxWidget512_2.jpg                # Ảnh đại diện hiển thị trên trang chủ GitHub
├── README.md                         # Tài liệu giới thiệu dự án
├── .github/workflows/
│   └── build-apk.yml                 # Workflow tự động build & ký APK trên GitHub Actions
└── FoxWidget-Android-Project/
    └── FoxWidget_v3/
        ├── build.gradle.kts
        ├── settings.gradle.kts
        ├── gradle.properties
        ├── ic_launcher-playstore.png
        └── app/
            ├── build.gradle.kts      # Cấu hình SDK 36, ViewBinding & chữ ký số foxwidget.jks
            ├── foxwidget.jks         # Keystore cố định giúp cập nhật đè không xung đột gói
            ├── proguard-rules.pro
            └── src/main/
                ├── AndroidManifest.xml
                ├── java/com/foxwidget/app/
                │   ├── MainActivity.kt               # Giao diện điều khiển 6 tab & Live Preview
                │   ├── data/PrefsManager.kt          # Lưu trữ cấu hình người dùng
                │   ├── service/
                │   │   ├── BootReceiver.kt           # Tự khôi phục Widget sau khi khởi động lại
                │   │   └── FoxMonitorService.kt      # Dịch vụ duy trì đồng bộ thời gian thực
                │   ├── util/
                │   │   ├── DeviceTelemetryHelper.kt  # Cảm biến pin, vị trí & thời tiết Open-Meteo
                │   │   ├── IslandOverlayManager.kt   # Quản lý giao diện nổi Dynamic Island
                │   │   ├── LiquidGlassRenderer.kt    # Engine vẽ đồ họa Kính Lỏng Quang Học
                │   │   ├── OemOptimizationHelper.kt  # Nhận diện & tối ưu pin theo từng hãng máy
                │   │   └── VietLunarCalendar.kt      # Thuật toán tính Âm lịch & Can Chi Việt Nam
                │   └── widget/
                │       └── FoxWidgetProvider.kt      # Xử lý hiển thị & cập nhật AppWidget
                └── res/
                    ├── drawable-nodpi/fox_logo.png   # Logo hiển thị trong ứng dụng
                    ├── layout/                       # Giao diện Activity, Widget & Island Overlay
                    └── xml/fox_widget_info.xml       # Khai báo kích thước & chu kỳ làm mới Widget
```

---

## 🛠️ Công nghệ sử dụng

- **Ngôn ngữ & Nền tảng:** Kotlin `1.9.24`, Android Gradle Plugin `8.9.1`, `minSdk = 26` (Android 8.0), `targetSdk = 36` (Android 16).
- **Kiến trúc & Thư viện:** AndroidX Core KTX, AppCompat, Material Components 3 (`1.12.0`), ViewBinding, Kotlin Coroutines (`1.8.1`), WorkManager (`2.10.0`).
- **Đồ họa & API:** Android Canvas / Shader (`LinearGradient`, `RadialGradient`, `SweepGradient`), Open-Meteo Forecast API, Android Geocoder.

---

## 🚀 Hướng dẫn Build & Cài đặt

1. **Build tự động qua GitHub Actions:**
   - Mỗi khi push code lên nhánh `main` hoặc chạy thủ công tại tab **Actions**, workflow `build-apk.yml` sẽ tự động biên dịch và ký file APK bằng khóa cố định `foxwidget.jks`.
   - Tải gói Artifact về, giải nén lấy file `.apk` và cài đặt trực tiếp lên thiết bị Android.
2. **Cập nhật phiên bản mới:**
   - Khi nâng cấp tính năng, chỉ cần tăng giá trị `versionCode` trong `app/build.gradle.kts` (`1` → `2` → `3`...) để cài đè trực tiếp mà vẫn giữ nguyên toàn bộ thiết lập Widget trên màn hình chính.
3. **Ghim Widget ra màn hình chính:**
   - Mở ứng dụng **FoxWidget** → chọn tab **Widget** → bấm **Ghim Widget ra màn hình chính** (hoặc nhấn giữ khoảng trống trên màn hình chính → chọn **Tiện ích (Widgets)** → **FoxWidget**).

---

## 👨‍💻 Tác giả & Liên hệ

Phát triển bởi **FoxStudio (FoxST)**

- **Facebook:** [Nguyễn Hải (FoxST)](https://www.facebook.com/nguyen.hai.225063)
- **TikTok:** [@foxstudio257](https://www.tiktok.com/@foxstudio257)
- **Email:** haistrem6792@gmail.com
