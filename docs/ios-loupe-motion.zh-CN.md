# iOS 26+ 文本编辑长按放大镜：跟手动画实现与参数

> 参考系统：本机 iOS Simulator runtime **iOS 27.1，build `24A94425`**，arm64。
> 二进制：`dyld_sim_shared_cache_arm64`（`…/iOS 27.1.simruntime/Contents/Resources/RuntimeRoot/System/Library/Caches/com.apple.dyld/`），用 IDA Professional 9.4 + `dyld` cache loader 以 *single module* 方式加载 `UIKitCore`。
> 覆盖范围：**iOS 26 起的 glass 实现**（`_UITextMagnifiedGlassLoupeView` + `UITextLoupeSession`）。iOS 25 及更早的 light / 位图放大镜只在分流处一句话带过，不展开。
> 对象：`UITextView` / `UITextField` 里长按后跟随手指的那个透镜（caret loupe）。选区手柄拖动用的是同一个 session，差异在 §10 标注。

## 0. 一句话结论

放大镜不做物理跟手，它跑一条 **60 Hz `CADisplayLink`**：

```text
target  = constrainedModelPosition(modelPosition)      // 手指点（水平夹紧后）
center += (target − center) × 0.3                      // 一阶低通，τ ≈ 47 ms
center  = spring(damping 0.7, response 0.2 s) 落帧      // 再套一层欠阻尼 spring
|modelPosition − center| < FLT_EPSILON → displayLink 暂停
```

透镜里的画面不是自绘，而是对宿主窗口的 `_UIPortalView` 做 `scale(1.25)` 且以**手指点为不动点**；开合各 1～2 层 `UIViewSpringAnimationBehavior`，关闭时把 portal 缩到 `0.125`。

拖拽时的“液态”形变（§8）是**另一条链路**：`_UIFlexInteraction`（variant = loupe）拿 `velocityIntegrator.acceleration / 2500` 量出一次按压，在 `0.75～1.15` 之间拉伸 / 压扁 `_containerView`（`scaleX / scaleY` + 前缘 `drift`），用 `spring(damping 1.0, response 0.5)` 落帧。

---

## 1. 类与视图树

分流开关：

```text
+[UITextSelectionDisplayInteraction isModernSelectionViewEnabled]
  └─ +[UIKeyboard isRedesignedTextCursorEnabled]
       └─ _os_feature_enabled_impl("UIKit", "redesigned_text_cursor")   // 26/27 默认开
```

| 角色 | 类 | 方法入口 |
| --- | --- | --- |
| 透镜 | `_UITextMagnifiedGlassLoupeView` | 工厂 `0x1c4ef466c`，视图 `0x1c4107264` |
| 会话（生命周期/坐标/状态栏） | `UITextLoupeSession` | `+_beginLoupeSessionAtPoint:…` `0x1c4ef471c` |
| 手势 | `UIVariableDelayLoupeGesture`（`UILongPressGestureRecognizer` 子类） | `-_startEnoughTimeElapsedTimer` `0x1c4bf4110` |
| 手势行为 | `UITextRefinementTouchBehavior` | `textLoupeInteraction:gestureChangedWithState:…` |
| 坐标调参 | `UITextGestureTuning` | `0x1c429e9d0` |
| 液态形变 | `_UIFlexInteraction`（variant 3 = loupe） | `0x1c507c804`（update） |
| 玻璃本体（SDF 透镜） | `_UILiquidLensView` | `0x1c55454c0`（元数据） |

开关关闭时 `_makeLoupeViewForSourceView:` 会建 `_UITextLightLoupeView`（`78×44` 胶囊、不放大），不在本文范围。

透镜视图树（`initWithSourceView:`，`0x1c4107264`）：

```text
tableView / textView (窗口)
│
├─ _UITextMagnifiedGlassLoupeView            ← 每帧被 setCenter 的那个视图
│   └─ _containerView                        ← 可见透镜：clipped、圆角、阴影、_UIFlexInteraction
│       ├─ _portalContainerView
│       │   └─ _portalView                   ← 放大后的画面（sublayerTransform）
│       ├─ _glassView                        ← ClearGlass 背景
│       └─ _highlightView / _innerShadowView / _highlightDarkView / _highlightLightView
│
└─ _contentOffsetPortalContainerView         ← 插在透镜下方，看不见
    └─ _contentOffsetPortalView              ← 宿主窗口 live portal，供 _portalView 取源

（非视图属性）_displayLink = CADisplayLink 60 fps，removeFromSuperview 时 invalidate
```

关键 ivar（IDA 已恢复符号名）：`_modelPosition`、`_orientation`、`_visible`、`_animationState`、`_closeLoupeAnimationRunning`、`_transitionGroupCompletion`、`_dismissalProgress`、`_animatableSelectionWidget`。

隐式协议（同族实现共用的契约，本文只讨论 glass 的实现）：

```objc
@property CGPoint modelPosition;
@property CGSize  preferredSize;
@property CGFloat dismissalProgress;
- (CGPoint)constrainedModelPositionForPosition:(CGPoint)p;
- (void)setVisible:(BOOL)v animated:(BOOL)a completion:(void (^)(BOOL))c;
- (id<UICoordinateSpace>)containerCoordinateSpace;
- (id<UITextSelectionWidgetAnimating>)animatableSelectionWidget;
```

---

## 2. 完整时序

```text
touch down
   │
   ├─ UIVariableDelayLoupeGesture     delay 0.5 / 0.07 / 0.0（见表）
   │        │  fires
   │        ▼
   │  UITextRefinementTouchBehavior
   │        ├─ UITextGestureTuning        → 触摸对齐 / 吸附
   │        └─ +[UITextLoupeSession _beginLoupeSessionAtPoint:…]
   │               ├─ 定位到键盘容器 window，addSubview
   │               ├─ bounds = preferredSize(115×85)，center = constrained
   │               └─ setVisible:NO  →  setVisible:YES animated:
   │
   │  gesture changed …… 每帧
   │        └─ session moveToPoint:withCaretRect:trackingCaret:
   │                └─ loupeView.setModelPosition   →  displayLink.unpause
   │
   │        CADisplayLink 60 Hz ─► _displayLinkFired:   （§5）
   │        _UIFlexInteraction     ─► scaleX/scaleY/drift（§8，同一段手势期间并行）
   │
   ├─ lineBreakProgress ≥ 0.85 / 状态栏相交 / 手势结束
   │        └─ session.invalidate → spring(1.0, 0.23) 收起 + portal → 0.125
   ▼
```

---

## 3. 触发阶段

`-[UIVariableDelayLoupeGesture _startEnoughTimeElapsedTimer]`（`0x1c4bf4110`）：

```c
double delay = 0.5;
if ([self _allowsForShortDelay]) {
    if ([self _tracksPointerTouch])            delay = 0.0;   // 指针/触控板
    else if ([self isCloseToCaret] && ![self isWithinRecentTap])
        delay = [self shouldUseLegacyBehavior] ? 0.25 : 0.07;
}
self.delay = delay;
```

| 项 | 判定 | 值 |
| --- | --- | --- |
| `isCloseToCaret` | `hypot(touch − caretRect.center)` | `< 40 pt` |
| `isLongPress` | `CACurrentMediaTime() − lastTouchTimestamp` | `> 0.5 s` |
| `_allowsForShortDelay` | 有 textView 且（指针触控 **或**（正在编辑 且（非异步协议 或 可编辑）））且光标配件不可见 | — |
| `isWithinRecentTap` | `UITextSelectionInteraction._isNowWithinRepeatedTapTime` | — |
| `allowableMovement` | 偏好 `TextRefinement_AllowableMovement` | `8 pt` |

`allowableMovement = 8 pt` 是抖动容忍：手指在 8 pt 内移动仍算“原位长按”，超出后手势进入拖动分支，短延迟逻辑失效。

`isLongPress > 0.5 s` 决定“落点是在光标上拖动”还是“选词”，与透镜是否出现无关。

---

## 4. 坐标流水线：手指点 → `modelPosition`

### 4.1 触摸对齐（`UITextGestureTuning`，`0x1c429e9d0`）

```c
// -pointForGestureState:point:translation:
if (state == 3 /* ended */) point = [self pointIfPlacedCarefully:point];
return [self touchAlignedPointForPoint:point translation:translation];
```

- **吸附**：`isPlacedCarefully` = `historyCovers(0.2 s) && distanceCoveredInInterval(0.2) < 15 pt`；成立时 `p += (lastPoint − weightedPoint)`，即手指稳定后对齐到真实采样点。
- **触摸对齐**：仅在偏好 `YukonMagnifiersDisabled` 打开、且非间接指针触控时生效（即“关掉放大镜后”的光标模式）。常量：换行阈值 `39.0`（`lineBreakProgress = max(dy/39, 0)`）、回退复位 `−2.0`、指尖投影补偿 `8.0`、容器纵向内缩 `1.0`。

### 4.2 旧的 150 ms 迟滞（`UITextMagnifierTimeWeightedPoint`，`0x1c4c2fd90`）

16 槽环形缓冲 `{x, y, t}`：`addPoint:` 覆盖写、`clearHistory` 填 `t = −1`。`weightedPoint`（`0x1c4c2fde0`）从最新往回扫，**返回第一条 `now − t > 0.15` 的采样点**——即“150 ms 前的位置”。

26 的 glass 路径**不再**用它做透镜位置（透镜位置就是 `modelPosition`），它只服务 §4.1 的吸附判定与距离统计。这条 150 ms 是 13～25 那代透镜拖尾的主要原因，容易被误当成现行为。

### 4.3 开 session（`0x1c4ef471c`）

```c
CGRect wf = selectionWidget.frame;
if (fabs(touch.x - wf.origin.x) < 10.0 && !CGRectIsNull(wf)) {   // 比的是 origin.x，不是 midX
    touch.x = CGRectGetMidX(wf);
    touch.y = CGRectGetMidY(wf);
}
loupe.bounds        = (0, 0, preferredSize);            // 115×85
loupe.modelPosition = [containerSpace convertPoint:touch fromCoordinateSpace:interactionView];
loupe.center        = [loupe constrainedModelPositionForPosition:loupe.modelPosition];
loupe.tintColor     = insertionPointColor ?: tintColor;
[loupe setVisible:NO  animated:NO  completion:nil];
[loupe setVisible:YES animated:YES completion:nil];
```

容器 window 取 `keyboardSceneDelegate.containerWindow`；若交互视图本就在 `remoteKeyboardWindow` 里则直接用。几个附带条件：

- selection widget 需实现 `_UITextSelectionWidgetAnimating`，否则当无 widget 处理（不吸 center、不开 #1 小点）。
- widget 在容器坐标下的矩形高度大于透镜高度（`85`）时直接 `invalidate`，不弹透镜。
- 透镜 window 与取源 window 不同时，给 layer 设一个 `sublayerTransform` 平移（取源 window `bounds.origin` 转换到目标 window），保证 portal 取源坐对齐。
- 创建完由手势行为回填 `session.gestureTuning`，后面的 `dismissalProgress` 就是从这里来的。

### 4.4 每次手势变化（`0x1c4ef4d44`）

```c
if (trackingCaret && !CGRectIsNull(caretRect)) y = CGRectGetMidY(caretRect);

// 光标 ↔ selection widget 互换时整体重播
if (widget != loupe.animatableSelectionWidget) {
    loupe.animatableSelectionWidget = widget;
    [loupe setVisible:NO  animated:NO  completion:nil];
    [loupe setVisible:YES animated:YES completion:nil];
}

loupe.dismissalProgress = gestureTuning.lineBreakProgress;
if (loupe.dismissalProgress >= 0.85 && loupe.visible)
    [loupe setVisible:NO animated:YES completion:nil];

[loupe setModelPosition:{x, y}];        // → displayLink.unpause
```

### 4.5 水平夹紧（`_LoupeConstrainPointHorizontally`，`0x1c4293200`）

```c
// 调用点: (x, y, containerBounds, loupeW=self.bounds.width, loupeH, inset=8.0)
CGFloat clampX(CGFloat x, CGRect box, CGFloat w, CGFloat inset) {
    CGFloat maxW = CGRectInset(box, inset, 0).size.width;
    CGFloat r    = w * 0.5;
    if (x + r > maxW) return maxW - r;
    if (x - r < inset) return inset + r;
    return x;
}
```

只夹 x（横排时透镜比手指宽）；`inset = 8.0` 由调用点硬编码（`STR X8, #0x4020000000000000`）。纵向不夹，靠 §7 的 `needsHideLoupe:` 语义与状态栏避让兜底。

---

## 5. 跟手算法（`_displayLinkFired:`，`0x1c4108e34`）

```c
CGPoint target = [self constrainedModelPositionForPosition:_modelPosition];
CGPoint cur    = self.center;

if (hypot(_modelPosition.x - cur.x, _modelPosition.y - cur.y) < FLT_EPSILON)
    _displayLink.paused = YES;                       // 1.1920929e-7

CGPoint next = { target.x * 0.3 + cur.x * 0.7,
                 target.y * 0.3 + cur.y * 0.7 };

if (_visible) [self _updatePortalViewTransformForPosition:next zoomScale:1.25];

[UIView _animateUsingSpringBehavior:
        [UIViewSpringAnimationBehavior behaviorWithDampingRatio:0.7 response:0.2]
                              tracking:NO
                            animations:^{ self.center = next; }
                            completion:nil];
```

| 要点 | 说明 |
| --- | --- |
| 一阶低通 | 60 Hz 下固定 `0.3/0.7`，等效时间常数 `τ = Δt / −ln(0.7) ≈ 46.8 ms`；95% 收敛 ≈ `3τ ≈ 140 ms`。连续形式 `ẋ = (target − x)/τ` |
| spring | `response 0.2 s`、`dampingRatio 0.7`、`tracking:NO`。单位质量：`k = (2π/0.2)² ≈ 987`，`c = 2ζ√k ≈ 43.9` |
| 暂停条件 | 用的是 `|_modelPosition − center|`（不是 target），阈值就是 `FLT_EPSILON`；任何新的 `setModelPosition` 会 `setPaused:NO` 重新启动 |
| 帧源 | `CADisplayLink`，`preferredFramesPerSecond = 60`，加在主 run loop 默认 mode；`removeFromSuperview` 时 `invalidate` |
| 顺序 | 先低通、再接 spring；两者叠加后视觉滞后约 1～2 帧，并带约 140 ms 的追赶拖尾 |

这就是“跟手但不粘手”的全部机制。没有物理、没有 velocity 预测、没有触点插值。

---

## 6. 开合动画

`setVisible:animated:completion:`（`0x1c410801c`）→ `_updateOpenLoupeAnimation:`（`0x1c4108134`）：

| 阶段 | 驱动 | 变化 |
| --- | --- | --- |
| 预备 | 非动画 | `_updatePortalViewTransform… zoomScale = loupeZoomOutScale(0.125)`；`layoutIfNeeded` |
| 起始布局 | `_animationState = 0` | 尺寸/圆角取 `originShape`（光标 rect 或 selection widget 的 dot）、`borderWidth 42.5`、`alpha 0`（完全不可见） |
| #1 | spring `damping 1.0 / response 0.4` | `_animationState.b0 = 1` → alpha `0→1`、`borderWidth 42.5→2`、圆角 `→ min(originSize)/2`，尺寸仍是光标大小 |
| #2 | spring `damping 0.8 / response 0.5` | `_animationState.b1 = 1` → 尺寸长到 `preferredSize`、圆角 `→ 42.5`、中心加 `±75`（横排 `(0,−75)`、竖排 `(+75,0)`） |

即“从光标处冒出一个点 → 上浮并长成透镜”。`_UIGroupCompletion` 把两次 spring 合成一次 completion。

关闭 `_updateCloseLoupeAnimation:`（`0x1c41083c0`）：

| 项 | 值 |
| --- | --- |
| spring | `damping 1.0 / response 0.23`，`tracking:YES` |
| 布局 | `_resetAnimationState()` → 回到 `originShape` 小环 + `alpha 0` |
| portal | `zoomScale = 0.125`（`FMOV D2, #0.125`，常量内联） |
| 帧率 | `_modifyAnimationsWithPreferredFrameRateRange(min 80, max 120, preferred 120, reason 1048635)` |
| 重定向 | `_closeLoupeAnimationRunning` 置位后，后续每次 `setModelPosition:` 都会再调一次关闭动画——**收起过程中手指还在动，spring 就地重定向**，不会跳回起点 |

无动画分支（`animated:NO`）：显示时 `_animationState = 1` 直接置位；隐藏时 `_resetAnimationState()`，由 session 的 `_invalidateAnimated:0` 负责 `removeFromSuperview`。

---

## 7. 几何与外观

横排文字的静止几何（pt）：

```text
                    ┌─────────────────────┐   ← 115 × 85，圆角 42.5
                    │  magnified content   │      portal scale 1.25
                    │      scale 1.25      │      （以手指点为不动点）
                    └─────────────────────┘
                            ▲  75
                            │
  H e l l o   w o r l d  ── │ ────────────   ← 正文
                            ●  finger (model position)
                            │
                      caret rect  ← originShape（开 #1 从这里长出来）

横排: size 115×85 , translate(0,−75) , radius 42.5
竖排: size  85×115 , translate(+75,0) , 圆角同为 min(w,h)/2
```

| 属性 | 值 |
| --- | --- |
| `preferredSize` | 横排 `115×85`；竖排 `85×115`（`0x1c4107ddc`） |
| 相对手指位移 | 横排 `(0, −75)`；竖排 `(+75, 0)`（`0x1c41089d4`） |
| 圆角 | `min(w,h)/2 = 42.5`，`kCACornerCurveContinuous` |
| 收起判定 `needsHideLoupe:` | `y < 117.5`（= `75 + 42.5`；26 调用图中已无 UIKitCore 调用者） |
| `loupeZoomScale` | `1.25`，可被 UserDefaults `_UITextMagnifiedLoupeViewZoomScale` 覆盖（`0x1c4109040`） |
| `loupeZoomOutScale` | `0.125`，`_UITextMagnifiedLoupeViewZoomOutScale`（`0x1c41090e4`） |
| 水平内缩 | `8 pt` |
| orientation 来源 | `-[UITextInteractionAssistant loupeOrientation]`：`isDisplayingVerticalSelection ? 2 : 1` |

`configureHighlightAndShadow`（`0x1c410762c`）每次 layout 重算玻璃质感：

| 层 | 参数 |
| --- | --- |
| 外阴影层 | `shadowOpacity 0.065`（`0x3D851EB8`）、`offset (0, 3.6667)`、`radius 6.6667`、`shadowPathIsBounds = YES`、`invertsShadow = YES` |
| 描边环 | 黑色 `borderWidth 16.2392`、容器 `alpha 0.08`、`gaussianBlur(inputRadius 3.3333)`、`transform scale(1.48, 1.6793)`、`position (w/2, h/2 − 8)` |
| 白色高光 | `backgroundColor white` + CA 合成过滤，以描边环为 `mask` |
| 容器层 | `shadowOpacity 0.00496`、`radius 6.6667`、`offset (0, 11)`、`punchoutShadow = YES` |
| 玻璃体 | 子视图挂 `_UIFlexInteraction`（`preferredActivationMode 3`、`preferredFlexSources 2`、`preferredVariant 3`，详见 §8），并调用 Swift 侧 `_UILiquidLensView._applyClearGlassBackgroundToSubview(_:)` |

---

## 8. 液态形变：拖动时的“变矮变长”

§5 只管**位置**。拖动时透镜会像液体一样被拉长/压扁，这是**另一条完全独立的链路**——`_UIFlexInteraction`。它不在 `_UITextMagnifiedGlassLoupeView` 自己的代码里，而是挂在它的 `_containerView` 上。

### 8.1 装配方式（`0x1c4107264`）

```objc
_UIFlexInteraction *flex = [NSClassFromString(@"_UIFlexInteraction") new];
flex.preferredActivationMode = 3;   // 激活时机（枚举值，具体分支未展开）
flex.preferredFlexSources    = 2;   // 形变来源 = 加速度分支（走上文 §8.3）
flex.preferredVariant        = 3;   // → _UIFlexInteractionSpec.loupe
[containerView addInteraction:flex];
```

`preferredVariant` → spec 的映射（`-[_UIFlexInteraction _spec]`，`0x1c507bc08`）：

| variant | spec |
| ---: | --- |
| 0 | small / loupe 按视图尺寸插值 |
| 1 | `small` |
| 2 | `large` |
| **3** | **`loupe`** ← 放大镜走这里 |
| 4 | smallLoupe ↔ loupe 按尺寸插值 |
| 5 | `menu`（改走 `updateWithInteractionPulse`） |

### 8.2 loupe 形变参数（`_UIFlexInteractionLoupeVariantSpec` init，`0x1c5159af0`）

| 字段 | 值 | 含义 |
| --- | ---: | --- |
| `associatedDimension` | `70` | 规格参考尺寸（仅 variant 0/4 插值用） |
| `movementMinScale` | `0.75` | 压缩下限 |
| `movementMaxScale` | `1.15` | 拉伸上限 |
| `movementScalePts` | `100` | 绝对变形量（小视图时按 `1 ± pts/dim` 收紧） |
| `movementNormalizationFactor` | `2500` | 加速度归一化分母（pt/s²） |
| `liftScalePoints` | `0` | 抬升附加缩放（loupe 无） |
| `scaleDistanceThreshold` | `0` | 仅 flexSources=1（位移分支）用，loupe 不走 |
| `scaleSpring` | `dampingRatio 1.0`、`response 0.5`；`trackingDampingRatio 0.9`、`trackingResponse 0.5` | 形变落帧弹簧 |

对照 `SmallLoupe`（`0x1c5159cb8`，用于小尺寸插值端）：`associatedDimension 37`、`min 0.9`、`max 1.1`、`movementScalePts 10`、`normalization 2000`、`scaleDistanceThreshold 6000`、`spring 0.56 / 0.444 / retargetImpulse 0.032`。

### 8.3 每帧公式（`-[_UIFlexInteraction update]`，`0x1c507c804`）

只在 `animationContext.isTracking == 1` 且 `bounds` 非空时计算；否则输出恒等值 `(1.0, 1.0, 0, 0)`。

```swift
let bounds  = view.bounds                             // 每帧重读：开场阶段只有 ≈6×26
let accel   = velocityIntegrator.acceleration         // pt/s²，_UIVelocityIntegrator 最新采样
let n       = (x: accel.x / 2500, y: accel.y / 2500)  // movementNormalizationFactor

// 单轴上下界：既受百分比限制，也受 ±100 pt 绝对量限制
func range(_ d: CGFloat) -> (lo: CGFloat, hi: CGFloat) {
    (max(0.75, (d - 100) / d),      // movementMinScale / movementScalePts
     min(1.15, (d + 100) / d))      // movementMaxScale
}

// 单轴软钳位（0x1c553f980）：区间内原样，界外 tanh 过冲，margin=(hi-lo)/3，初始斜率 0.55
func softClamp(_ v: CGFloat, _ lo: CGFloat, _ hi: CGFloat) -> CGFloat {
    let margin = (hi - lo) / 3
    if v > hi { return hi + margin * tanh((v - hi) / margin * 0.55) }
    if v < lo { let floor = lo - margin
                return floor + margin * (tanh((v - floor) / margin * 0.55 - 0.55) + 1) }
    return v
}

let rw = range(bounds.width), rh = range(bounds.height)

var sx = 1.0, sy = 1.0, dx = 0.0, dy = 0.0
if n.x != 0 {
    let t = softClamp(lerp(n.x, 1.0, rw.hi), rw.lo, rw.hi)   // 运动轴：拉长
    sx += t - 1.0
    let u = softClamp(lerp(n.x, 1.0, rh.lo), rh.lo, rh.hi)   // 垂直轴：压短
    sy += u - 1.0
    dx  = (1 - t) * 0.5 * bounds.width  * sign(velocity.x)
}
if n.y != 0 {
    let t = softClamp(lerp(n.y, 1.0, rh.hi), rh.lo, rh.hi)
    sy += t - 1.0
    dy  = (1 - t) * 0.5 * bounds.height * sign(velocity.y)
}
```

`lerp(t, from, to) = (1−t)·from + t·to`（`__UILerp`，`0x1c4128320`）。要点：

- 形变源是**加速度**而不是位移。手指匀速拖动 → 加速度≈0 → 形变≈0；只在**加速/减速瞬间**看到明显的拉伸，松手后弹簧自动回 `1.0`。
- 插值是**带符号**的：`n.x > 0` 时 `sx` 拉长、`sy` 压短，`n.x < 0` 时反过来。所以形体是镜像的，只有 `drift` 钉住的那条边跟着方向翻转。
- 但每个插值结果都要过 `softClamp`（`0x1c553f980`，三处调用点 `0x1c507ce34` / `0x1c507cee4` / `0x1c507cf2c`）。三者的渐近值是 `0.617` 与 `1.283`：**不管多快地甩，镜体永远落在 ±30% 内**。所以在 `115×85` 的胶囊上，宽度 ±15% 基本看不出来、高度 −38% 却一眼就是“扁”，左右两个方向都被压成了同一个“变扁”包络。
- `bounds()` 与 `drift` 都读**当帧** `bounds`：开场阶段身体还只有光标大小，`drift` 只有 ≈0.4 pt；用静止尺寸 `115` 去算会把它放大 ~19 倍，看起来像小镜子刚出现就被甩出去。
- `drift` 是“前缘锚定”补偿：`(1−t)·0.5·dim·sign(v)`，被压短的那一侧向内收，整体看起来像液体被拖着走，而不是整块平移。

### 8.4 输出与应用

```text
_UIFlexInteraction.update
  ├─ delegate != nil → [delegate flexInteraction:didIssueUpdate:]
  │                     _UIFlexInteractionUpdate{ scale: CGSize(sx, sy), translation: (dx, dy), isActive }
  └─ delegate == nil → 写自身可动画属性 scaleX / scaleY / driftX / driftY（UIView animatable property）
```

放大镜这个实例没有 `setDelegate:`，所以 `delegate == nil`，走第二路；宿主视图（`_containerView`）的 `transform` 由这些属性合成（内部使用 `valueWithCATransform3D:` + `"transform"` keypath 的 animatable property，见 `0x1c5079058`）。落帧统一走：

```objc
[UIView _animateUsingSpringBehavior:spec.scaleSpring
                           tracking:isTracking
                         animations:^{ /* 写入 sx, sy, dx, dy */ }
                         completion:nil];
```

即 **`_containerView` 被非均匀缩放 → 里面的 portal 画面、圆角、阴影、玻璃一起被拉**，这就是“液体”观感的来源。玻璃本体是 `_UILiquidLensView`：内部由 `SDFView` / `SDFElementView`（signed distance field）+ `BackdropView` + `DestOutView` + `ClearGlassView` 渲染，另有 `liftProgress` / `liftPortal` / `warpsContentBelow` / `unliftDelayTimer` 处理“抬起—回落”（`liftSpring` / `unLiftSpring`）。

### 8.5 总开关

`_UIFlexInteractionSettings`（init `0x1c5158774`）：`liftEnabled = 1`、`flexEnabled = 1`、`glassFlexibleByDefault = 0`、`ignoreIdiomCheck = 0`，另有 `interactionSpec` 与 `liquidLensSpec`。两个开关都为 `YES` 才会走到 §8.3；`traitCollection.userInterfaceIdiom == 5` 时改走按触点类型分支的路径。

---

## 9. Portal 放大数学

`_updatePortalViewTransformForPosition:zoomScale:`（`0x1c4108d90`）：

```c
CGFloat s = zoomScale;                 // 可见 1.25；收起 0.125
CATransform3D t = CATransform3DIdentity;
t.m11 = t.m22 = s;
t.m41 = position.x * (1.0 - s);        // 横排 = -0.25·x
t.m42 = position.y * (1.0 - s);        //        = -0.25·y
portalView.layer.sublayerTransform = t;
```

等价于 `T(p)·S(s)·T(−p)`：以手指点为不动点缩放，所以透镜里永远是“手指下方那一段文字”，而不是画面整体平移。收起时 `s = 0.125`，视觉上是画面缩回文字里。

`_contentOffsetPortalView` 只做窗口 live 取源：它在 `didMoveToSuperview` 里被 `insertSubview:belowSubview:` 插到透镜下方，本身不可见。

---

## 10. 联动与例外

| 场景 | 行为 |
| --- | --- |
| 换行/超阈值 | `lineBreakProgress ≥ 0.85` → `setVisible:NO animated:YES`（透镜淡出，拖动继续） |
| 状态栏避让 | 透镜 frame 与 `_statusBarFrameIgnoringVisibility` 相交 → `_setOverridingStatusBarHidden:YES`；参数 `UIStatusBarHideAnimationParameters(curve 5 = easeOut, duration 0.2)`（`0x1c4293284`） |
| 容器 window | 取 `keyboardSceneDelegate.containerWindow`；`remoteKeyboardWindow` 场景直接用它；取不到则不显示（`Could not find window to attach loupe view.`） |
| 光标配件可见 | `_allowsForShortDelay = NO` → 退回 0.5 s 长按 |
| selection widget 切换 | session 检测到 widget 变化 → `setVisible:NO`+`setVisible:YES animated:` 整体重播（配合 `originShape` 完成从 dot 到透镜的 morph） |
| 触控笔/指针 | `shouldDisplayLoupeSessionForTouchType:` 仅 `touchType == 0`（直接触摸）为真 |
| 抬手指 | `pointIfPlacedCarefully`（`200 ms` 内位移 `< 15 pt`）吸附到真实采样点；抬点距光标 rect `< 40 pt` 时 `showSelectionCommandsAfterDelay:0.2` |
| 手柄拖拽 | `UITextRangeAdjustmentInteraction` 持同一个 `_loupeSession`；失效回调 `_loupeSessionDidInvalidate:` |

---

## 11. 复刻配方（Glasmio / Compose）

```kotlin
// 1. 手指点水平夹紧（只夹 x）
fun clampX(p: Offset, box: Rect, loupeW: Float, inset: Float = 8f): Offset {
    val r = loupeW / 2f
    return p.copy(x = p.x.coerceIn(inset + r, box.right - inset - r))
}

// 2a. 逐帧复刻：60 Hz 一阶低通（iOS 的拖尾就来自这里）
LaunchedEffect(modelPosition) {
    while (isActive) {
        withFrameNanos { }
        val target = clampX(modelPosition, container, loupeW)
        if ((modelPosition - center).getDistance() <= 1.19e-7f) break   // 静止即停
        center += (target - center) * 0.3f
    }
}

// 2b. 惯用写法：把低通 + spring 合成一个 spring，stiffness = (2π/0.2)² ≈ 987
val follow = spring<Offset>(dampingRatio = 0.7f, stiffness = 987f)
LaunchedEffect(modelPosition) { anim.animateTo(clampX(modelPosition, container, loupeW), follow) }

// 3. 开：0.4 s/阻尼 1.0 长成透镜，再 0.5 s/阻尼 0.8 上浮 75 dp
//    关：0.23 s/阻尼 1.0，同时把内容缩放收到 0.125

// 4. 液态形变：加速度 → 非均匀 scale（§8）
//    response 0.5 s → stiffness = (2π/0.5)² ≈ 158；tracking 时改阻尼 0.9
val deform = spring<Float>(dampingRatio = 1.0f, stiffness = 158f)
private fun lerp(t: Float, from: Float, to: Float) = (1f - t) * from + t * to   // = __UILerp
private fun sign(v: Float) = if (v < 0f) -1f else 1f

fun flex(accel: Offset, w: Float, h: Float, velocity: Offset, bodyScale: Offset = Offset(1f, 1f)): Pair<Offset, Offset> {
    val liveW = (w * bodyScale.x).coerceAtLeast(1f)     // UIKit 读当帧 bounds
    val liveH = (h * bodyScale.y).coerceAtLeast(1f)
    val n = accel / 2500f
    fun lo(d: Float) = max(0.75f, (d - 100f) / d)
    fun hi(d: Float) = min(1.15f, (d + 100f) / d)
    fun soft(v: Float, a: Float, b: Float): Float {      // 0x1c553f980
        val margin = (b - a) / 3f
        return when {
            v > b -> b + margin * tanh((v - b) / margin * 0.55f)
            v < a -> (a - margin) + margin * (tanh((v - (a - margin)) / margin * 0.55f - 0.55f) + 1f)
            else -> v
        }
    }
    var sx = 1f; var sy = 1f; var dx = 0f; var dy = 0f
    if (n.x != 0f) {
        val t = soft(lerp(n.x, 1f, hi(liveW)), lo(liveW), hi(liveW)); sx += t - 1f   // 沿运动方向拉长
        val u = soft(lerp(n.x, 1f, lo(liveH)), lo(liveH), hi(liveH)); sy += u - 1f   // 垂直方向压短
        dx = (1f - t) * 0.5f * liveW * sign(velocity.x)                              // 前缘锚定
    }
    if (n.y != 0f) {
        val t = soft(lerp(n.y, 1f, hi(liveH)), lo(liveH), hi(liveH)); sy += t - 1f
        dy = (1f - t) * 0.5f * liveH * sign(velocity.y)
    }
    return Offset(sx, sy) to Offset(dx, dy)
}
// 只在“加速/减速瞬间”有形变；匀速拖动形变为 0，松手自动回到 1.0
// 软夹紧把双向的形变都压在 0.617–1.283，所以左右拖动看到的是同一个“变扁”包络
```

> 2a 与 2b 不等价：iOS 是“先低通、再 spring”，2b 只保留 spring。要还原那 47 ms 拖尾用 2a。

### 11.1 Glasmio 落地映射

本仓库采用 2a，并把上面的常量收在 `glasmio-ui` 的 `IosLoupeMotion.kt` 里：

| 配方 | Glasmio 实现 |
| --- | --- |
| §7 几何（115×85 / 抬升 75 / 内缩 8） | `GlasmioMagnifier(lensSize, verticalOffset, horizontalInset)` 默认值 |
| §5 跟手（低通 + spring） | `GlasmioLoupeFollowState` / `rememberGlasmioLoupeFollow`，按真实帧间隔重算低通系数，静止即停 |
| §6 开合 | `GlasmioMagnifier` 内部两段 `Animatable`；关闭同时把内容缩到 `0.125×` |
| §8 液态形变 | `rememberLoupeVelocitySample` + `loupeFlex`（含 `loupeSoftClamp` 与当帧尺寸 `bodyScale`），`scaleX/scaleY` + 前缘 `drift` 落在 `graphicsLayer` |
| §10 换行收起 | `GlasmioMagnifier(dismissalProgress)`，编辑器按 39 pt 距离换算进度 |
| 全部时限参数 | `GlasmioLoupeMotion`（response → stiffness 走 `loupeSpringStiffness`） |

未照搬的部分：纵向不夹紧，`needsHideLoupe:` 改为视口翻转与状态栏/IME 内缩避让；`_UIVelocityIntegrator` 的平滑常数未闭合，用 56 ms 最小二乘速度 + 56 ms 差分 + 100 ms 指数平滑近似。

CSS 近似：

```css
.loupe {
  width: 115px; height: 85px; border-radius: 42.5px;
  /* 位置层（§5）与形变层（§8）分层做，互不影响 */
  transform: translate(var(--drift-x), calc(-75px + var(--drift-y))) scale(var(--sx), var(--sy));
  transition:
    transform .5s cubic-bezier(.32,.72,0,1),     /* ≈ spring(damping .8, response .5) */
    opacity   .4s cubic-bezier(.25,.8,.25,1);
}
.loupe__content {
  transform-origin: var(--x) var(--y);          /* JS 每帧写入手指点 */
  transform: scale(1.25);                       /* T(p)·S(1.25)·T(-p) */
}
```

> 形变与位移叠在同一个 `transform` 上时，注意顺序：iOS 是外层 transform 做非均匀 scale，内容自己的放大是另一层 `sublayerTransform`，两者不互相影响形状以外的因子。

调参经验：

- 只调“跟手黏度” → 动低通系数：`0.25` 更黏（拖尾长），`0.4` 更硬（贴身）。
- 只调“落点回弹” → 动 `dampingRatio 0.6～0.75`；`response 0.15～0.25` 之间观感差异很小。
- 不要给 `center` 同时开低通和 duration > 0.25 s 的 spring，会出现两段拖尾。
- 想要“液体”更明显 → 拉大 `movementMaxScale`（`1.15 → 1.25`）或降低 `movementNormalizationFactor`（`2500 → 1500`）；反之把 `max/min` 改成 `1.05/0.95` 就是极简版。

---

## 12. 参数速查表

| 名称 | 值 | 位置 |
| --- | --- | --- |
| 长按基础延迟 | `0.5 s` | `0x1c4bf4110` |
| 近光标短延迟 | `0.07 s`（WK 兼容分支 `0.25 s`） | 同上 |
| 指针触控延迟 | `0.0 s` | 同上 |
| 近光标判定 | `< 40 pt` | `0x1c4bf3e08` |
| 长按判定 | `> 0.5 s` | `0x1c4bf40cc` |
| 允许抖动 | `8 pt` | `TextRefinement_AllowableMovement` |
| 稳定判定（吸附） | `200 ms / 15 pt` | `0x1c4c300fc` |
| 历史窗口 | `150 ms`，16 槽（仅吸附用） | `0x1c4c2fde0` |
| 换行阈值 | `39 pt`，进度 `0..1` | `0x1c429eb68` |
| 收起进度 | `0.85` | `0x1c4ef4d44` |
| 开 session 吸附 | `|Δx| < 10 pt` 贴 widget 中心 | `0x1c4ef471c` |
| 水平内缩 | `8 pt` | `0x1c4293200` |
| 跟手低通 | `next = 0.3·target + 0.7·current`（τ ≈ 47 ms） | `0x1c4108e34` |
| 跟手 spring | `damping 0.7 / response 0.2` | 同上 |
| display link | `60 fps`；暂停阈值 `1.19e-7` | `0x1c4107264` |
| 开 #1 | `damping 1.0 / response 0.4` | `0x1c4108134` |
| 开 #2 | `damping 0.8 / response 0.5` | 同上 |
| 合 | `damping 1.0 / response 0.23`，`tracking:YES`，`80–120 fps @120` | `0x1c41083c0` |
| 透镜尺寸 | `115×85` / `85×115` | `0x1c4107ddc` |
| 抬升位移 | `75 pt` | `0x1c41089d4` |
| 放大倍率 | 可见 `1.25` / 收起 `0.125` | `0x1c4109040`、`0x1c41090e4` |
| 状态栏动画 | `curve 5`，`0.2 s` | `0x1c4293284` |
| 收起阈值（纵向） | `y < 117.5` | `0x1c410902c` |
| 形变 variant | `3`（= loupe spec） | `0x1c4107264` |
| 形变来源位 | `preferredFlexSources = 2`（加速度分支） | 同上 |
| 形变上限 / 下限 | `1.15 / 0.75` | `0x1c5159af0` |
| 加速度归一化 | `2500 pt/s²` | 同上 |
| 绝对变形量 | `100 pt`（小尺寸才生效） | 同上 |
| 形变 spring | `damping 1.0 / response 0.5`；tracking `0.9 / 0.5` | 同上 |
| 形变软钳位 | `tanh` 三段，`margin=(hi−lo)/3`，斜率 `0.55`，渐近 `0.617 / 1.283` | `0x1c553f980` |
| 前缘 drift | `(1−scale)·0.5·dim·sign(v)`，`dim` 取**当帧** `bounds` | `0x1c507c804` |

---

## 13. 尚未闭合

- **开 #1 阶段的小点尺寸**：`_animationState.b0` 分支尺寸取 `originShape`（光标 rect 或 selection widget 的 dot），其具体值由 `_UITextSelectionLollipopView._dotSize` / `UIStandardTextCursorView.originShape` 提供，未逐机型实测。
- **`compositingFilter` 的具体 CA 过滤名**：常量落在 IDA 未映射的段（`0x221A9B6C0`、`0x221A9B788`），只能确认是 CA 合成过滤，字符串未解析。
- **`needsHideLoupe:` 的调用者**：`UIKitCore` 内无 xref，可能由其它 framework 调用，或已成废弃路径。
- **grayscale / 非 Liquid Glass 环境**：`_UIFlexInteraction` 在低端设备或减弱动效下的降级行为未验证。
- **形变到渲染的最后一公里**：`scaleX/scaleY/driftX/driftY` 合成到 `transform` 的那段闭包（`0x1c5079058` 附近）只确认了 keypath = `"transform"` + `CATransform3D`，具体矩阵顺序未逐条核对；`_UILiquidLensView` 的 SDF 是否另有一套随形变变化的半径/圆度也未展开。
- **`velocityIntegrator.acceleration` 的平滑窗口**：`_UIVelocityIntegrator` 为环形采样（步长 144 B、外加 `accelerationFilter`），具体时间常数未跟到底，所以“多快才拉满 1.15”只能按 `2500 pt/s²` 粗略估算。
- **`_UIFlexInteractionSettings` 的外部覆盖**：设置域（`+settingsDomain`）是否可通过内部设置/偏好改 `flexEnabled` / `liftEnabled` 未验证。
- **采样锚点 `LoupeSampleAnchorFraction = 0.78`**：二进制里没有这个常数。原生用 `_contentOffsetPortalContainerView` 的 `translate(±75)` 抵消镜体抬升，且 zoom 锚在 `self.center`；按这条链路推算，被编辑那行应落在镜体上部 `≈0.3`。0.78 是经验拟合，需要一张真机截图定案。
- **纵向文字变体**：`85×115` + `translate(+75,0)` 的竖向分支（`0x1c4107ddc` / `0x1c41089d4`）尚未在实现里提供。
