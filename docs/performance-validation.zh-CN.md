# Glasmio PR #7 性能与视觉验收

更新日期：2026-11-12。

## 验收结论

**所测 x86_64 原生菜单场 CPU 阶段超过 2×；Kotlin 轮廓阶段仍未达到 2×，整个 Android 应用的 2× 目标待设备测量。** 独立复跑确认原生阶段提速 **2.56–2.76×**，并确认数值输出与基线逐位一致。首页静态布局有所改善，Clear 材质采样台阶、最终版本交互和硬件画质仍有未完成项。

基线固定为 [`032a889`](https://github.com/NEORUAA/Glasmio/commit/032a88914a582bdcd270b13af7f8566ac9070b01)。原始 PR 验证版本为 `68034fc`；原生实现此后未变。应用及测试 APK 已从 [`4301cbc`](https://github.com/LIghtJUNction/Glasmio/commit/4301cbc2f15a076796bf7ec83030fb27aafc324d) 构建，261 个非报告跟踪文件与该提交逐项匹配。Kotlin 本轮验证覆盖 [`2525c8f`](https://github.com/LIghtJUNction/Glasmio/commit/2525c8f) 的构造函数，测试源码与提交内容已按摘要核对。下文将新复跑、历史记录和待验收项目分别列明。

| 验收项 | 已有证据 | 当前判定 |
| --- | --- | --- |
| x86_64 原生菜单场 ≥2× | 新复跑四场景 2.56–2.76×；历史记录 2.68–3.44× | 通过该阶段指标 |
| Kotlin 轮廓构造 ≥2× | 新复跑 1.30–1.52× | 未达标 |
| 保持原生／轮廓数值质量 | 相同输入、参数和输出格式；扩大语料逐位一致 | 所测语料通过 |
| Android 整体 ≥2× | 缺少 ARM 实机同场景端到端测量 | 待测 |
| 静态首页和大字体布局 | 逐图复核 8 张历史截图；历史 1 项设备回归 | 已列场景部分通过 |
| Clear、动画、硬件 GPU／HDR | Clear 可见台阶，缺少同配置基线与硬件结果 | 待验证 |
| 最终版本 CI 与设备交互 | 新单元测试通过；新增设备测试待执行 | 待完成 |

**当前不建议按“整体性能提升至少 2×、视觉全面通过”的标准合并。** 继续优化 Kotlin 并补齐设备、画质和 CI 验收；如维护者拟按阶段性优化接收，也应明确保留这些开放项。

## 实现与工作负载

原生菜单场把相邻四个位置的距离／法线计算交给 SIMD：ARM64 使用 NEON，x86 使用 SSE2，不足四个位置仍走标量尾部。x86 同时新增向量化卷积；ARM64 基线已经有 NEON 卷积，因此 x86 提速幅度需与 ARM64 实机结果分开报告。

原生路径保留采样间距、Gaussian 核、精确除法和平方根、输出格式以及 `-ffp-contract=off`，并复用工作缓冲。代码审查和相同输入的原始浮点位比较支持“向量指令处理相同采样、保持所测数值输出”的结论；没有发现降低该路径采样质量换取速度的修改。

Kotlin 直接读取 `Path.approximate` 的浮点数组并写入连续边数据，移除顶点装箱、临时边对象和额外遍历。`2525c8f` 进一步用普通循环处理子路径，移除局部函数捕获的六个 `kotlin.jvm.internal.Ref` 对象；边顺序、绕行、闭合、短边处理、包围盒和 fingerprint 保持所测语义。

首页新增真实壁纸与 `RegularMedium` 玻璃预览，目录提供短标题和用途说明，大字体切为单列。导航标签按相邻中心间距限制宽度，并在可见层与采样层使用相同约束；Search 返回 Home 时比较真实选择索引，避免绘制用的钳制索引抑制回调。

阶段提速不能直接转成整帧提速：若该阶段原先占总耗时的比例为 `f`，仅它提速 `s` 倍时，总耗时理论加速比为 `1 / (1 - f + f / s)`。例如 `f = 50%`、`s = 2.7` 时约为 1.46×；这只是解释范围的算例，未测得本应用的 `f`。

**首页新增玻璃预览会改变渲染工作量。** 端到端性能对照需采用完全相同的场景、布局、素材与操作轨迹，或单列新旧首页各自的体验数据。原生微基准未覆盖新增预览的实际开销。

## 独立复跑：原生菜单场

环境：Intel Xeon Platinum 8573C、Linux x86_64、GCC 14.2.0，固定 CPU 8。前后共同使用 `-std=c++17 -O3 -fno-math-errno -ffp-contract=off -DLIQUID_MENU_STANDALONE`。

表内耗时为每轮 32 次调用的平均值，再对全部 45 轮取中位数；越低越好。加速比为基线中位数除以 PR 中位数。

| 合成场景 | 基线 ms ↓ | PR ms ↓ | 加速比 ↑ |
| --- | ---: | ---: | ---: |
| 小菜单 | 0.393 | 0.145 | 2.71× |
| 大菜单 | 1.830 | 0.670 | 2.73× |
| 宽菜单 | 1.301 | 0.472 | 2.76× |
| 单玻璃表面 | 1.510 | 0.589 | 2.56× |

公平性检查覆盖以下条件：

- 三个新进程各测 15 轮，每种实现预热四批，轮次交替执行前后版本，全部样本保留。
- 每轮使用相同的 32 个变化几何场；边界、原点、网格、`step = max(2, smoothing / 4)` 和核权重均按生产公式生成。四组场景裁剪并重新归一化后的核半径均为 11。
- 参数准备、输出分配在双方计时区间之外，已有线程局部缓冲容量复用；完整采样、横纵卷积和最终法线归一化在计时区间内。
- 基线与新版分别编译成外部对象，不启用 LTO；输出检查与真实缓冲避免将无效工作计为优化收益。

原有 **1,536 场／11,941,640 个 Float32 通道**复跑逐位一致。扩大至 **3,584 场／21,187,368 个通道**后仍逐位一致，覆盖独立随机几何、细小／重合／分离形状、零尺寸、亚像素原点和 SIMD 尾部宽度。扩大语料通过 ASan／UBSan；`detect_leaks=0`，未验收泄漏检查。

该测量是暖缓冲下的原生构建耗时，排除 Kotlin 参数准备、JNI 传递、FP16 打包、纹理上传及 GPU 绘制。输入为四条确定性合成轨迹；批次平均值不提供单帧 P95／P99 或 Android FPS。共享主机的调度离群值保留在逐轮 CSV 和各进程结果中。

证据：[协议与命令](../.local-research/pr7-review/native/README.md)、[汇总](../.local-research/pr7-review/native/gcc-host/summary.json)、[全部样本](../.local-research/pr7-review/native/gcc-host/samples.csv)、[sanitizer 结果](../.local-research/pr7-review/native/gcc-sanitizers/summary.json)。

## 独立复跑：Kotlin 轮廓构造

环境：OpenJDK 21.0.13、Kotlin 2.4.21、真实 Compose geometry／util 1.12.1，固定 x86_64 CPU 7。三个新 JVM，每场景每进程 18 轮、每轮 15,000 次调用；各实现预热 120,000 次。基线、原 PR 和本轮修订采用六种均衡排列，使用 volatile 对象保留结果，由 `ThreadMXBean` 测量分配。

表内为全部 54 轮的批次平均耗时中位数；分配量为每次构造的实测字节数，已四舍五入。

| 轮廓 | 基线 μs ↓ | 原 PR μs ↓ | 本轮 μs ↓ | 基线／本轮 ↑ | 基线／原 PR／本轮 B ↓ |
| --- | ---: | ---: | ---: | ---: | ---: |
| 4 边矩形 | 0.2570 | 0.1921 | 0.1689 | 1.52× | 600／296／200 |
| 64 边胶囊 | 3.7450 | 2.8266 | 2.7591 | 1.36× | 7608／1976／1880 |
| 两个分离胶囊，128 边 | 7.1014 | 5.5433 | 5.4825 | 1.30× | 14368／3768／3672 |
| 256 边复杂轮廓 | 14.9847 | 11.1526 | 10.9488 | 1.37× | 31704／7352／7256 |

本轮相对原 PR **每次再减少 96 B 分配**，与字节码移除六个捕获对象的结果一致。较大轮廓的总体耗时改进约 1–2%，部分独立 JVM 的前后差异跨过 1.0，故不将这部分微小变化写成稳定加速承诺。四个 Kotlin 场景均未达到 2×；主机 JVM 的分配和时间需另在 Android ART 上验证。

九项轮廓 JUnit 测试通过；三个 JVM 各自验证 **5,514 组输入**的边数据原始浮点位、bounds 和 fingerprint 与基线一致，涵盖随机子路径、相反绕行、重复／闭合点、正负零和短边。新增三项生产回归覆盖全部短边、有效轮廓中的短边及连续分离移动。

证据：[复跑协议](../.local-research/pr7-review/kotlin/README.md)、[汇总及各 JVM 结果](../.local-research/pr7-review/kotlin/final/summary.json)、[源码与结果验收](../.local-research/pr7-review/kotlin/final/acceptance.json)。原生和 Kotlin 表对应不同阶段，加速比按各自范围解释，不相乘。

## 历史测量：2026-11-11

已重新核对原始归档的源码、摘要和逐轮数据，以下中位数可从归档重算。历史环境与本轮不同，两组结果单独保留；不以跨环境时间差判断本轮代码变快或变慢。

原生历史环境为 AMD EPYC 9V74、NDK Clang 18.0.2、固定 CPU 8，共 15 轮；同样使用生产参数公式和相同编译选项。

| 场景 | 初始网格／间距 | 基线 ms ↓ | 原 PR ms ↓ | 加速比 ↑ |
| --- | --- | ---: | ---: | ---: |
| 小菜单 | 68×44／3 | 0.352 | 0.102 | 3.44× |
| 大菜单 | 106×146／6 | 1.522 | 0.545 | 2.79× |
| 宽菜单 | 150×66／6 | 1.085 | 0.391 | 2.78× |
| 单玻璃表面 | 109×159／4 | 1.294 | 0.482 | 2.68× |

Kotlin 历史环境为 OpenJDK 25.0.5、Kotlin 2.4.21、Compose 1.12.1，三个 JVM 共 33 轮、每轮 6,000 次。

| 轮廓 | 基线 μs ↓ | 原 PR μs ↓ | 加速比 ↑ | 分配减少 |
| --- | ---: | ---: | ---: | ---: |
| 4 边矩形 | 0.353 | 0.230 | 1.53× | 50.7% |
| 64 边胶囊 | 3.567 | 2.352 | 1.52× | 74.0% |
| 分离胶囊，128 边 | 7.780 | 4.241 | 1.83× | 73.8% |
| 复杂轮廓，256 边 | 14.448 | 8.924 | 1.62× | 76.8% |

历史 ARM64 证据为 NDK 编译后在 QEMU 验证 **1,412 场／6,812,212 通道**逐位一致，属于编译／正确性证据，未包含硬件计时。本轮未将模拟执行耗时转为 ARM 手机加速比。

## 构建、CI 与交互回归

本轮使用仓库 **Gradle wrapper 9.6.0／Temurin JDK 21.0.13**，SDK 37.0、NDK 27.1.12297006、CMake 3.22.1。最终应用与测试 APK 对应代码提交 `4301cbc`；261 个非报告跟踪文件与该提交逐项匹配，未提供额外字体输入，使用仓库默认 debug 资源。

| 检查 | 实际结果 | 证据 |
| --- | --- | --- |
| 完整 JVM 单元测试 | 13 个 XML、146 项：143 通过、3 项已有跳过、0 失败／错误；包含九项轮廓测试 | [单元汇总](../.local-research/pr7-review/android/unit-summary.json) |
| 应用与测试 APK | 最终新增预览入口测试后构建成功；应用包含 arm64-v8a、armeabi-v7a、x86、x86_64 四个 ABI | [源码记录](../.local-research/pr7-review/android/final-build-source.json)、[APK 摘要](../.local-research/pr7-review/android/tested-apks.json)、[最终日志](../.local-research/pr7-review/android/final-apk-and-lint.log) |
| 七模块 lint | 0 错误、27 条 Warning、4 条 Hint；最终测试修改后 sample lint 再次成功 | [lint 汇总](../.local-research/pr7-review/android/lint-summary.json) |
| 本地 staging | 六个库的 AAR、POM、模块元数据、源码及文档产物生成成功；未发布至公共 Maven | [产物清单](../.local-research/pr7-review/android/staging-artifacts.json)、[拆分执行记录](../.local-research/pr7-review/android/split-build-results.json) |
| 独立 Maven 消费示例 | 使用 staging 坐标及传递依赖构建新的 Android 应用成功 | [结果](../.local-research/pr7-review/android/consumer-result.json)、[日志](../.local-research/pr7-review/android/consumer.log) |
| 七项定向设备回归 | APK 已编译；本轮模拟器启动未完成，七项均未执行 | 见下文设备范围 |
| 上游 CI | 代码提交 `4301cbc` 的 Build #79 为 `action_required`，0 个 job | [GitHub run](https://github.com/NEORUAA/Glasmio/actions/runs/43193789142)、[状态记录](../.local-research/pr7-review/android/ci-status.json) |

两次组合构建曾发生 Gradle daemon 意外退出：第一次在单元测试完成后，第二次在 preference 文档生成期间。后续以单 worker、独立 Gradle 进程拆分执行完成上述本地检查。失败日志仍保留于 [首次构建](../.local-research/pr7-review/android/build-and-tests.log) 与 [lint／staging 组合构建](../.local-research/pr7-review/android/lint-and-staging.log)；没有将组合调用记为成功，也没有把拆分后的本地成功替代上游 CI 通过。进程退出的根因未得到独立确认。

历史日志记录 Gradle 9.8.2／JDK 25.0.5，初次 140 项通过／3 跳过，后续 UI 52 项通过；这些历史结果与本轮准确 wrapper 的结果分别保存。

工作流配置了 API 33 模拟器上的七项定向设备回归，并配置为上传测试报告、APK 和失败截图／logcat。所选用例包含既有测试、扩展测试和新增测试；配置本身不代表已运行。覆盖首页玻璃预览进入 Materials 并返回、200% 字体 LTR／RTL 标签与真实触摸、Search→Home、紧凑栏展开不改变选择、搜索标题／路由／描述匹配、空白查询恢复、取消重入和 Dynamic Type 路由。

原始归档只记录 **1 项设备测试通过**：`largeTypeNavigationLabelsStaySeparateAndTabsRemainClickable`，使用 `reduceTransparency=true` 验证布局和输入。本轮扩展后的七项均未执行：本地没有 KVM，先前模拟器进程曾以 SIGKILL／137 结束；在所有构建结束后，用新 AVD 独立启动，430.8 秒内 `sys.boot_completed` 仍为空。后续诊断可访问 Package Manager、zygote 和 SurfaceFlinger，诊断时日志显示仍在首次启动的 `UpdatePackagesIfNeeded`／dexopt 阶段。收尾前再次检查仍未启动完成，随后正常关闭模拟器。未将该环境结果记为应用测试失败，也没有新的可验收应用截图。见 [启动记录](../.local-research/pr7-review/android/final-boot-status.json)、[系统诊断](../.local-research/pr7-review/android/final-device-diagnostics.json)；新增测试代码和工作流不计作执行成功。

## 逐图视觉复核

独立查看全部八张归档截图，核验 **15/15** 个归档文件长度与摘要、**8/8** 张 PNG 尺寸与摘要均匹配。截图环境为 Android 13／API 33 x86_64、ANGLE＋SwiftShader；以下结论限于已展示的静态内容。

| 截图 | 观察 | 验收范围 |
| --- | --- | --- |
| `before-home-light.png` | 原始双列目录，长标题和底部内容局部遮挡 | 首页基线 |
| `after-home-light.png` | 真实玻璃预览、短标题和说明层次清楚 | 浅色首页 |
| `after-home-dark.png` | 黑色背景、卡片、文字和边缘可辨 | 深色首页 |
| `after-materials-light.png` | Regular Small／Medium 文字与边缘完整 | 两种材质浅色静态表现 |
| `after-materials-dark.png` | Small／Medium 深色内容可读 | 两种材质深色静态表现 |
| `after-materials-clear-light.png` | Large／Clear 内容排版完整；Clear 白色曲线有采样台阶 | 布局通过，Clear 画质待验 |
| `after-home-font-2-light.png` | 介绍与材质说明换行；导航标签分别省略 | 200% 字体浅色 LTR 首页 |
| `after-catalog-font-2-light.png` | 滚动后 Controls／Navigation 单列卡片完整 | 两张已展示卡片 |

五张常规字号新版图标记为 `45a4909`，两张大字体图标记为 `c949d9f`。Git 核对确认其间仅导航实现／测试变化；首页目录和材质布局源码相同。`c949d9f` 到原报告提交 `68034fc` 只增加文档，之后的 Kotlin 轮廓优化仍需最终 APK 的视觉回归。

基线为 720×1600／density 320，常规新版为 540×1200／density 240，大字体为 360×800／density 160；逻辑宽度均为 360dp。物理分辨率不同，现有图片支持定性布局比较；像素／抗锯齿质量对照需统一设备配置。Clear 缺少同配置旧版 Materials 画面，问题来源待查。

原归档提供 APK 摘要而未包含 APK 字节；源码、构建、安装与截图之间的完整来源记录仍待补齐。归档曾记录软件模拟器 ANR、黑帧和 SystemUI 恢复；这些帧未用于验收。大字体静态图关闭系统动画，底栏遮挡部分视口内容后可通过滚动露出。RTL 现有证据来自布局／触摸夹具，尚无 RTL 原始画面。

证据：[逐图复核](../.local-research/pr7-review/visual/manual-review.md)、[归档核验](../.local-research/pr7-review/visual/evidence-check.json)。当前视觉结果不包含实机动画、全目录逐页交互、硬件 GPU 或 HDR 验收。

## 合并前需要补齐的关键测试

1. **ARM64 同场景性能。** 至少在实际目标手机上固定系统／显示配置、材质参数、分辨率、几何轨迹和编译模式，交替运行基线与新版，保留设备温度、频率、热降频、运行顺序和所有样本。
2. **完整管线计时。** 分别记录 Kotlin、JNI、原生场构建、FP16 打包、上传、GPU 和整帧耗时；使用实际帧事件统计 P50／P95／P99、卡顿和帧预算超限比例，并报告分配／GC。先确认瓶颈占比，再判断整体 2× 是否达成。
3. **相同工作负载与画质。** 固定素材和采样参数，对新旧版的相同 Materials 场景采集同分辨率截图及拖动录屏；覆盖四种材质的深浅色，定位 Clear 台阶，区分软件渲染与硬件 GPU 表现。HDR 使用支持该能力的设备独立验证。
4. **最终 APK 交互。** 执行七项定向设备测试；复查字体 1.0／2.0、LTR／RTL、首页预览入口、搜索取消与返回、紧凑／展开导航及目录可滚动访问。补充深色大字体、横屏／宽屏和触摸动画检查。
5. **准确提交的 CI。** 允许并完成最终 head 的构建、lint、单元测试、发布依赖图及定向 instrumentation；保存源码状态、完整命令、工具链、APK 摘要和设备配置，出现失败后修复并复验受影响项。

## 证据与复现

按仓库约定，原始数据、探针、截图放在忽略的 `.local-research/` 中；本文中的工作区相对链接仅在将证据包按目录结构解压到 checkout 后可用，GitHub 页面不提供这些本地文件。

- [2026-11-12 独立验收证据包](https://drive.google.com/file/d/1b0-duTgpuEORqPwfEUJhFHW9aYOhLOir/view?usp=drivesdk)：本轮完整原始样本、源代码对应关系、复现脚本、构建／CI 状态、视觉复核，以及两份展开后的原始证据；内含文件摘要清单，访问沿用证据文件夹权限。
- [2026-11-11 原始性能证据包](https://drive.google.com/file/d/1VcrP2t2EMsUGCHZeZ4ukAKeeUaCUepkC/view?usp=drivesdk)：源快照、原始数据、复现脚本、ARM64 正确性和历史构建记录。
- [2026-11-11 原始视觉与交互证据包](https://drive.google.com/file/d/1-26gUj2jv-neGaH8bMIv51TuIY_caIcb/view?usp=drivesdk)：八张原图、截图元数据、APK 摘要及历史设备日志。
- [原生历史数据重算](../.local-research/pr7-review/native/archive-audit.json)、[Kotlin 历史数据重算](../.local-research/pr7-review/kotlin/original-audit.json)：记录归档源码与 Git、CSV／JSON 和汇总值之间的对应关系。
- [Glasmio 证据文件夹](https://drive.google.com/drive/folders/1HRaJ2EPZ7r2u6ptjO2iLr6GL5oyKqD7m)：集中保存交付证据。

在 Linux x86_64 checkout 中可按下面命令复跑；CPU 号需选当前允许的核心，并隔离同时运行的构建／模拟器。脚本分别记录编译器、依赖、源码、协议与全部样本。

```sh
python3 .local-research/pr7-review/native/reproduce.py --compiler g++ --cpu 8 --forks 3 --label reproduced
ASAN_OPTIONS=detect_leaks=0 python3 .local-research/pr7-review/native/reproduce.py --compiler g++ --cpu 8 --sanitize --label reproduced-sanitizers
python3 .local-research/pr7-review/kotlin/reproduce.py --stable --cpu 7 --out .local-research/pr7-review/kotlin/reproduced
python3 .local-research/pr7-review/visual/verify_evidence.py
```

Android 构建需 JDK 21、仓库 wrapper 9.6.0、SDK 37.0、NDK 27.1.12297006 和 CMake 3.22.1。CI 的主要构建及本地 Maven 消费验证命令如下；本轮本地为控制资源使用拆分执行，并保留完整日志。

```sh
./gradlew testDebugUnitTest lintDebug :sample:assembleDebug :sample:assembleDebugAndroidTest publishAllPublicationsToStagingRepository --console=plain
python3 scripts/verify_consumer.py
```

七项 API 33 定向测试的完整 selector 保存在 [构建工作流](https://github.com/LIghtJUNction/Glasmio/blob/main/.github/workflows/build.yml) 中。工作流的配置和 APK 的成功编译都不能代替设备实际通过结果。

## 结论与证据自检

| 审查问题 | 回答 |
| --- | --- |
| 改善来自哪里？ | SIMD 同时处理相邻采样点和卷积通道，连续边数据和无捕获循环减少 Kotlin 临时分配；测试直接使用生产源码。 |
| 性能主张能否对应测量？ | 原生 CPU ≥2× 有新旧两组证据；Kotlin 和整机目标分别标为未达标、待测。 |
| 对照是否公平？ | 每组内部固定输入／参数／编译环境，交替或均衡顺序，保留所有样本；不同环境结果分表。 |
| 质量与回归覆盖是否完整？ | 数值语料通过；Clear、硬件输出、最终设备交互和动态表现仍有明确缺口。 |
| 当前是否满足完整验收？ | 尚未满足；合并建议受上面列出的设备、画质及 CI 结果约束。 |
