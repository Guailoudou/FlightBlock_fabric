# 多版本移植进度

目标为保留 FlightBlock 的现有功能，在各加载器实际发行的组合上分别构建。

常用版本范围：1.16.5、1.17.1、1.18.2、1.19.2、1.19.4、1.20.1、1.20.2、1.20.4、1.20.6、1.21.1、1.21.4、1.21.8、1.21.11，以及 26.x 正式版。

## 当前阶段

- 26.3：已拆出加载器无关的生命周期、交互和指令逻辑；Fabric 保留内嵌 PAL。
- 26.1、26.1.1、26.1.2、26.2、26.3：Fabric、Quilt、Forge、NeoForge 全部通过 GitHub Actions 编译和产物检查，共 20 个组合，记录见 [构建运行](https://github.com/Guailoudou/FlightBlock_fabric/actions/runs/37225547987)。
- Quilt 使用 Fabric 兼容接口及内嵌 PAL，构建依赖 Quilt Loader 0.31.0-beta.4（提供 Fabric Loader 0.19.5 兼容接口）。
- 1.21.11：四种加载器已加入构建矩阵，正在修复和验证，尚不能作为可用版本发布。
- 其余 1.x Minecraft：仍待移植及验证，当前不能使用 26.3 JAR 替代。
- Forge / NeoForge 使用独立权限适配，不能加载 Fabric 版 PAL。支持本 Mod 自身授权的清理及存档隔离；与其他直接修改能力的 Mod 的兼容性需要实际验收。

## 构建与交付

`ci/targets.json` 定义当前加入构建的组合，GitHub Actions 对每个组合独立执行 Gradle。任何组合失败都会阻止最终合集发布，避免提供缺少版本的成功合集。

所有组合成功后，汇总任务生成一个 ZIP，结构如下：

```text
FlightBlock-all-versions-<运行编号>.zip
├── 26.3/
│   ├── flightblock-26.3-fabric-0.2.0.jar
│   ├── flightblock-26.3-forge-0.2.0.jar
│   └── flightblock-26.3-neoforge-0.2.0.jar
└── SHA256SUMS
```

此目录仅说明当前构建阶段；完整支持矩阵仍在推进。只构建和检查代码，不启动游戏或服务端。
