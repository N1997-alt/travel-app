# travel-app · 旅行记录

一款旅行记录 App：**点亮地图**（拍照打卡，自动识别城市并金色填充整片行政边界）、**旅行记账**（分类明细 + 统计饼图）、**心声日记**（带天气氛围）。数据全部本地存储，无登录。

## 技术结构

这是一个基于 **Cordova** 的安卓工程，前端是单文件 `www/index.html`（含内嵌的国内行政区索引，离线也能识别城市）。推送到 GitHub 后，GitHub Actions 会自动构建出 `app-debug.apk`。

```
travel-app/
├── config.xml                      # Cordova 配置 + Android 权限
├── package.json                    # cordova 依赖
├── BUILD.md                        # 详细构建说明
├── www/index.html                  # App 主体（最新原型）
└── .github/workflows/build-apk.yml # 云端自动出包
```

## 如何拿到 APK

1. 把本仓库推到 GitHub（首次需允许 Actions 读取）。
2. 进入仓库 **Actions → Build Android APK → Run workflow**。
3. 跑完后在 **Artifacts** 下载 `travel-app`，里面就是 `app-debug.apk`，传到手机安装即可（首次需允许「未知来源」）。

更详细的本地构建 / Android Studio 方式见 [BUILD.md](./BUILD.md)。

## 地图说明

地图底图走腾讯地图 GL JS（合规代理模式，密钥前端不暴露），打开需联网；地名识别与行政边界绘制为国内直连，稳定可用。
