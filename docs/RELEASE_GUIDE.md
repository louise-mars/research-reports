# 发布到 Google Play — 操作指南

## 一、生成签名密钥

```bash
keytool -genkey -v -keystore weather-calendar-release.jks \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -alias weather-calendar
```

将生成的 `weather-calendar-release.jks` 保存在安全位置，**不要提交到 Git**。

## 二、配置签名

在项目根目录创建 `keystore.properties`（已加入 .gitignore）：

```properties
storeFile=../weather-calendar-release.jks
storePassword=YOUR_STORE_PASSWORD
keyAlias=weather-calendar
keyPassword=YOUR_KEY_PASSWORD
```

在 `app/build.gradle.kts` 的 `android {}` 块中添加：

```kotlin
signingConfigs {
    create("release") {
        val props = java.util.Properties().apply {
            load(rootProject.file("keystore.properties").inputStream())
        }
        storeFile = file(props["storeFile"] as String)
        storePassword = props["storePassword"] as String
        keyAlias = props["keyAlias"] as String
        keyPassword = props["keyPassword"] as String
    }
}

buildTypes {
    release {
        signingConfig = signingConfigs.getByName("release")
        // ... 其他配置
    }
}
```

## 三、构建 AAB（Android App Bundle）

```bash
./gradlew bundleRelease
```

输出文件位于：`app/build/outputs/bundle/release/app-release.aab`

## 四、Google Play Console 上架步骤

1. **创建开发者账号**：https://play.google.com/console （需要 $25 注册费）

2. **创建应用**：
   - 应用名称：天气日历 / Weather Calendar
   - 默认语言：中文（简体）
   - 应用类型：应用（非游戏）
   - 免费

3. **填写商品详情**：
   - 简短描述（80字）：天气与日历融合，一眼看清天气和日程，轻松规划每一天。
   - 完整描述（4000字）：详细介绍功能特点
   - 截图：至少 2 张手机截图（推荐 4-8 张）
   - 高分辨率图标：512x512 PNG
   - 置顶大图：1024x500 PNG

4. **隐私政策**：
   - 上传 `docs/PRIVACY_POLICY.md` 的内容到你的网站
   - 在 Console 中填入隐私政策 URL

5. **内容分级**：
   - 填写 IARC 问卷
   - 本应用不含暴力/色情/赌博内容，通常获得 "所有人" 评级

6. **目标受众**：
   - 选择 "13 岁及以上"（因为不专门面向儿童）

7. **权限声明**：
   - 位置权限：用于获取本地天气
   - 日历权限：用于显示用户日程
   - 在 "数据安全" 部分如实填写

8. **上传 AAB**：
   - 进入 "发布" → "正式版"
   - 上传 `app-release.aab`
   - 填写版本说明

9. **提交审核**：
   - 首次审核通常需要 1-3 天
   - 确保所有必填项都已完成（Console 会提示）

## 五、数据安全声明（Google Play 要求）

| 问题 | 回答 |
|------|------|
| 是否收集用户数据？ | 是 — 位置数据（大致位置） |
| 是否共享用户数据？ | 否 |
| 数据是否加密传输？ | 是（HTTPS） |
| 用户能否请求删除数据？ | 是（清除应用数据即可） |
| 是否面向儿童？ | 否 |

## 六、注意事项

- `keystore.properties` 和 `.jks` 文件**绝对不要**提交到版本控制
- 每次发布递增 `versionCode`
- 保留签名密钥备份，丢失后无法更新应用
- Open-Meteo API 免费无限制，无需担心 API 费用
