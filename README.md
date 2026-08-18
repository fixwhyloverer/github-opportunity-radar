# GitHub 信息差雷达

一个面向 Android 的 GitHub Opportunity Radar 原生应用，用来发现 GitHub 新项目、用户吐槽和未满足需求，并给出粗略商业价值评分。

## 功能

- GitHub 新项目雷达：按最近创建时间、Star、Fork、描述关键词筛选公开仓库。
- 用户吐槽/未满足需求雷达：搜索 GitHub Issues 中的 pain point、feature request、alternative 等信号。
- 商业价值评分：按增长、活跃度、需求强度、商业关键词、竞争/替代信号计算 0-100 分。
- 来源直达：每条结果可直接打开 GitHub 仓库或 Issue。
- 收藏/缓存：本地保存收藏项和最近一次拉取的数据。
- 基础中文 UI：适配手机竖屏，兼容 Samsung S24 Ultra / Android 16。

## 技术栈

- 原生 Android Java
- Android Gradle Plugin 9.2.0
- `compileSdk` / `targetSdk` 36
- 最低支持 Android 8.0 API 26
- 无后端、无密钥，直接使用 GitHub 公共 API

## 构建

```bash
./gradlew :app:assembleDebug
```

生成的 `app/build/outputs/apk/debug/app-debug.apk` 使用标准 debug keystore 签名，可侧载安装。

## GitHub Actions

仓库包含 `.github/workflows/android-apk.yml`。推送到 `main` 或 `master` 后会自动构建并上传 `github-opportunity-radar-debug-apk` artifact。
