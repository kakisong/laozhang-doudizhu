# 游戏语音素材

语音通过 Google Gemini 专用语音生成模型制作，随应用离线打包。三个座位拥有独立声线；每个座位包含 76 句，共 228 条。报牌、叫抢地主、加倍、不要、余牌提醒、结算、托管和操作提示均有对应素材。

| 座位 | Gemini 声线 | 设定 | 最终生成模型 |
| --- | --- | --- | --- |
| 0 | Achird | 温暖亲切的成年男声 | `gemini-3.8-flash-lite-tts` |
| 1 | Orus | 沉稳醇厚的成年男声 | `gemini-3.8-flash-lite-tts` |
| 2 | Leda | 清亮柔和的成年女声 | `gemini-3.8-flash-lite-tts` |

试听片段使用最终素材的“叫地主 → 对三 → 王炸”，句间约 200 ms，没有变调、变速或后期声线替换：

- [温暖男声](audio/voice-preview-0.m4a)
- [沉稳男声](audio/voice-preview-1.m4a)
- [清亮女声](audio/voice-preview-2.m4a)

## 文件与来源

运行时素材位于 `app/src/main/assets/voices/{0,1,2}/{key}.m4a`，清单为 `app/src/main/assets/voices/catalog.json`。清单记录每句台词、路径、解码时长、编码文件与原始 PCM 的 SHA-256、声线、实际提示词、生成模式及验证结果。卡牌 J 的中文报法为“勾”，K 按英语字母 K 发音，A 为“尖”；救济提示是“救济金到账啦”。

这是 AI 专门生成的语音，没有使用系统朗读引擎，也没有模仿、复刻或采样某位真人的录音。API 密钥只从 `GEMINI_API_KEY` 环境变量读取，不写入素材、脚本、日志或 APK。

生成遵循 Google 官方 [语音生成接口](https://ai.google.dev/gemini-api/docs/generate-content/speech-generation)；[3.8 Flash-Lite TTS](https://ai.google.dev/gemini-api/docs/models/gemini-3.8-flash-lite-tts) 接受逐句台词，`speech_metadata.style` 承载表演提示，输出 WAV PCM。

## 提示词与切分

完整且可复现的设定位于 `scripts/generate_voices.py` 的 `VOICES` 与 `COMMON_STYLE`。共同要求是标准普通话、自然亲切、轻快清楚、句末干净，不拖长尾音、不喊叫、不使用播音腔、笑声或背景音乐。声线设定在每句保持一致，未通过改变播放音高区分人物。

三个角色各生成一段带 `<long pause>` 的完整录音，原始长度分别是 98.52 秒、100.60 秒和 115.56 秒。每相邻短句在文字中插入暂停标记，提示模型留出安静间隔。模型实际输出的外部停顿约 0.53–1.16 秒，并未严格遵循提示中的 1.5 秒，因此切分依据真实波形。

脚本以 10 ms 窗口计算 RMS，安静阈值是 `max(50, 最大窗口 RMS × 0.012)`。只有至少 500 ms 的安静段才成为切分候选，按静音中点切开。每个角色必须有恰好 75 个有效边界才导出 76 条；边界不足或过多时立即停止，不按猜测发布。

女声“没人叫地主，重新发牌”中的逗号也产生了长暂停。两个分段通过独立音频识别分别确认是“没人叫地主”和“重新发牌”，随后显式合并第 68 个边界（从 0 开始），并再次识别合并后的完整文件。对应复现参数为 `--merge-gap 2:68`，记录在清单中。

“对勾”的座位 0 与 2 素材还逐句重新生成了一次，使用同音、意义明确的输入“对钩”，并提示 `gōu` 第一声、禁止念 K；运行时台词仍显示“对勾”。这两条新文件在发布前按新 PCM 哈希重新识别。

## 母带与压缩

每句仅裁掉外部静音，保留约 90 ms 前缘与 150 ms 后缘，避免吞掉辅音或尾音。统一活动语音 RMS 目标为 -20 dBFS，峰值上限 -1 dBFS，最大增益 3 倍，首尾静音中的 4 ms 使用淡入淡出。所有语音为单声道 24 kHz。

最终使用 macOS `afconvert` 编码为 AAC-LC，目标码率 48 kbps，容器 M4A。每条编码后都重新解码并验证：声道与采样率正确、解码样本数与原始 PCM 完全相同、信噪比至少 12 dB。低于门槛时只对该条提高码率。实际 227 条使用 48 kbps；座位 1 的“七”使用 64 kbps。`durationMs` 来自实际解码样本数。原始 WAV 保存于生成目录下的归档，不进入 APK。

实测音频共 **2,204,168 字节（2.10 MiB）**，连同清单共 **2,395,934 字节（2.28 MiB）**；相对 9,224,112 字节的源 PCM 缩小约 76.1%。228 条解码样本数全部一致，最低信噪比 **12.90 dB**；单条时长为 **400–2750 ms**。这些是音频素材尺寸，不包含应用其他资源。

## 核对范围

`scripts/audit_voices.py` 对全部 228 个独立文件进行音频识别。每次输入只提供任意数字 ID 和音频，不提供预期文本或有含义的文件名；模型逐文件转录后才与清单台词比较。初检采用 `gemini-3.1-flash-lite`；“救济金”、女声重新发牌与操作提示等关键疑项还采用 `gemini-3.8-flash` 单文件复核。“对勾”的两个重生成文件重新使用独立音频识别，准确转录为“对勾”。报牌、王炸、飞机、叫地主、余牌提醒等全部包含在范围内。

审查保留原始转录，并区分正确牌点与语气词差异。“勾/钩”和“牌型/排型”等同音拼写、结尾“啦/了”或“哦/哟”等自然语气词变化可以在完整语句不变时接受；牌点错误、吞音、额外邻句或重复不予接受。“对勾”不会把 K 当作同音变体。

机器初检曾把操作提示误当作不属于报牌，以及把“救济金”写成其他字；强模型单文件复核用于排除这类识别器误判。切分后的女声重新发牌提示也按完整文件单独复核。最终 **228/228 条被接受，全部审查哈希与当前源 PCM 相符**：216 条台词正确或仅有同音字写法差异，11 条只有结尾自然语气词差异，1 条为“牌型/排型”的同音拼写。审查记录见 [voice-audit.json](audio/voice-audit.json)。这是全量自动识别与编码验证，不能等同于逐条人工试听。

## 复现

脚本使用 Python 标准库，无需安装 Gemini SDK。生成前在进程环境中设置 `GEMINI_API_KEY`。密钥对应项目须有足够的专用 TTS 请求额度；额度耗尽时脚本会停止，不能依靠不断重试恢复。`--direct` 可在本机代理不兼容 HTTPS 时直接连接 API。

```sh
python3 scripts/generate_voices.py --batch --direct
```

录音会保存在 `/private/tmp/doudizhu-voice-source`，再次运行时复用源录音。若静音边界数不匹配，先独立识别问题段，再使用明确的 `--merge-gap` 重新切分。勿复用与新台词不一致的旧源录音；修改台词后使用新 `--sources` 目录或逐句重新生成该 key。

```sh
python3 scripts/generate_voices.py --batch --direct --seats 2 --merge-gap 2:68
python3 scripts/generate_voices.py --direct --seats 0,2 --keys pair_j --replace
python3 scripts/audit_voices.py --batch-size 12 --workers 2
python3 scripts/audit_voices.py --model gemini-3.8-flash --keys pair_j,relief --recheck --batch-size 1
```

只有全部文件的识别结果被接受且与当前 PCM 哈希一致，压缩脚本才允许发布。

```sh
python3 scripts/compress_voices.py
```

macOS 音频编码器需要访问系统 codec 服务，在受限进程沙箱内可能不可用，应为上述压缩命令使用正常的本机音频服务访问权限。压缩脚本保留所有原始 WAV、验证实际 AAC 解码结果、更新清单，并生成三个试听片段。
