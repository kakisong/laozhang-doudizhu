# 超级难度：离线 DouZero

超级难度在出牌阶段使用 DouZero-WP 的三个预训练模型：地主、地主上家和地主下家。
该功能从 2.0.0 开始提供，场次入口为「超级场」；底分 2,000，入场需 20,000 金币。
模型随安装包内置，通过 ONNX Runtime 在设备 CPU 上运行；游戏不下载模型、不访问网络。
应用固定使用 ONNX Runtime 1.23.2（与转换校验版本相同），关闭遥测；该 Android
依赖本身不声明网络权限，也不安装遥测初始化组件。原生运行库在 APK 内压缩存储。
叫地主、抢地主和加倍继续使用现有困难档策略，DouZero-WP 只负责出牌。

## 模型来源

- 原始项目：[kwai/DouZero](https://github.com/kwai/DouZero)。
- 原始代码固定版本：`718a5c920bf3361e34178a38f3b80458e176b351`。
- 原始论文：[DouZero: Mastering DouDizhu with Self-Play Deep Reinforcement Learning, ICML 2021](https://proceedings.mlr.press/v139/zha21a.html)。
- 权重：官方 README 中命名的 `baselines/douzero_WP/{landlord,landlord_up,landlord_down}.ckpt`。
- 下载镜像：[palemoky/douzero-baselines](https://huggingface.co/palemoky/douzero-baselines)，固定版本 `57b3914046c2a0877016b8b8830fd07cf5b0ba08`。

原始仓库没有提交上述 checkpoint，README 中的 Google Drive 链接在接入时返回 404，
因此构建工具从声明镜像官方 baseline 的 Hugging Face 仓库获取 checkpoint。
镜像模型卡标注 Apache-2.0。源代码和完整 Apache 2.0 许可证来自原始项目。
每个下载文件都用固定 SHA-256 校验，实际下载 URL、checkpoint 与导出文件的哈希、
导出工具版本和数值对拍误差保存于
`engine/src/main/resources/douzero/manifest.json`。
安装包同时包含 `LICENSE` 和 `NOTICE`。
ONNX Runtime 的 MIT 许可证与完整第三方声明保存于
`engine/src/main/resources/onnxruntime/`，也会随安装包提供。原文来自固定版本
[v1.23.2 LICENSE](https://github.com/microsoft/onnxruntime/blob/v1.23.2/LICENSE) 和
[ThirdPartyNotices.txt](https://github.com/microsoft/onnxruntime/blob/v1.23.2/ThirdPartyNotices.txt)。

| 模型 | 原始 checkpoint SHA-256 |
|---|---|
| landlord | `132e26479fcb69f457ddbf1ad32a7bdc3aa73cde80a28cc91f29a62c6075955a` |
| landlord_up | `b849ea518e66f088d635d3a29956da880f7b5ca89fabc5f70fc356c106ca6a15` |
| landlord_down | `964720faf583f4905662c94aaf73b1a97352b76ae3686fcc949beabbacdd95d1` |

## 离线推理与公开信息

出牌候选仍由本游戏的规则引擎产生，包含合法带牌变体；跟牌时同时考虑不出。
网络为每个候选输出一个值，选取值最大的合法动作。
模型输入完全从 `Observation` 构造，包括自己的手牌、未知牌的总集合、各座位公开
出过的牌、剩余张数、最近动作、炸弹数和最近 15 次出牌/不出记录。
农民不知道另外两人的手牌分配；模型也不会得到这种信息。
恢复存档时可以仅从存下来的公开记录重建输入。

点数编码沿用原始实现：3–A、2 各占四个累计二进制位，随后是小王、大王。
历史记录按时间排列，保留不出，左侧补零，组成 `[1, 5, 162]`。
地主的动作输入为 `[候选数, 373]`，农民为 `[候选数, 484]`。
导出图在每次批量推理中只计算一次 LSTM 历史表示，然后展开到该批候选；原模型的网络层和权重没有改变。
运行时按最多 128 个候选分批评分，限制临时内存并在批次之间响应暂停/取消。
模型资源缺失、本地运行库无法加载或评分异常时使用困难档继续牌局；取消决策不会触发回退。
真实模型测试禁用回退，因此缺失模型不能被误判为测试通过。

本游戏保留自己的带牌约束与牌型声明规则。例如不能同时带大小王，不能用四张同点数
作翅膀。原始 DouZero 的动作生成对某些带牌情形更宽松，接入时以本游戏合法动作
为准。预训练模型的实际强度应以本游戏的对局基准衡量，原项目成绩不直接等于本游戏成绩。

## 重新导出与验证

仓库已包含导出的 ONNX 文件，日常 Gradle 构建不需要 Python，也不需要联网获取模型。
仅在重新导出时需要 Python 3.10 或更新版本和以下构建工具：

```bash
python3 -m venv /tmp/douzero-export
/tmp/douzero-export/bin/pip install torch==2.8.0 onnx==1.19.1 onnxruntime==1.23.2
/tmp/douzero-export/bin/python scripts/export_douzero.py
```

导出工具固定 ONNX opset 17。它将每个 ONNX 模型与原始 PyTorch 模型对拍，验证
不同候选批量大小，并生成 `engine/src/test/resources/douzero/parity.json`。
该 fixture 同时保存原始 Python 编码器产生的特征、公开牌局数据和原始模型得分，
供 JVM 测试校验输入转换与推理结果。测试覆盖三个角色、炸弹、一人不出、两人不出后
重新首出，以及超过 15 步的历史截断。

定向运行 JVM 模型和策略测试：

```bash
./gradlew :engine:test --tests '*DouZero*' --tests '*SuperBotTest*'
```

在指定模拟器上验证实际 APK 中的模型、JNI 和四场布局：

```bash
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest
adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5554 install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s emulator-5554 shell am instrument -w \
  -e class com.kaynzhang.doudizhu.OfflineAiSmokeTest,com.kaynzhang.doudizhu.SuperRoomSmokeTest \
  com.laozhang.doudizhu.test/androidx.test.runner.AndroidJUnitRunner
```

可用 `adb devices` 查看设备序列号，再替换命令中的 `emulator-5554`。

2026-10-03 验证结果：JVM 特征/评分对拍、完整对局、隐藏手牌互换、序列化恢复均通过。
Android 模拟器上三个模型完成 3 局、94 次决策，未触发回退；平均 3.1ms，
最长 122ms（含首次加载）。大厅另验证了短屏、字号 1.3 和四场横向滚动。
地主与农民方各 200 副配对牌、对手为 64 次采样的困难档时，超级档地主胜率差
为 +13.0 ± 6.7 个百分点，农民方为 +23.0 ± 7.2 个百分点（95% 配对置信区间）。
农民方对照中，两位农民同时使用超级档或同时使用困难档。
基准脚本为 `SuperStrengthBenchmarkTest`，复现命令见 README。
