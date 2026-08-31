# 旅行记录 App · Android APK 构建指南

用 Apache Cordova 把 `www/index.html`（旅行记录原型）打包成 Android APK。

> 应用功能：拍照上传 + 自由输入地名 → 腾讯地图点亮整座城市的行政边界；
> 旅行记账（按类别统计 + 饼图）；心声日记（天气氛围配色）。
> 数据默认保存在 WebView 的 `localStorage`，卸载 App 会清空。

---

## 方式一：云端构建（推荐，无需本地 Android 环境）

1. 把整个仓库（含 `travel-app-apk/` 目录）推送到 GitHub。
2. 进入仓库 **Actions → “Build Android APK” → Run workflow**。
3. 运行结束后，在 **Artifacts** 里下载 `travel-app`，里面就是 `app-debug.apk`。
4. 把 APK 传到手机安装即可（首次安装需「允许未知来源」）。

> 工作流见 `.github/workflows/build-apk.yml`，云端会自动装好 Node / JDK17 / Android SDK 并构建。

---

## 方式二：本地构建（需已装 Android Studio）

前置依赖：Node.js 18+、JDK 17、Android Studio（自带 Android SDK + Gradle）。

```bash
cd travel-app-apk
npm install
npx cordova platform add android
npx cordova build android --debug
```

构建完成后 APK 位于：

```
travel-app-apk/platforms/android/app/build/outputs/apk/debug/app-debug.apk
```

连上手机后安装运行：

```bash
npx cordova run android --device
```

---

## 权限说明

`config.xml` 已声明以下权限，对应原型功能：

- `INTERNET` / `ACCESS_NETWORK_STATE`：腾讯地图、行政区边界数据
- `ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION`：地图定位
- `CAMERA`：拍照上传（通过 WebView 的原生 `<input capture>` 调用系统相机）

> 注：原型未使用已废弃的原生插件，改用 WebView 原生能力，构建更稳、更少依赖。

---

## 更新 App 内容

只需替换 `www/index.html`（即 `travel-app/index.html` 的最新版），其余文件不用动，重新构建即可。
