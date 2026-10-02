# 老张斗地主

一款完全离线的安卓斗地主游戏，采用欢乐斗地主规则（叫地主 / 抢地主 + 加倍），支持三档 AI 难度。
不联网、不申请任何权限，语音与音效全部离线播放。

## 功能

- **规则**：欢乐斗地主规则，详见 [docs/RULES.md](docs/RULES.md)。
  - 叫地主、抢地主、加倍。
  - 炸弹、王炸、春天、反春都翻倍。
  - 结算零和：输赢按双方携带的金币封顶，谁都不会被扣成负数。
- **三档 AI**：都不偷看手牌，只根据自己手牌和公开信息决策。
  - 简单（新手场）：出最小能压的牌，偶尔乱过。
  - 普通（普通场）：拆牌估值、记牌、配合队友（顶牌、喂牌）、控制炸弹时机。
  - 困难（高手场）：在普通档的候选出法上做蒙特卡洛采样推演，残局精确求解。
- **为长辈设计**（主要玩家是老人，这就是默认界面）：
  - 大字大牌：牌面角标加大，大小王采用标准英文 JOKER 与小丑插画，红色为大王、黑色为小王；桌上别人出的牌约是原来的 1.4 倍；名字 16sp、剩余张数 20sp、按钮 22sp。
  - 按钮位置固定：左边放弃（不出、不叫……），中间“提示”，右边确定（出牌、叫地主……），换阶段也不挪位置；按钮 124×56dp，主操作使用香槟金底深色字，辅助操作使用深绿底浅色字。
  - “出牌”随时能点，出不了会说原因；能出时按钮发光。点空白处不会清空已选的牌，手抖的点按不会被当成滑动。
  - 跟牌时要压的那手牌带金色底板；轮到你时“叮”一声；提示消息停留 3 秒。
  - 托管时手牌上有横幅和大号“取消托管”，开启后电脑等 2 秒再出，误点来得及撤销。
  - 新安装默认慢速；系统字体最多跟随放大到 1.3 倍；适配 320dp 高的“显示大小：大”和平板。
- **牌桌体验**：
  - 统一的深松绿牌室风格：织物牌桌、香槟金细线、原生矢量图标和人物头像，大厅与设置、战绩、说明、结算页保持一致。
  - 24 款内置人物形象，包含不同脸型、发型、服装和配饰；在设置的「我的形象」中选择，牌桌与结算同步使用，自动保存。对手也会从形象库中挑选。
  - 扑克牌采用象牙纸面、大号数字与单个正向花色，数字牌留白充足，无需数点数；宫廷牌与大小王使用矢量插画，牌背为酒红纹样。
  - 发牌、出牌飞入动画。
  - 点选或横向滑动多选手牌。
  - 提示按拆牌代价由小到大轮换。
  - 压不过时自动“要不起”。
  - 随时暂停：停止电脑行动和自动跳过，继续后接着当前牌局；切到后台同样停止行动。
  - 电脑代打收在「更多」菜单中，需要时再开启；下方可随时取消托管。
  - 记牌器。
  - 炸弹闪光震屏、王炸火箭、飞机、春天等特效。
- **声音**：
  - 三种专门生成的自然中文人声：温暖男声、沉稳男声、清亮女声，按三个座位独立播放。报牌、叫抢、加倍、报单报双、春天、结算等语音内置在安装包中，不需要联网或安装系统中文语音。
  - 22 类由代码合成的音效，包含选牌、撤选、提示、无效出牌、发牌、顺子、连对、飞机、炸弹、王炸、轮到你与胜负结算；常用操作轻柔，重要事件更有辨识度。
  - 语音与音效分别开关、调整音量，在设置中可试听三种人声；说话时自动降低音效音量。
  - 同一步中的报牌、剩牌提醒与结果按顺序播放，电脑会等待语音结束；暂停、静音、切到后台和离开牌桌立即清掉旧声音，返回后不补播过时台词。
  - 素材来源、生成与验证见 [docs/audio-assets.md](docs/audio-assets.md)，可用 `scripts/generate_voices.py` 重新生成。
- **成长与存档**：
  - 金币、三个场次、救济金（金币少于 1,000 时可领 3,000，不限次数）、战绩统计。
  - 每日对局统计：按结算当天的本地日期保存局数、胜场、胜率和净金币；战绩页显示今日概览与近七日记录，保留累计战绩。
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
  audio/  SoundSynth（PCM 合成）、SoundManager（SoundPool）、VoiceAnnouncer（离线人声队列）、AppAudio（音频焦点与混音）、GameAudioPlanner（事件顺序）
  src/main/assets/voices/  三席中文人声与台词/时长目录
  data/   AppDataStore（DataStore，单个 JSON 文档，结算与清除存档原子完成）
  src/debug/DebugHooks.kt  测试钩子，只编译进 debug 包
docs/RULES.md  规则说明
```

## 构建

应用 ID（Android 安装包名）：`com.laozhang.doudizhu`。

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
./gradlew :app:testDebugUnitTest            # 牌桌布局在各种屏幕尺寸下互不重叠
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
adb shell am start -n com.laozhang.doudizhu/com.kaynzhang.doudizhu.MainActivity --es room NOVICE --el seed 42 --ez autoplay true
# 预设牌局：bomb / rocket / plane / spring
adb shell am start -n com.laozhang.doudizhu/com.kaynzhang.doudizhu.MainActivity --es room NORMAL --es scenario rocket
# 压力测试：自动连打，AI 零延迟
adb shell am start -n com.laozhang.doudizhu/com.kaynzhang.doudizhu.MainActivity --es room NOVICE --ez autoplay true --ez loop true --ez turbo true
```

debug 包会把 Compose 的 testTag 暴露为 resource-id，可以用 `uiautomator dump` 按 id 点击控件（例如 `btn_call`、`btn_hint`、`btn_play`、`btn_trustee`）。

## 以后可以扩展

- 其他玩法：叫分模式、不洗牌模式、癞子模式、明牌。
- 背景音乐。
- 更强的 AI：例如接入 DouZero 一类的深度学习模型，或者在采样时根据对手的出牌推断手牌分布。
