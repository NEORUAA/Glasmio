# iOS 固定 Header 与跨页 morph 改造方案

> 状态：设计与分析，尚未实现。基线：`cf57af9c63b7e7a27ee9c12b1602dc3d59df6b54`。本文件说明 Glasmio 的代码改造；动画表现与参数单独收录在 `docs/ios-header-motion.zh-CN.md`。以下新增类型名均为建议 API。

## 1. 当前问题与目标结构

当前按钮已经处于列表 overscroll 之外，但仍属于导航 entry。`NavDisplay` 移动整个 entry 时，按钮继承页面的位移、缩放、裁切和遮罩。

已核对的代码位置：

| 文件与行号 | 当前行为 | 改造含义 |
| --- | --- | --- |
| `sample/src/main/java/io/github/neoruaa/glasmio/sample/MainActivity.kt:373` | `NavDisplay → NavEntry → GlasmioBackdropHost → SamplePinnedPage` | 导航 entry 包括整个 Header，是切页时按钮一起移动的根因 |
| 同文件 `:537` | 每页独立创建 `GlasmioPinnedPage` 与主题按钮 | 跨页没有同一份按钮状态和玻璃容器 |
| 同文件 `:465` | Bottom bar 是 NavDisplay 的外部 sibling | Header 应采用同样的常驻层级 |
| `glasmio-scaffold/src/main/java/io/github/neoruaa/glasmio/scaffold/IosPinnedListPage.kt:220` | body、overscroll 与顶部 toolbar 已分层 | 保留滚动隔离，再把顶部层的所有权提升到导航范围 |
| `glasmio-scaffold/src/main/java/io/github/neoruaa/glasmio/scaffold/AppBar.kt:35` | `isPinned = true` 是 nested scroll 行为 | 单改 pinned 不会解除 entry 的祖先变换 |
| `glasmio-ui/src/main/java/io/github/neoruaa/glasmio/ui/IosToolbars.kt:153` | leading / trailing 各自创建 action group | 分组布局可复用，持久身份和跨页动画需补齐 |

目标层级：

```text
NavigationRoot — stable screen coordinates
│
├── BodyCapture
│   └── NavDisplay
│       ├── outgoing body ─── slides / clips / dims
│       └── incoming body ─── slides / clips / dims
│
├── HeaderHost — stays mounted across entries
│   ├── scroll-edge material
│   ├── leading glass shells + symbols
│   ├── compact title
│   └── trailing glass shells + symbols
│
└── BottomBar
```

对应屏幕效果：

```text
Before                     During push                    After
┌──────────────────┐       ┌──────────────────┐          ┌──────────────────┐
│              (☾) │       │              (☾) │          │              (☀) │
│ Library          │       │ Libra… │ Detail  │          │ Detail           │
│                  │       │   ← body moves  │          │                  │
└──────────────────┘       └──────────────────┘          └──────────────────┘
       screen anchor ─────────────┴─────────────────────────────┘
```

容器变宽时允许自身几何变化；同尺寸换图标时保持屏幕锚点。固定的含义是解除页面祖先变换，并不禁止容器自身 morph。

## 2. 文件级改造范围

### 2.1 scaffold：增加导航范围的 HeaderHost

建议新增 `GlasmioNavigationHeaderHost` 与 Header 状态类型。它拥有导航期间仍需存活的 leading / trailing 容器、source / target 前景和布局快照。

拆分 `GlasmioPinnedPage`：

- body-only 部分负责背景、列表、overscroll、内容 inset。
- Header overlay 部分负责渐进模糊、紧凑标题和按钮。
- 现有公共 API 可继续包装这两部分，供没有导航容器的独立页面使用。
- 导航 sample 改用 body-only 页面与外置 HeaderHost。

大标题继续留在 body 内，参与原本的滚动与页面转场。按钮、紧凑标题、顶部材质留在常驻层，分别驱动各自的动画。

`IosPinnedListPage.kt:211` 的当前高度为 `statusBarHeight + 62.dp`，body 顶 inset 再加 `10.dp`；`IosToolbars.kt:160` 的 TopBar 高度为 `54.dp`。应统一由 HeaderHost 输出 inset，避免迁移后重复叠加状态栏高度。这些是当前项目布局值，不是已证实的 iOS 全机型常量。

### 2.2 数据模型：区分页面、玻璃组、操作与视觉内容

```text
HeaderSpec
  entryKey                         // back-stack instance identity
  title / subtitle / style
  collapseState                    // belongs to this entry
  scrollEdgeState
  leadingGroups / trailingGroups

HeaderGroupSpec
  groupKey                         // persistent glass-shell identity
  edge                             // logical leading / trailing
  material
  ordered actions
  grouping policy

HeaderActionSpec
  actionKey                        // semantic identity across entries
  visualKey                        // symbol / text / style identity
  enabled
  onClick
  accessibility description
  optional menu anchor
```

`appearance.toggle` 可作为主题操作的稳定 key。`moon` 和 `sun.max` 是这个操作的视觉状态，不能拿图标字符串代替 action 身份。不同页面中同名但语义不同的操作也不能误匹配。

这一模型是 Glasmio 的建议契约，不宣称 Apple 内部使用相同字段。UIKit 测试中同一图标保持稳定，可作为行为目标；其完整匹配算法须以反编译证据为准。

导航期间同时保留 from / to 的 HeaderSpec。不能仅在 `backStack.last()` 改变时立刻替换当前 Header，否则无法取消返回，也无法在内容替换期间保留旧图标。

### 2.3 几何：先测量两个端点，再生成每帧状态

```text
HeaderLayoutSnapshot
  host size / safe insets / layout direction
  each group: bounds / continuous corner geometry / material
  each action: bounds / content baseline / content center
  compact title bounds

HeaderPresentationFrame
  persistent group identity
  presentation bounds
  shell scale / deformation
  source content transform / alpha / blur
  target content transform / alpha / blur
  current hit targets
```

所有 endpoint 先转换到稳定 HeaderHost 坐标。单按钮变双按钮时，改变基础容器宽度并维持对应外缘，再叠加 shell 的局部弹性。不要把整行图标沿 X 轴拉宽来模拟胶囊变宽。

同图标、同尺寸且内容没有变化的稳定操作应保留现有前景。视觉内容变化时使用两份可独立绘制的前景，分别执行内容 scale / alpha / blur；玻璃底层保持一份。

### 2.4 glass：扩展现有 renderer 与 group，保留材质能力

`glasmio-glass/src/main/java/io/github/neoruaa/glasmio/glass/IosToolbarActionGroup.kt:339` 当前提供的是单实例内的 merged / separated 动画，使用 `dampingRatio = 0.86f`、`stiffness = 380f`。它不是原生跨页 morph 的参数来源。

当前成员在 `:444` 注册，身份来自 group / highlight / material 等对象，没有 `actionKey` / `groupKey`。建议补充稳定身份及 endpoint / presentation 几何输入，继续让该类处理单端布局分组，不把整个导航状态机塞进其中。

现有能力可复用：

| 能力 | 位置 | 用途 |
| --- | --- | --- |
| 连续胶囊与连接轮廓 | `IosToolbarActionGroup.kt:1192` | 基础轮廓生成 |
| foreground portal / occlusion | 同文件 `:644` | 旧新前景保留、避免重复绘制；需要从 menu 用途抽象为通用契约 |
| 动态 bounds / radius / path / material | `GlassMorphRenderer.kt:165` | 绘制当前 morph 几何与材质 |
| morph 绘制分支 | `GlassSurface.kt:794` | 接入已经计算好的导航 morph 状态 |
| 固定背景采样 | `GlassBackdrop.kt:199` | 抵消玻璃自身局部形变对背景坐标的影响 |

现有 `separationProgress` 控制连通颈部与分离拉伸，并 clamp 到 `[0,1]`。不能让它兼任导航全部属性；位置、bounds、kick、内容替换和弹性超调需要各自的状态。

导航 scale pulse 需新增两组可交替的 X/Y progress state。每维先计算 `r = min(sqrt(1.2), sqrt((size + 16dp) / size))`，再以 `L(r,p)=1/r+(r-1/r)p` 合成 `L(r,pA)*L(r,pB)`。初始一组0、另一组1；鼓起和回落分别改变通道，保留当前值与速度。局部坐标要统一单位，不能用 px 尺寸直接除以未换算的16dp。

这条公式来自真实执行端，替代泛化的 `sin²` pulse。它限制目标放大量，而非把最终 presentation 硬 clamp 到1.2；大胶囊的横向目标会低于1.2。

### 2.5 sample：迁移所有 Header 入口

`MainActivity.kt` 的通用页面 `:422`、`SamplePinnedPage:515`、Lists 页面 `:908`、Menus 页面 `:1144` 都要接入同一个 HeaderHost。

每个 entry 的滚动状态、列表/网格状态和 menu 回调须按 entryKey 保存；转场取消时恢复来源 entry 的同一份状态。不要将已经离开 composition 的 callback 无限保存在全局 registry 中。

## 3. 页面与 Header 共用时间来源

项目当前使用 `androidx.navigation3:navigation3-runtime:1.2.0` 与 `top.yukonga.miuix.kmp:miuix-navigation3-ui-android:0.9.3`，声明见 `sample/build.gradle.kts:51`。

该精确版本的官方 sources.jar：

`https://repo.maven.apache.org/maven2/top/yukonga/miuix/kmp/miuix-navigation3-ui/0.9.3/miuix-navigation3-ui-0.9.3-sources.jar`

其中 `commonMain/androidx/navigation3/ui/NavDisplay.kt`：

| 行号 | 行为 |
| --- | --- |
| 464–471 | 内部创建 `SeekableTransitionState` / `Transition` |
| 630–650 | predictive back 对内部状态 seek，并处理打断时的曲线转换 |
| 653–703 | 内部处理完成与取消 |
| 740–825 | `AnimatedContent` 包裹完整 scene 内容，并应用 clip |
| 934–960、1006 | 默认 push / pop 为 `500ms`，使用 `NavTransitionEasing(0.8f, 0.95f)` |

这些是 Miuix 参数。外置 HeaderHost 不能直接读取 entry 内才有的 `LocalNavAnimatedContentScope`；它也不是一个公开的、能涵盖所有阶段的导航 fraction API。

推荐给项目使用的 NavDisplay 做受控封装或维护版本，暴露它内部真实导航状态。若希望完全掌控原生轨道与手势，则由自有 `NavigationMotionController` 统一驱动 body 与 Header，改动范围更大。

```text
NavigationMotionFrame
  transitionId
  fromEntryKey / toEntryKey
  operation                        // push / pop / tab
  phase                            // tracking / settling / canceling / idle
  timelineFraction
  gestureFraction
  playTime
  velocity
  settledEntryKey
```

共用时间来源，不等于全部属性共用一个插值值。Header 的内容、几何、pulse、kick 使用各自原生 spring；页面的 350ms 协调器声明时长，也不能拿来截断 Header 弹性尾段。

主 spring 可转换为 Compose 同量纲的 `dampingRatio = 0.75f`、`stiffness = 228.1251f`，但只填这两个数还不足以等价：Compose 的结束阈值、每次重定向的初始速度、延迟期间是否继续积分及多个 pulse 的组合也必须一致。建议集中增加 `NavigationHeaderMotionSpec` 和可 seek 的采样器，按动画文档的物理方程生成 presentation frame；生命周期由仍活动的各条轨道共同决定，不能在 Compose 的某一个 Animatable 完成时销毁整组旧内容。

当前 Miuix 默认 500ms 也不能当作原生时长。若目标包含页面与 Header 的相对节奏，需要另外替换或控制 body 的原生 pacing；本轮读到 coordinator 为350ms，但其内部曲线 rawValue=7，并不允许直接命名为公开 ease-in-out。只更换毫秒值不能宣称页面运动也已1:1。

禁止依靠 route 改变后另开一个定时 `animateFloatAsState` 来猜测进度。它无法正确处理手势回拖、取消、快速重入、转场中断和不同属性尾段。

## 4. 中断、返回与状态所有权

- 正常 push / pop：保留两端快照，提交目标 entry 后再清理已不再可见的前景。
- 手势跟踪：输入真实进度与速度，按跟踪参数更新 presentation。
- 取消：回到 source 的同一份快照和交互 owner。
- 完成：收敛到 target，再清理 source。
- 中途反向或新导航：从当前屏幕值与当前速度接续；不能从旧端点重新播放。
- 内容图层允许同时存在两份，但只有一份语义与点击 owner，避免一次点击触发两个 callback。

本轮原生测量没有通过真实触摸完成交互式返回，手势进度映射必须从反编译路径核对并在后续实现时补原生对照，不能把非交互式视频反放当作证明。

## 5. 背景采样与坐标

常驻 Header 应采样 outgoing / incoming body 的最终合成结果。当前 `navigationBackdrop` 记录的整棵 NavDisplay 含有旧 Header；拆分后必须确保 BodyCapture 内只有页面内容，Header 在其外。

当前 group bounds 是局部坐标，menu anchor 是 window 坐标，morph viewport 使用 root 坐标。endpoint 必须先归一到稳定 host，再在 popup 边界显式转换。否则按钮表面看似固定，菜单仍会从旧页面位置弹出。

背景采样矩阵保持屏幕锚定。壳体鼓起时，后方页面不能跟着放大；变宽时也不能把已采样的局部纹理横向拉伸。

采样区域应覆盖 source / target bounds 的 union，并加入 blur、折射和阴影余量。现有 renderer 的 viewport 相同并不代表 source placement 永远相同，切换 backdrop 或其变换时应正确失效录制缓存。

按压 transform 和导航 morph 各组合一次。布局快照保存未受按压形变影响的基准 bounds，避免把已变换尺寸反馈到下一帧布局，造成逐帧累积漂移。

## 6. 反编译证据与参数落点

本轮并非仅凭录屏估计数值。已从本机 `24A434` 的 `dyld_sim_shared_cache_arm64` 提取 `UIKitCore`、`SwiftUICore`、`AnimationKit`，使用本机 IDAT / Hex-Rays 反编译，并回到原 shared cache 解析跨库 stubs 与 ObjC method list。地址均为 image VA，未加运行时 ASLR。

真实调用链：

```text
Modern v2:
UIKit transaction builder @ 0x1c4fdd948
  → VariantContext(.toolbar)
  → SettingsOverride.override(context:) @ 0x1c6ff2148
  → prototypeSettingsEnabled == false
  → SettingsOverride.defaultOverride(context:) @ 0x1c6ff2468
  → SwiftUI.Spring.init(duration:bounce:) @ 0x1c71909ac

Also independently defined in UIKit container settings:
NavigationBarTransitionContainer update @ 0x1c520b7d8
  → NavigationBarPlatterContainer Settings factory @ 0x1c4fe7ed4
```

现代 v2 在 toolbar 分支独立构造下述参数，与 UIKit 容器 Settings 的数值一致；不再需要凭旧类的名称推断现代实现。

常驻层结构由 `UIKitCore 0x1c520b138` 创建：`hostedViewContainer`、`underGlassTransitionContainer`、`platterContainer` 分开挂载；现代路径使用 `NavigationBarPlatterContainer_v2.model` / `NavigationBarPlatterModel`。不能只分析旧版 ObjC `_UINavigationBarTransitionContext` 就推断现代导航栏玻璃行为。

本机原生探针动态读取：

```text
PTDefaults.sharedInstance.prototypeSettingsEnabled = false
UIAccessibilityIsReduceMotionEnabled() = false
```

因此应以 Settings factory 的非 prototype 分支为当前正常基准。去除 Swift ABI、临时变量及内存管理细节后，该分支的赋值等价于：

```text
transition.spring = interactive
  ? Spring(duration: 0.2, bounce: 0)
  : Spring(duration: 0.416, bounce: 0.25)
transition.delayFactor = 0.33
transition.minDelay = 0.083
transition.maxDelay = 0.166

scalePulse = ScalePulseSettings.default
scalePulse.scaleUpSpring = Spring(duration: 0.292, bounce: 0.5)
scalePulse.scaleDownHeightSpring = Spring(duration: 0.416, bounce: 0.416)
scalePulse.scaleDownHeightDelay = 0.113
scalePulse.scaleDownWidthSpring = Spring(duration: 0.416, bounce: 0.5)
scalePulse.scaleDownWidthDelay = 0.142
scalePulse.transitionBlurSpring = Spring(duration: 0.416, bounce: 0.25)

translationKick = TranslationKickSettings.default
translationKick.offsetSpring = Spring(duration: 0.25, bounce: 0)
translationKick.offsetDelay = 0.05
translationKick.returnSpring = Spring(duration: 0.5, bounce: 0.166)
translationKick.returnDelay = 0.15
```

继承默认值的证据：

| 函数 / 位置 | 内容 |
| --- | --- |
| SwiftUICore `0x1c6ff18c4` | `ScalePulseSettings.default` |
| `0x1c6ff19fc`–`0x1c6ff1a0c` | `maxScale=(1.2,1.2)`，`maxPointScale=(16,16)` |
| 常量 pair `0x1c7a90600`，getter `0x1c6ff1534` | `transitionBlurRadius=10` |
| `0x1c6ff1a64`，getter `0x1c6ff1624` | `TranslationKickSettings.default`，`kickFactor=6` |
| `0x1c742e7f4` | `kickDistance = average(offsetVectors) / kickFactor` |
| `0x1c7804628`、`0x1c7806c10` | pulse 开平方限幅与双通道乘积合成；目标 `min(1.2,1+16/size)` |
| `0x1c7804534`、`0x1c7804958` | pulse state 的互补初值与交替通道 |
| UIKitCore `0x1c5178648` | 读取当前 UIView animation block 的 spring / curve / repeat / delay |

这套参数应在改造时集中为导航 Header motion spec。AnimationKit 的 `liquidMorph`、菜单 morph 及当前项目 action-group 的 `0.86/380` 均不能代替它。

本轮原生测量首次完整样本为 `32秒 / 1987帧`，另有独立录屏用于视觉核对。没有用录屏文件报告的名义帧率推断设备刷新率。presentation layer 偶发返回旧值，已通过稳定身份、相邻帧及反编译结果交叉核对；拟合只用于检验，不覆盖已恢复的静态常量。

研究产物保存在 `/private/tmp/glasmio-header-analysis-current/` 和 `/private/tmp/uikit-header-internals/`。前者包含 probe 源码、完整逐帧 JSON、运行时 settings 与 `native-navigation.mp4`；后者包含提取库、IDA 数据库、反编译文本与原始汇编。它们是临时研究文件，长期规格以仓库中的两份文档为准。

## 7. 实现顺序与验收

1. 拆分 body / header，在导航外建立常驻 HeaderHost。首先验证切页期间共享按钮的屏幕锚点稳定。
2. 补充 groupKey / actionKey / visualKey 和 source / target 布局快照。验证同图标不重复鼓起。
3. 接入真实导航时钟及完成、取消、重定向状态，再接各条原生 spring。
4. 接内容 scale / alpha / blur、shell bounds / kick / settle、组增删与文字按钮。
5. 修正 BodyCapture、屏幕采样坐标、menu anchor、命中区域和可访问语义所有权。
6. 用相同场景逐帧对照原生样本，再做手势与设置变化回归。

必要场景：同图标不变；图标 A→B；单按钮↔双按钮；单按钮↔三按钮；图标↔文字；组出现与消失；大标题滚动折叠；短列表 overscroll；返回完成、取消及反向；转场中快速再次导航；LTR / RTL；深浅色；系统减少动态效果与动画时长缩放。

建议几何验收目标：稳定锚点漂移不超过 `0.5` 个逻辑单位；其余动态位置与轮廓误差在同尺度对齐后控制在 `1` 个逻辑单位以内，主要阶段时序误差不超过一个显示帧。它们是本项目建议阈值，不是 Apple 规范。

“1:1”需要同时满足布局、运动模型、内容合成、玻璃采样与手势行为。只改固定层级可以解决随页移动；完整 morph 还需要本文其余状态与渲染改造。原生 shader、所有交互分支和不同系统版本未全部验证前，不应宣称逐像素等价。
