# iOS Header 固定按钮与页面切换 morph：动画效果及参数

> 参考系统：本机 iPhone 18 Pro 模拟器，iOS 27.0，build `24A434`，arm64。SDK 为 iPhoneSimulator 27.1；本文原生实现与参数来自 **27.0 runtime 二进制**。默认逻辑屏幕 `402×874 pt`，显示倍率 `3×`。适用范围：标准 UINavigationController 的普通导航栏按钮、玻璃组、非交互式 push / pop。本文只记录动画表现、参数与数学定义；代码改造见 `ios-header-implementation-plan.zh-CN.md`。

## 1. 参数的依据

本文区分三类信息：

- **反编译值**：从 `UIKitCore` / `SwiftUICore` 的实际导航栏调用链、setter 调用和 ARM64 常量恢复。
- **运行值**：本轮原生 UIKit 程序的 presentation layer、运行时配置读取与录屏结果。
- **尚未闭合**：目前没有足够调用链证据的行为，不指定一个看起来合理的数值代替。

导航栏已确认使用 UIKit 定制的 `SwiftUI.GlassContainer` 设置。`AnimationKit.MorphAnimationSpec` 中的通用八轨 spring 有另一套默认参数，不能替代本文件的导航栏参数。

## 2. 屏幕固定的视觉约束

### 2.1 页面移动与按钮运动相互独立

普通 push / pop 期间，页面正文可以横向进入、退出，按钮所在的玻璃组保持屏幕空间的锚点。页面位移不叠加到按钮上。

```text
Screen
┌──────────────────────────────────┐
│ (back)                    (action)│ ← fixed screen anchors
│                                  │
│ outgoing body ← │ incoming body ← │ ← navigation motion
│                                  │
└──────────────────────────────────┘
```

静止时，同尺寸右侧图标按钮的本轮运行值：

| 属性 | 值 |
| --- | ---: |
| 基础尺寸 | `44×44 pt` |
| 基础 rect | `(342, 62, 44, 44) pt` |
| 屏幕中心 | `(364, 84) pt` |
| 到右边缘的基础距离 | `16 pt` |
| 玻璃组 alpha | `1` |

换图标期间，右侧持久玻璃组的中心始终是 `(364,84) pt`；宽高随 scale pulse 改变。这些绝对坐标属于此设备、方向与 safe area，不是其他机型的通用坐标。

### 2.2 列表滚动

正文从大标题状态滚向紧凑标题状态时，按钮不随列表 offset、惯性滚动和回弹平移。本轮上滚及回滚中，右侧玻璃组中心和基础尺寸保持不变。

按钮后方的页面仍继续移动，玻璃内的背景应随真实页面更新。固定的是按钮的屏幕锚点，不是将背景纹理冻结。

### 2.3 容器尺寸变化时的锚点

单按钮变为多按钮时，容器基础几何沿相应边缘扩展。右侧组的基础 trailing edge 在本轮样本中保持 `x=386 pt`；宽度增加后，基础中心自然向左移动。

scale pulse 会让最终可见边缘临时越过该基础边缘。因此，“固定”不等于每一帧的可见最外缘都完全不动。

## 3. 一次 morph 包含的视觉轨道

| 轨道 | 视觉作用 |
| --- | --- |
| 基础几何转场 | 将旧组的位置、宽高与布局变为目标布局 |
| Scale up | 在内容变化时把玻璃短暂鼓起 |
| Height return | 使高度较早回落，并保留自己的弹性 |
| Width return | 使宽度较晚回落，产生横纵不同步的流体感 |
| 内容替换 | 旧新图标分别缩放、淡化和模糊 |
| Translation kick | 根据成员位移给组附加短促位移，再回到目标位置 |
| 出现 / 消失 | 当某一侧完全没有对应玻璃组时，进行整体显隐 |

这些轨道有各自的 spring 或阶段参数。页面协调器的声明时长、玻璃主 spring 的频率参数、内容显隐时间和最终回落时间不相同。

## 4. 导航栏专用 spring 参数

以下表格来自导航栏 Settings 生成函数 `UIKitCore 0x1c4fe7ed4`，并已在现代 v2 的 `SwiftUICore 0x1c6ff2468` toolbar override 分支独立核对。v2 transaction builder `UIKitCore 0x1c4fdd948` 明确选择 `.toolbar` 并传入该 override；本机运行时 `prototypeSettingsEnabled=false`，采用正常分支。API 为 `SwiftUI.Spring(duration:bounce:)`。`duration` 是该构造函数的参数；不能把它理解成必须在该时刻硬切到最终值的固定总时长。

| 轨道 | duration | bounce | 该轨道的 delay 配置 |
| --- | ---: | ---: | ---: |
| 主几何 `TransitionSettings.spring`，非交互 | `0.416 s` | `0.25` | 受主转场 delay 设置控制 |
| 主几何，interactive toolbar | `0.2 s` | `0` | 跟踪分支 |
| 玻璃鼓起 `scaleUpSpring` | `0.292 s` | `0.5` | 此处没有额外 setter |
| 高度回落 `scaleDownHeightSpring` | `0.416 s` | `0.416` | `0.113 s` |
| 宽度回落 `scaleDownWidthSpring` | `0.416 s` | `0.5` | `0.142 s` |
| 内容模糊 `transitionBlurSpring` | `0.416 s` | `0.25` | 此处没有额外 setter |
| 位移 kick `offsetSpring` | `0.25 s` | `0` | `0.05 s` |
| kick 回位 `returnSpring` | `0.5 s` | `0.166` | `0.15 s` |

高度回落 bounce 为 `0.416`，它与同一行的 duration 数字相同，已经逐条核对寄存器与 setter，不是转写错误。

主转场另外配置：

| 参数 | 值 |
| --- | ---: |
| `delayFactor` | `0.33` |
| `minDelay` | `0.083 s` |
| `maxDelay` | `0.166 s` |

这些是实际设置字段。不能仅根据字段名就把 `delay = clamp(duration × factor, minDelay, maxDelay)` 当成已证明的执行公式；调度公式以执行端证据为准。

## 5. spring 的数学含义

`SwiftUICore 0x1c71909ac` 的 `Spring.init(duration:bounce:)` 给出以下转换。设 `D` 为 duration，`b` 为 bounce：

```text
0 ≤ b ≤ 1:   ζ = 1 − b
−1 < b < 0:  ζ = 1 / (1 + b)
ω = 2π / D
```

本文参数均位于 `0≤b<1`。以质量 `m=1` 归一化：

```text
stiffness k = ω²
physical damping c = 2ζω
```

| 轨道 | 阻尼比 ζ | stiffness k | physical damping c |
| --- | ---: | ---: | ---: |
| 主几何 / 内容模糊 | `0.75` | `228.125102` | `22.655716` |
| 交互式主几何 | `1` | `986.960440` | `62.831853` |
| 鼓起 | `0.5` | `463.013905` | `21.517758` |
| 高度回落 | `0.584` | `228.125102` | `17.641251` |
| 宽度回落 | `0.5` | `228.125102` | `15.103811` |
| 位移 kick | `1` | `631.654682` | `50.265482` |
| kick 回位 | `0.834` | `157.913670` | `20.960706` |

对于静止起步的单位阶跃，欠阻尼解为：

```text
ωd = ω √(1−ζ²)
p(t) = 1 − e^(−ζωt) [cos(ωd t) + (ζω/ωd) sin(ωd t)]
```

临界阻尼 `ζ=1` 时：

```text
p(t) = 1 − (1+ωt)e^(−ωt)
```

对于中途改目标的情况，必须使用当前展示值与当前速度作为新的初始条件；上述零初速单位阶跃不能直接用于每次重启。

`bounce=0.25` 也不表示几何超出目标 `25%`。它先映射到阻尼比，再由动力系统产生超调。

## 6. 玻璃鼓起与回落

### 6.1 幅度配置

导航栏先取得 `ScalePulseSettings.default`，再覆盖上表的 spring / delay。保留的反编译值为：

| 参数 | 值 |
| --- | ---: |
| `maxScale.width` | `1.2` |
| `maxScale.height` | `1.2` |
| `maxPointScale.width` | `16 pt` |
| `maxPointScale.height` | `16 pt` |
| `transitionBlurRadius` | `10 pt` |

`1.2` 和 `16 pt` 共同限制脉冲的目标幅度。`ScalePulseModifier.body(content:)` 先对 maxScale 开平方，`ScalePulseEffect.effectValue(size:)` 再按当前几何尺寸限幅。对 `W>0、H>0`：

```text
rx = min(√1.2, √((W+16)/W))
ry = min(√1.2, √((H+16)/H))

L(r,p) = 1/r + (r−1/r)p

scaleX = L(rx,pAX) × L(rx,pBX)
scaleY = L(ry,pAY) × L(ry,pBY)
```

W、H 为此帧参与效果计算的几何尺寸，单位为 pt。完整脉冲目标每个方向为：

```text
targetScale(size) = min(1.2, 1 + 16/size)
```

| 基础维度 | 脉冲目标尺度 |
| --- | ---: |
| `44pt` | `1.2` |
| `100.6667pt` | 约 `1.15894` |
| `154pt` | 约 `1.10390` |

限制的是目标，不是逐帧 presentation 结果。spring 进度允许超调，因此不能在合成之后再把瞬时尺度硬裁剪到 `1.2`。

### 6.2 两组进度交替生成脉冲

静止状态中，一组进度为 `0`，另一组为 `1`，两个 L 的乘积恰为 `1`。变化发生时，一组向 `1` 运动以鼓起，另一组再按各维回落 spring 向 `0` 运动；后续触发交替两组通道。

这与每次把一个统一 scale 重新设为1再播放固定 keyframe 不同。应保留当前两组进度及其速度，避免连续触发时跳回初始形状。

几何执行函数为 `SwiftUICore 0x1c7806c10`，上游开平方与效果装配位于 `0x1c7804628`；公式已逐条核对原始 ARM64。

### 6.3 横纵方向错开

玻璃先鼓起，随后高度以 `113ms` 的回落 delay 开始返回，宽度的回落 delay 为 `142ms`。相差 `29ms`，并且两个方向的回落阻尼不同。

因此，中间帧会出现短暂的横向拉长，尾段也会先在一个方向上稳定。把它合成一个等比 `scale` 无法得到相同轮廓。

### 6.4 同尺寸换图标的运行参考

以下来自本轮 `push-symbol` 场景。时间零点是该场景的第一条显示采样，不是内部动画对象的 beginTime；绝对 offset 含布局与提交延迟。数据用于检查形态，不作为硬编码延迟。

| 场景相对时间 | shell scale X | shell scale Y | 屏幕中心 |
| --- | ---: | ---: | --- |
| `100ms` | `1.00000` | `1.00000` | `(364,84)` |
| `150ms` | `1.11338` | `1.11338` | `(364,84)` |
| `200ms` | `1.20419` | `1.19941` | `(364,84)` |
| `233ms` | `1.22347` | `1.18741` | `(364,84)` |
| `300ms` | `1.12329` | `1.07904` | `(364,84)` |
| `400ms` | `0.97797` | `0.98027` | `(364,84)` |
| `450ms` | `0.96524` | `0.97722` | `(364,84)` |
| `600ms` | `0.99809` | `0.99901` | `(364,84)` |
| `700ms` | `1.00495` | `1.00000` | `(364,84)` |
| `850ms` | `1.00000` | `1.00000` | `(364,84)` |

玻璃 shell 自身 alpha 在该场景一直为 `1`。内容可以淡出，但玻璃不能随旧图标一起淡掉，再生成第二个壳。

## 7. 图标内容的 morph

### 7.1 旧新内容分别合成

本轮运行层显示两份内容：旧图标、新图标。视觉上是缩放、模糊和透明度交接，不应理解成任意两个 SF Symbol 的向量 path 顶点插值。

以内容替换进度 `p` 表示，本轮读出的关系为：

```text
old alpha = 1 − p
new alpha = p

old local scale = 1 − 0.8p
new local scale = 0.2 + 0.8p

old blur radius = 10p pt
new blur radius = 10(1−p) pt
```

`local scale` 指内容自身的变换；最终屏幕尺寸还会受玻璃父层的 scale pulse 影响。不能把最终看到的图标宽度直接当成本地缩放值。

| 内容阶段 | 旧图标 | 新图标 |
| --- | --- | --- |
| `p=0` | scale `1`、alpha `1`、blur `0` | scale `0.2`、alpha `0`、blur `10pt` |
| `p=0.5` | scale `0.6`、alpha `0.5`、blur `5pt` | scale `0.6`、alpha `0.5`、blur `5pt` |
| `p=1` | scale `0.2`、alpha `0`、blur `10pt` | scale `1`、alpha `1`、blur `0` |

运行对象在弹性尾段可读到小于 `0` 的旧内容 opacity 或大于 `1` 的归一化进度。可见合成的透明度应在有效范围内，不能把动画状态本身一律 clamp，破坏相关几何的超调。

### 7.2 相同内容保持稳定

本轮 `moon → moon` 的 push / pop 中，右侧组的尺寸始终是 `44×44pt`、中心始终是 `(364,84)`，没有观察到内容替换与玻璃鼓包。

这意味着“页面变了”本身不应无条件触发按钮 morph。需要比较持续存在的内容是否实际变化。

完整的系统 identity 匹配规则没有仅凭这一场景得到证明；尤其是相同图标但不同操作语义、同组重排与多组拆合，不应直接推出相同结果。

## 8. 单按钮与多按钮互变

玻璃组保持连续可见，基础 bounds 向目标尺寸变化，叠加 scale pulse；旧新内容各自替换。

本轮样例的静止端点：

| 内容 | 基础 rect | 基础宽高 | 基础右边缘 |
| --- | --- | --- | ---: |
| 单 `moon` | `(342,62,44,44)` | `44×44pt` | `386pt` |
| `ellipsis` + `square.and.arrow.up` | `(285.3333,62,100.6667,44)` | `100.6667×44pt` | `386pt` |
| 三图标组 | `(232,62,154,44)` | `154×44pt` | `386pt` |
| `Done` 文字操作 | `(312.3333,62,73.6667,44)` | `73.6667×44pt` | `386pt` |

这些宽度包含系统字体、SF Symbol 的内在布局与按钮 style，不能以固定倍数推广到任意两个或三个图标。

| 场景 | 本轮可见宽度范围 | 基础中心 X 范围 |
| --- | --- | --- |
| 一图标 → 两图标 | `44 → 峰值约109.9447 → 100.6667pt` | `364 → 最低约334.9855 → 335.6667pt` |
| 两图标 → 一图标 | `100.6667 → 最低约41.3209 → 44pt` | `335.6667 → 最高约364.6812 → 364pt` |
| 一图标 → 三图标 | `44 → 峰值约159.8124 → 154pt` | `364 → 最低约307.6777 → 309pt` |
| 一图标 → Done | `44 → 峰值约83.8962 → 73.6667pt` | `364 → 最低约348.8101 → 349.1667pt` |

宽度变化需要表达真实基础尺寸变化；仅把圆形图标按钮的最终像素横向拉成胶囊，会连图标和玻璃纹理一起拉伸。

## 9. 整组出现与消失

当某侧没有可延续的玻璃组时，整体显隐与“持久壳内换图标”不同。

本轮返回按钮出现时：

```text
shell alpha: 0 → 1
shell scale: 1.2 → 1
screen center: constant
```

返回按钮消失时：

```text
shell alpha: 1 → 0
shell scale: 1 → 1.2
screen center: constant
```

在当前样本内，可用出现进度 `p` 描述可见的关系：

```text
appear shell scale = 1.2 − 0.2p
appear alpha = p

disappear shell scale = 1 + 0.2p
disappear alpha = 1 − p
```

右侧 action 组整体变空和重新出现也观察到相同方向的放大淡出 / 缩回淡入。这里的整体 scale 范围与“旧图标缩到0.2、新图标从0.2起步”是不同层级。

退出尾段中，旧 shell 可能仍以透明状态短暂存在。判断动画是否仍可见，必须考虑所有祖先的 alpha 与 hidden 状态，不能只读取该 view 自己的 alpha。

## 10. 位移 kick

反编译得到：

| 属性 | 值 |
| --- | ---: |
| offset spring | duration `0.25s`、bounce `0` |
| offset delay | `0.05s` |
| return spring | duration `0.5s`、bounce `0.166` |
| return delay | `0.15s` |
| kickFactor | `6` |

`SwiftUI.GlassContainer.TranslationKick.kickDistance` 的反汇编给出：

```text
empty offset list → (0,0)
otherwise kickDistance = average(offsetVectors) / kickFactor
```

`kickFactor=6` 是位移缩放因子，不是每次固定移动 `6pt`。若对应成员的平均位移为零，这条轨道不会凭空制造页面方向的移动。

哪些匹配成员向 offsetVectors 贡献位移仍需逐个场景核对；不能把整页的宽度作为该数组输入。

## 11. 页面时序、父动画与弹性尾段

本轮 UINavigationController transition coordinator 对普通 push / pop 报告：

| 属性 | 运行值 |
| --- | --- |
| `transitionDuration` | `0.35s` |
| `isInteractive` | `false` |
| `completionCurve.rawValue` | `7` |

`7` 是此运行对象返回的原始枚举值，不能当作公开 UIView.AnimationCurve 的命名曲线。内部 `uiFractionalProgress` pacing layer 的 timing function 为线性、speed 为 `0`，也不能直接推出所有屏幕属性匀速；它用于被驱动的协调进度。

导航栏默认主 spring 参数为 `.416/.25`，同时存在从当前 UIView animation block 继承 spring、时长、曲线、repeat 与 delay 的路径。调用环境改变时，实际时钟可能受继承动画影响。

因此，三个数字应分别理解：

```text
350ms   — 本轮普通页面 coordinator 的声明时长
416ms   — 导航栏默认主 spring 的构造参数
约850ms — 本轮同尺寸换图标的可读 shell 尾段收敛参考
```

不能在页面 `350ms` 完成时把 Header 直接跳到终值，也不能给所有属性硬套一个 `416ms` 的线性或贝塞尔动画。

## 12. 减少动态效果与交互边界

反编译的导航栏更新路径会读取主 spring 的 duration / bounce，并在 Reduce Motion 下把该处 bounce 置为 `0`，以相同 duration 重建 spring。SwiftUICore 的 appearance 降级还包含 scale 回到 `1`、blur 项归零的写入。

这证明存在减少动态效果分支，但本轮动态采样使用 Reduce Motion 关闭状态；没有把所有分支的完整可见效果都录制验证。不能将 AnimationKit 的通用 `liquidMorphReduceMotion` 表格作为这里的替代参数。

本轮未完成真实触摸的交互式返回、回拖取消和手势中途再导航采样。反编译已经确认 interactive toolbar 主 spring 为 `duration=0.2s、bounce=0`，即阻尼比 `ζ=1`；其余 pulse 配置仍由 toolbar override 提供。这是已恢复的跟踪参数，不等于已证明完整的手势进度映射。交互式完成阈值、速度投影与全部 retarget / 取消规则仍需进一步证据。

## 13. 参数边界

已经明确的目标包括：屏幕锚点稳定、持久壳不随内容淡出、横纵回落错开、内容 `0.2↔1` 缩放 / 透明度 / `10pt` 模糊、组宽度连续变化、同内容保持稳定、单独的 kick 与回位。

尚需进一步证据才能覆盖所有场景的部分包括：主 delay 执行计算、全部按钮身份匹配、继承动画对每条轨道的影响、交互式手势的映射与取消策略。内容替换公式属于动态实测，尚未形成完整静态属性调用闭环。本文没有用拟合参数冒充这些缺失的系统常量。
