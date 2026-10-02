# 单机斗地主

一款完全离线的安卓斗地主游戏，采用欢乐斗地主规则（叫地主 / 抢地主 + 加倍），支持三档 AI 难度。
不联网、不申请任何权限，release 包约 1.3MB。

## 功能

- **规则**：欢乐斗地主规则，详见 [docs/RULES.md](docs/RULES.md)。
  - 叫地主、抢地主、加倍。
  - 炸弹、王炸、春天、反春都翻倍。
  - 结算零和：输赢按双方携带的金币封顶，谁都不会被扣成负数。
- **三档 AI**：都不偷看手牌，只根据自己手牌和公开信息决策。
  - 简单（新手场）：出最小能压的牌，偶尔乱过。
  - 普通（普通场）：拆牌估值、记牌、配合队友（顶牌、喂牌）、控制炸弹时机。
  - 困难（高手场）：在普通档的候选出法上做蒙特卡洛采样推演，残局精确求解。
- **牌桌体验**：
  - 发牌、出牌飞入动画。
  - 点选或横向滑动多选手牌。
  - 提示按拆牌代价由小到大轮换。
  - 压不过时自动“要不起”。
  - 托管。
  - 记牌器。
  - 炸弹闪光震屏、王炸火箭、飞机、春天等特效。
- **声音**：
  - 音效全部由代码实时合成，不带任何素材文件。
  - 报牌用系统中文 TTS（例如“对K”“飞机”“王炸”）；手机没有中文语音时自动静音。
- **成长与存档**：
  - 金币、三个场次、救济金（金币少于 1,000 时可领 3,000，不限次数）、战绩统计。
  - 每一步都自动存档，被杀进程后可以继续上一局。

## 工程结构

```
engine/   纯 Kotlin/JVM 模块：规则、对局状态机、AI。不依赖 Android，可以直接在电脑上测试和批量模拟。
  model/  Card / CardSet（64 位掩码）、Counts（每个点数 4 位打包）、SplitMix64 随机数
  rules/  ComboClassifier（牌型判定，含多种解释）、MoveGenerator（出牌生成）
  game/   GameEngine（纯函数 reducer）、GameState（可序列化）、Observation（单个座位能看到的信息）、Settlement
  ai/     HandAnalyzer（拆牌 DP）、NormalPolicy、Easy/Normal/HardBot、LitePlayout（推演）、EndgameSolver、BidModel
app/      Android 应用（Jetpack Compose）
  game/   GameViewModel（回合调度、节奏、托管、存档）、TableUi（界面状态）
  ui/     牌桌（统一坐标的卡牌精灵、手势、特效）、大厅、设置、战绩、玩法说明
  audio/  SoundSynth（PCM 合成）、SoundManager（SoundPool）、VoiceAnnouncer（TTS）
  data/   AppDataStore（DataStore，单个 JSON 文档，结算与清除存档原子完成）
  src/debug/DebugHooks.kt  测试钩子，只编译进 debug 包
docs/RULES.md  规则说明
```

## 构建

需要 JDK 17 和 Android SDK（compileSdk 37）。本机的安装位置：

- JDK：`brew install openjdk@17`
- SDK：`~/Library/Android/sdk`，路径写在 `local.properties` 的 `sdk.dir` 里

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home

./gradlew :app:assembleDebug        # app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:assembleRelease      # R8 压缩；目前用 debug 签名，只适合本地安装
./gradlew :app:installDebug         # 安装到已连接的手机或模拟器
```

正式发布前需要生成自己的签名密钥（不要提交到仓库），并替换 `app/build.gradle.kts` 里 release 的 `signingConfig`。

## 测试

```bash
./gradlew :engine:test                      # 约 50 个单元测试，几秒钟跑完
./gradlew :engine:slowTest                  # AI 强度基准、叫牌模型校准（较慢）
./gradlew :app:lintDebug
./gradlew :app:connectedDebugAndroidTest    # 在模拟器或手机上跑 Compose 冒烟测试
```

`:engine:test` 覆盖以下内容：

- **规则基准表**：测试代码不经过判定器，直接按规则表生成全部 27,845 种合法牌组，然后：
  - 与 ComboClassifier 的判定逐条核对；
  - 检查 30 万个随机牌组不会被误判为合法；
  - 与 MoveGenerator 的出牌生成、压牌生成结果对拍。
- **黄金用例**：叫抢状态机、春天/反春、结算封顶。
- **自对弈**：数千局 AI 对局，每一步都检查不变量：
  - 54 张牌守恒；
  - 每个动作都合法；
  - 结算零和；
  - JSON 存档往返后状态不变。
- **残局求解器**：6000 个随机残局，与朴素 minimax 的结果对拍。
- **困难档 AI**：结果可复现，并且不偷看手牌（隐藏牌互换后决策不变）。

### AI 强度

数据来自 `StrengthBenchmarkTest`：同一副牌、固定地主、交换角色，给出配对胜率差及 95% 置信区间。

| 对比 | 当地主 | 当农民 |
|---|---|---|
| 困难 − 普通 | +9.1% ± 2.4% | +12.2% ± 2.4% |
| 普通 − 简单 | +44.0% ± 1.4% | +27.4% ± 1.3% |

困难档在电脑 JVM 上每步平均 3.8ms（预算上限 450ms）。在模拟器的 debug 包里，几百局中只有 1 次决策超过 150ms。

### 模拟器上的自动化测试

debug 包可以用 intent 参数直接开局，便于截图和压力测试：

```bash
# 指定场次和种子开局，托管自动打
adb shell am start -n com.kaynzhang.doudizhu/.MainActivity --es room NOVICE --el seed 42 --ez autoplay true
# 预设牌局：bomb / rocket / plane / spring
adb shell am start -n com.kaynzhang.doudizhu/.MainActivity --es room NORMAL --es scenario rocket
# 压力测试：自动连打，AI 零延迟
adb shell am start -n com.kaynzhang.doudizhu/.MainActivity --es room NOVICE --ez autoplay true --ez loop true --ez turbo true
```

debug 包会把 Compose 的 testTag 暴露为 resource-id，可以用 `uiautomator dump` 按 id 点击控件（例如 `btn_call`、`btn_hint`、`btn_play`、`btn_trustee`）。

## 以后可以扩展

- 其他玩法：叫分模式、不洗牌模式、癞子模式、明牌。
- 背景音乐。
- 更强的 AI：例如接入 DouZero 一类的深度学习模型，或者在采样时根据对手的出牌推断手牌分布。
