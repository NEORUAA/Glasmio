#include <algorithm>
#include <cmath>
#include <cstddef>
#include <cstdint>
#include <limits>
#include <unordered_map>
#include <cstring>
#include <vector>
#if defined(__aarch64__)
#include <arm_neon.h>
#endif
#if !defined(LIQUID_MENU_STANDALONE)
#include <jni.h>
#endif

namespace {
constexpr float kExpansion = 1.5286649465560913f;
constexpr float kAdjustment = 0.5286649465560913f;
struct Normal {
    float x, y;
};
struct Sample {
    float distance, nx, ny;
};
inline float saturate(float value) { return std::clamp(value, 0.0f, 1.0f); }

// CASDF's continuous field and its independently ovalized normal, in isotropic coordinates.
struct RoundedField {
    float cx, cy, radius, inverseX, inverseY, halfWidth, halfHeight;
    float expanded, curvatureX, curvatureY, baseRadius;
    bool circular;
    explicit RoundedField(const float *g, bool edges = false) {
        cx = edges ? (g[0] + g[2]) / 2.0f : g[0] + g[2] / 2.0f;
        cy = edges ? (g[1] + g[3]) / 2.0f : g[1] + g[3] / 2.0f;
        const float rx = std::max(g[4], 0.01f), ry = std::max(g[5], 0.01f);
        radius = std::min(rx, ry);
        inverseX = radius / rx;
        inverseY = radius / ry;
        halfWidth = (edges ? g[2] - g[0] : g[2]) / 2.0f * inverseX;
        halfHeight = (edges ? g[3] - g[1] : g[3]) / 2.0f * inverseY;
        expanded = radius * kExpansion;
        curvatureX = saturate(2.8915570351735034f - 1.4457785175867517f * (halfWidth * 2.0f) / expanded);
        curvatureY = saturate(2.8915570351735034f - 1.4457785175867517f * (halfHeight * 2.0f) / expanded);
        baseRadius = radius * (kExpansion - kAdjustment * std::max(curvatureX, curvatureY));
        circular = curvatureX == 1.0f && curvatureY == 1.0f;
    }
    float distance(float x, float y) const {
        const float qx = std::abs((x - cx) * inverseX) - halfWidth;
        const float qy = std::abs((y - cy) * inverseY) - halfHeight;
        const float bx = qx + baseRadius, by = qy + baseRadius;
        const float interior = std::min(std::max(bx, by), 0.0f);
        if (circular) {
            const float dx = std::max(bx, 0.0f), dy = std::max(by, 0.0f);
            return interior + std::sqrt(dx * dx + dy * dy) - radius;
        }
        const float ux = (qx + expanded) / expanded, uy = (qy + expanded) / expanded;
        const float zx = std::max(ux, 0.0f), zy = std::max(uy, 0.0f);
        const float maximum = std::max(zx, zy);
        const float t = maximum > 0.0f ? std::min(zx, zy) / maximum : 0.0f;
        const float length = std::sqrt(zx * zx + zy * zy);
        const float polynomial =
            ((((-0.9260540008544922f * t + 3.1560099124908447f) * t - 3.6412200927734375f) * t +
              1.268030047416687f) *
                 t +
             0.2685309946537018f) *
            t * t;
        const float continuous = length + 1.0f - 1.0f / (1.0f - polynomial * std::min(length, 1.0f));
        const float ax = std::max(kExpansion * ux - kAdjustment, 0.0f);
        const float ay = std::max(kExpansion * uy - kAdjustment, 0.0f);
        const float correction =
            0.3458344340324402f - continuous + 0.6541655659675598f * std::sqrt(ax * ax + ay * ay);
        const float weight = saturate((zx < zy ? 1.0f : -1.0f) * (t - 1.0f) + 0.5f);
        return interior + expanded * (continuous - 1.0f +
                                      correction * (curvatureX + (curvatureY - curvatureX) * weight));
    }
    Normal normal(float x, float y, float normalMix = 0.5f) const {
        const float lx = (x - cx) * inverseX, ly = (y - cy) * inverseY;
        const float qx = std::abs(lx) - halfWidth + baseRadius;
        const float qy = std::abs(ly) - halfHeight + baseRadius;
        float nx = std::max(qx, 0.0f), ny = std::max(qy, 0.0f);
        const float cornerLength = std::sqrt(nx * nx + ny * ny);
        if (cornerLength > 0.000001f) {
            nx /= cornerLength;
            ny /= cornerLength;
        } else if (qx > qy) {
            nx = 1.0f;
            ny = 0.0f;
        } else {
            nx = 0.0f;
            ny = 1.0f;
        }
        if (lx < 0.0f)
            nx = -nx;
        if (ly < 0.0f)
            ny = -ny;
        if (normalMix == 0.0f) {
            // Navigation contour folding uses only the rounded-box normal.
            // Avoid computing an oval normal whose mix weight is exactly zero.
            const float length = std::sqrt(nx * nx + ny * ny);
            if (length <= 0.000001f)
                return {0.0f, 0.0f};
            return {nx / length * inverseX, ny / length * inverseY};
        }
        float ox = lx, oy = ly * halfWidth / std::max(halfHeight, 0.000001f);
        const float ovalLength = std::sqrt(ox * ox + oy * oy);
        if (ovalLength > 0.000001f) {
            ox /= ovalLength;
            oy /= ovalLength;
        } else {
            ox = nx;
            oy = ny;
        }
        if (normalMix == 0.5f) {
            nx += ox;
            ny += oy;
        } else {
            nx += (ox - nx) * normalMix;
            ny += (oy - ny) * normalMix;
        }
        const float length = std::sqrt(nx * nx + ny * ny);
        if (length <= 0.000001f)
            return {0.0f, 0.0f};
        return {nx / length * inverseX, ny / length * inverseY};
    }
};

Sample sample(const RoundedField &incoming, const RoundedField &outgoing, bool hasOutgoing, float smoothing,
              float x, float y) {
    const float a = incoming.distance(x, y);
    const float b = hasOutgoing ? outgoing.distance(x, y) : INFINITY;
    if (!hasOutgoing || std::abs(a - b) >= smoothing) {
        const Normal n = (!hasOutgoing || a <= b) ? incoming.normal(x, y) : outgoing.normal(x, y);
        return {std::min(a, b), n.x, n.y};
    }
    const Normal an = incoming.normal(x, y), bn = outgoing.normal(x, y);
    const float alignment = an.x * bn.x + an.y * bn.y;
    const float radius = smoothing * saturate(0.5f - 0.5f * alignment);
    const float weight =
        radius <= 0.000001f ? (a <= b ? 0.0f : 1.0f) : saturate(0.5f + 0.5f * (a - b) / radius);
    return {a + (b - a) * weight - radius * weight * (1.0f - weight), an.x + (bn.x - an.x) * weight,
            an.y + (bn.y - an.y) * weight};
}
struct Scratch {
    std::vector<float> raw, horizontal;
    std::vector<float> contactEdges, contactColumns;
};
thread_local Scratch scratch;

// Match java.lang.Math's signed-zero ordering, including normal channels at exact edges.
inline float javaMin(float a, float b) {
    if (std::isnan(a)) return a;
    if (std::isnan(b)) return b;
    if (a == 0.0f && b == 0.0f) return std::signbit(a) ? a : b;
    return a < b ? a : b;
}
inline float javaMax(float a, float b) {
    if (std::isnan(a)) return a;
    if (std::isnan(b)) return b;
    if (a == 0.0f && b == 0.0f) return std::signbit(a) ? b : a;
    return a > b ? a : b;
}
inline int gridIndex(double value) {
    if (value >= std::numeric_limits<int>::max()) return std::numeric_limits<int>::max();
    if (value <= std::numeric_limits<int>::min()) return std::numeric_limits<int>::min();
    return static_cast<int>(value);
}
} // namespace

// One call covers the entire field. Scratch capacity survives subsequent frames on this thread.
extern "C" void liquid_menu_sdf_build(const float *geometry, float originX, float originY, float step,
                                      int width, int height, const float *weights, int radius, float *rgba) {
    const RoundedField incoming(geometry), outgoing(geometry + 6);
    const bool hasOutgoing = geometry[13] != 0.0f;
    const float smoothing = std::max(geometry[12], 0.0f);
    const int paddedWidth = width + 2 * radius, paddedHeight = height + 2 * radius;
    const size_t rawSize = static_cast<size_t>(paddedWidth) * paddedHeight * 3;
    const size_t horizontalSize = static_cast<size_t>(width) * paddedHeight * 3;
    if (scratch.raw.size() < rawSize)
        scratch.raw.resize(rawSize);
    if (scratch.horizontal.size() < horizontalSize)
        scratch.horizontal.resize(horizontalSize);
    float *raw = scratch.raw.data();
    float *horizontal = scratch.horizontal.data();
    for (int row = 0; row < paddedHeight; row++) {
        const float y = originY + (row - radius) * step;
        for (int column = 0; column < paddedWidth; column++) {
            const Sample s =
                sample(incoming, outgoing, hasOutgoing, smoothing, originX + (column - radius) * step, y);
            const size_t output = (static_cast<size_t>(row) * paddedWidth + column) * 3;
            raw[output] = s.distance;
            raw[output + 1] = s.nx;
            raw[output + 2] = s.ny;
        }
    }
    for (int row = 0; row < paddedHeight; row++) {
        const float *source = raw + (static_cast<size_t>(row) * paddedWidth + radius) * 3;
        float *output = horizontal + static_cast<size_t>(row) * width * 3;
        int channel = 0;
#if defined(__aarch64__)
        // Four independent channel sums preserve every tap's Float32 operation order.
        // Keep field channels interleaved, including partial four-pixel groups.
        for (; channel + 4 <= width * 3; channel += 4) {
            float32x4_t value = vmulq_n_f32(vld1q_f32(source + channel), weights[0]);
            for (int offset = 1; offset <= radius; offset++) {
                const float32x4_t left = vld1q_f32(source + channel - offset * 3);
                const float32x4_t right = vld1q_f32(source + channel + offset * 3);
                value = vaddq_f32(value, vmulq_n_f32(vaddq_f32(left, right), weights[offset]));
            }
            vst1q_f32(output + channel, value);
        }
#endif
        for (; channel < width * 3; channel++) {
            float value = source[channel] * weights[0];
            for (int offset = 1; offset <= radius; offset++)
                value += (source[channel - offset * 3] + source[channel + offset * 3]) * weights[offset];
            output[channel] = value;
        }
    }
    for (int row = 0; row < height; row++) {
        int column = 0;
#if defined(__aarch64__)
        // Resolve four pixels together with exact square roots and division, not
        // reciprocal estimates. This matches the portable normal at every lane.
        for (; column + 4 <= width; column += 4) {
            const size_t center = (static_cast<size_t>(row + radius) * width + column) * 3;
            const float32x4x3_t values = vld3q_f32(horizontal + center);
            float32x4_t d = vmulq_n_f32(values.val[0], weights[0]);
            float32x4_t nx = vmulq_n_f32(values.val[1], weights[0]);
            float32x4_t ny = vmulq_n_f32(values.val[2], weights[0]);
            for (int offset = 1; offset <= radius; offset++) {
                const float32x4x3_t above = vld3q_f32(horizontal + center - offset * width * 3);
                const float32x4x3_t below = vld3q_f32(horizontal + center + offset * width * 3);
                const float weight = weights[offset];
                d = vaddq_f32(d, vmulq_n_f32(vaddq_f32(above.val[0], below.val[0]), weight));
                nx = vaddq_f32(nx, vmulq_n_f32(vaddq_f32(above.val[1], below.val[1]), weight));
                ny = vaddq_f32(ny, vmulq_n_f32(vaddq_f32(above.val[2], below.val[2]), weight));
            }
            const float32x4_t length = vsqrtq_f32(vaddq_f32(vmulq_f32(nx, nx), vmulq_f32(ny, ny)));
            const uint32x4_t valid = vcgtq_f32(length, vdupq_n_f32(0.000001f));
            const float32x4_t safeLength = vbslq_f32(valid, length, vdupq_n_f32(1.0f));
            const float32x4x4_t result = {{d, vbslq_f32(valid, vdivq_f32(nx, safeLength), vdupq_n_f32(0.0f)),
                vbslq_f32(valid, vdivq_f32(ny, safeLength), vdupq_n_f32(0.0f)), vdupq_n_f32(1.0f)}};
            vst4q_f32(rgba + (static_cast<size_t>(row) * width + column) * 4, result);
        }
#endif
        for (; column < width; column++) {
            const size_t center = (static_cast<size_t>(row + radius) * width + column) * 3;
            float d = horizontal[center] * weights[0], nx = horizontal[center + 1] * weights[0],
                  ny = horizontal[center + 2] * weights[0];
            for (int offset = 1; offset <= radius; offset++) {
                const size_t above = center - offset * width * 3, below = center + offset * width * 3;
                const float weight = weights[offset];
                d += (horizontal[above] + horizontal[below]) * weight;
                nx += (horizontal[above + 1] + horizontal[below + 1]) * weight;
                ny += (horizontal[above + 2] + horizontal[below + 2]) * weight;
            }
            const float length = std::sqrt(nx * nx + ny * ny);
            const size_t output = (static_cast<size_t>(row) * width + column) * 4;
            rgba[output] = d;
            rgba[output + 1] = length > 0.000001f ? nx / length : 0.0f;
            rgba[output + 2] = length > 0.000001f ? ny / length : 0.0f;
            rgba[output + 3] = 1.0f;
        }
    }
}

// Identical Float32 axis-hoisted raster; no contour, sample spacing or optical band changes.
extern "C" void liquid_menu_contact_field_build(const float *edges, int edgeFloatCount, float originX,
                                                float originY, float step, int width, int height,
                                                float band, float *rgba) {
    const float reach = band + step * 2.0f;
    const size_t cells = static_cast<size_t>(width) * height;
    const size_t columns = static_cast<size_t>(width);
    if (scratch.contactColumns.size() < columns * 3) scratch.contactColumns.resize(columns * 3);
    float *outsideXSquared = scratch.contactColumns.data();
    float *xOffset = outsideXSquared + columns;
    float *xProjection = xOffset + columns;
    for (size_t index = 0; index < cells; index++) {
        const size_t output = index * 4;
        rgba[output] = reach * reach;
        rgba[output + 1] = 0.0f;
        rgba[output + 2] = 0.0f;
        rgba[output + 3] = -1.0f;
    }
    for (int edge = 0; edge < edgeFloatCount; edge += 6) {
        const float ax = edges[edge], ay = edges[edge + 1];
        const float bx = edges[edge + 2], by = edges[edge + 3];
        const float dx = bx - ax, dy = by - ay;
        const float lengthSquared = dx * dx + dy * dy;
        const float inverseLengthSquared = 1.0f / lengthSquared;
        const float minimumX = javaMin(ax, bx), maximumX = javaMax(ax, bx);
        const float minimumY = javaMin(ay, by), maximumY = javaMax(ay, by);
        const int left = std::max(gridIndex(std::floor(static_cast<double>((minimumX - reach - originX) / step))), 0);
        const int right = std::min(gridIndex(std::ceil(static_cast<double>((maximumX + reach - originX) / step))), width - 1);
        const int top = std::max(gridIndex(std::floor(static_cast<double>((minimumY - reach - originY) / step))), 0);
        const int bottom = std::min(gridIndex(std::ceil(static_cast<double>((maximumY + reach - originY) / step))), height - 1);
        for (int x = left; x <= right; x++) {
            const float pointX = originX + x * step;
            const float outsideX = javaMax(javaMax(minimumX - pointX, pointX - maximumX), 0.0f);
            outsideXSquared[x] = outsideX * outsideX;
            xOffset[x] = pointX - ax;
            xProjection[x] = xOffset[x] * dx;
        }
        for (int y = top; y <= bottom; y++) {
            const float pointY = originY + y * step;
            const float outsideY = javaMax(javaMax(minimumY - pointY, pointY - maximumY), 0.0f);
            const float outsideYSquared = outsideY * outsideY;
            const float py = pointY - ay;
            const float yProjection = py * dy;
            const size_t row = static_cast<size_t>(y) * width * 4;
            for (int x = left; x <= right; x++) {
                const size_t output = row + static_cast<size_t>(x) * 4;
                if (outsideXSquared[x] + outsideYSquared >= rgba[output]) continue;
                const float t = saturate((xProjection[x] + yProjection) * inverseLengthSquared);
                const float nx = xOffset[x] - t * dx, ny = py - t * dy;
                const float squared = nx * nx + ny * ny;
                if (squared >= rgba[output]) continue;
                const float direction = nx * edges[edge + 4] + ny * edges[edge + 5] >= 0.0f ? 1.0f : -1.0f;
                rgba[output] = squared;
                rgba[output + 1] = squared > 0.00000001f ? nx * direction : edges[edge + 4];
                rgba[output + 2] = squared > 0.00000001f ? ny * direction : edges[edge + 5];
                rgba[output + 3] = direction;
            }
        }
    }
    for (size_t index = 0; index < cells; index++) {
        const size_t output = index * 4;
        const float distance = static_cast<float>(std::sqrt(static_cast<double>(rgba[output])));
        rgba[output] = distance * rgba[output + 3];
        if (distance > 0.0001f) {
            rgba[output + 1] /= distance;
            rgba[output + 2] /= distance;
        }
        rgba[output + 3] = 1.0f;
    }
}

extern "C" void liquid_menu_sdf_pack(const float *rgba, int width, int height, int rowBytes, void *target) {
    for (int row = 0; row < height; row++) {
        const float *source = rgba + static_cast<size_t>(row) * width * 4;
        auto *output = reinterpret_cast<uint16_t *>(static_cast<uint8_t *>(target) +
                                                    static_cast<size_t>(row) * rowBytes);
        for (int index = 0; index < width * 4; index += 4) {
#if defined(__aarch64__)
            vst1_u16(output + index, vreinterpret_u16_f16(vcvt_f16_f32(vld1q_f32(source + index))));
#else
            auto *half = reinterpret_cast<_Float16 *>(output + index);
            for (int channel = 0; channel < 4; channel++)
                half[channel] = static_cast<_Float16>(source[index + channel]);
#endif
        }
    }
}

#if !defined(LIQUID_MENU_STANDALONE)
extern "C" JNIEXPORT jboolean JNICALL Java_io_github_neoruaa_glasmio_glass_LiquidMenuNativeField_buildContactNative(
    JNIEnv *env, jobject, jfloatArray source, jfloat originX, jfloat originY, jfloat step,
    jint width, jint height, jfloat band, jfloatArray output) {
    if (!source || !output) return JNI_FALSE;
    const int edgeFloatCount = env->GetArrayLength(source);
    if (width < 1 || height < 1 || !std::isfinite(originX) || !std::isfinite(originY) ||
        !std::isfinite(step) || step <= 0.0f || !std::isfinite(band) || band < 0.0f ||
        edgeFloatCount < 6 || edgeFloatCount % 6 != 0 ||
        static_cast<int64_t>(width) * height > env->GetArrayLength(output) / 4) return JNI_FALSE;
    if (scratch.contactEdges.size() < static_cast<size_t>(edgeFloatCount)) scratch.contactEdges.resize(edgeFloatCount);
    env->GetFloatArrayRegion(source, 0, edgeFloatCount, scratch.contactEdges.data());
    if (env->ExceptionCheck()) return JNI_FALSE;
    for (int edge = 0; edge < edgeFloatCount; edge += 6) {
        for (int channel = 0; channel < 6; channel++)
            if (!std::isfinite(scratch.contactEdges[edge + channel])) return JNI_FALSE;
        const float dx = scratch.contactEdges[edge + 2] - scratch.contactEdges[edge];
        const float dy = scratch.contactEdges[edge + 3] - scratch.contactEdges[edge + 1];
        const float squared = dx * dx + dy * dy;
        if (!(squared > 0.0f) || !std::isfinite(squared)) return JNI_FALSE;
    }
    // Copy the small contour before pinning; no JNI access occurs while the output is critical.
    const size_t columns = static_cast<size_t>(width) * 3;
    if (scratch.contactColumns.size() < columns) scratch.contactColumns.resize(columns);
    auto *rgba = static_cast<float *>(env->GetPrimitiveArrayCritical(output, nullptr));
    if (!rgba) return JNI_FALSE;
    liquid_menu_contact_field_build(scratch.contactEdges.data(), edgeFloatCount, originX, originY,
                                    step, width, height, band, rgba);
    env->ReleasePrimitiveArrayCritical(output, rgba, 0);
    return JNI_TRUE;
}

extern "C" JNIEXPORT jboolean JNICALL Java_io_github_neoruaa_glasmio_glass_LiquidMenuNativeField_buildNative(
    JNIEnv *env, jobject, jfloatArray geometry, jfloat originX, jfloat originY, jfloat step, jint width,
    jint height, jfloatArray weights, jint radius, jfloatArray output) {
    if (width < 2 || height < 2 || radius < 1 || radius > 16 || step <= 0 ||
        env->GetArrayLength(geometry) < 14 || env->GetArrayLength(weights) <= radius ||
        static_cast<int64_t>(width) * height * 4 > env->GetArrayLength(output))
        return JNI_FALSE;
    float g[14], w[17];
    env->GetFloatArrayRegion(geometry, 0, 14, g);
    env->GetFloatArrayRegion(weights, 0, radius + 1, w);
    auto *rgba = static_cast<float *>(env->GetPrimitiveArrayCritical(output, nullptr));
    if (!rgba)
        return JNI_FALSE;
    liquid_menu_sdf_build(g, originX, originY, step, width, height, w, radius, rgba);
    env->ReleasePrimitiveArrayCritical(output, rgba, 0);
    return JNI_TRUE;
}
extern "C" JNIEXPORT jboolean JNICALL Java_io_github_neoruaa_glasmio_glass_LiquidMenuNativeField_packNative(
    JNIEnv *env, jobject, jfloatArray source, jint width, jint height, jint rowBytes, jobject target) {
    if (width < 1 || height < 1 || rowBytes < static_cast<int64_t>(width) * 8 ||
        static_cast<int64_t>(width) * height * 4 > env->GetArrayLength(source) ||
        static_cast<int64_t>(rowBytes) * height > env->GetDirectBufferCapacity(target))
        return JNI_FALSE;
    void *output = env->GetDirectBufferAddress(target);
    if (!output)
        return JNI_FALSE;
    auto *rgba = static_cast<float *>(env->GetPrimitiveArrayCritical(source, nullptr));
    if (!rgba)
        return JNI_FALSE;
    liquid_menu_sdf_pack(rgba, width, height, rowBytes, output);
    env->ReleasePrimitiveArrayCritical(source, rgba, JNI_ABORT);
    return JNI_TRUE;
}
extern "C" JNIEXPORT void JNICALL Java_io_github_neoruaa_glasmio_glass_LiquidMenuNativeField_releaseNative(JNIEnv *,
                                                                                                 jobject) {
    std::vector<float>().swap(scratch.raw);
    std::vector<float>().swap(scratch.horizontal);
    std::vector<float>().swap(scratch.contactEdges);
    std::vector<float>().swap(scratch.contactColumns);
}

namespace {
inline uint64_t navigationFloatBits(float value) {
    uint32_t bits;
    std::memcpy(&bits, &value, sizeof(bits));
    return bits;
}

struct NavigationUnionPath {
    JNIEnv *env;
    jobject path;
    jmethodID moveTo, lineTo, close;
    RoundedField a, b;
    bool hasSecond;
    float smoothness;
    const float *ag;
    const float *bg;
    float centerY, guard, left, top, right, bottom;
    std::unordered_map<uint64_t, float> edgeRoots;
    std::vector<std::pair<float, float>> points;

    NavigationUnionPath(JNIEnv *env, jobject path, const float *geometry)
        : env(env), path(path), a(geometry, true), b(geometry + 6, true), hasSecond(geometry[13] != 0.0f),
          smoothness(geometry[12]), ag(geometry), bg(geometry + 6) {
        jclass pathClass = env->GetObjectClass(path);
        moveTo = env->GetMethodID(pathClass, "moveTo", "(FF)V");
        lineTo = env->GetMethodID(pathClass, "lineTo", "(FF)V");
        close = env->GetMethodID(pathClass, "close", "()V");
        env->DeleteLocalRef(pathClass);
        left = hasSecond ? std::min(ag[0], bg[0]) : ag[0];
        top = hasSecond ? std::min(ag[1], bg[1]) : ag[1];
        right = hasSecond ? std::max(ag[2], bg[2]) : ag[2];
        bottom = hasSecond ? std::max(ag[3], bg[3]) : ag[3];
        centerY = hasSecond ? ((ag[1] + ag[3]) / 2.0f + (bg[1] + bg[3]) / 2.0f) * 0.5f
                            : (ag[1] + ag[3]) / 2.0f;
        guard = smoothness * 0.25f + 1.0f;
        top -= guard;
        bottom += guard;
    }

    float distance(float x, float y) const {
        if (!hasSecond) return a.distance(x, y);
        const float ad = a.distance(x, y);
        const float bd = b.distance(x, y);
        if (std::abs(ad - bd) >= smoothness) {
            // The mixed weight is saturated even at the maximum possible radius.
            // Preserve the original saturated arithmetic without evaluating normals.
            return ad <= bd ? bd + (ad - bd) : bd;
        }
        const Normal an = a.normal(x, y, 0.0f), bn = b.normal(x, y, 0.0f);
        const float radius = smoothness * saturate(0.5f - 0.5f * (an.x * bn.x + an.y * bn.y));
        const float h = radius <= 0.000001f ? (ad < bd ? 1.0f : 0.0f)
                                           : saturate(0.5f + 0.5f * (bd - ad) / radius);
        return bd + (ad - bd) * h - radius * h * (1.0f - h);
    }

    float rootX(float outside, float inside) {
        float lo = outside, hi = inside;
        for (int i = 0; i < 20; ++i) {
            const float mid = (lo + hi) * 0.5f;
            if (distance(mid, centerY) > 0.0f) lo = mid; else hi = mid;
        }
        return (lo + hi) * 0.5f;
    }

    float edgeY(float x, float outer) {
        const uint64_t key = (navigationFloatBits(x) << 32) | navigationFloatBits(outer);
        const auto cached = edgeRoots.find(key);
        if (cached != edgeRoots.end()) return cached->second;
        float lo = outer, hi = centerY;
        for (int i = 0; i < 20; ++i) {
            const float mid = (lo + hi) * 0.5f;
            if (distance(x, mid) > 0.0f) lo = mid; else hi = mid;
        }
        const float result = (lo + hi) * 0.5f;
        edgeRoots.emplace(key, result);
        return result;
    }

    void subdivide(float x0, float y0, float x1, float y1, float outer, int depth) {
        const float xm = (x0 + x1) * 0.5f;
        const float ym = edgeY(xm, outer);
        const bool refine = std::abs(x1 - x0) > 8.0f || std::abs(ym - (y0 + y1) * 0.5f) > 0.125f ||
            std::abs(edgeY(x0 + (x1 - x0) * 0.25f, outer) - (y0 + (y1 - y0) * 0.25f)) > 0.125f ||
            std::abs(edgeY(x0 + (x1 - x0) * 0.75f, outer) - (y0 + (y1 - y0) * 0.75f)) > 0.125f;
        if (depth < 16 && refine) {
            subdivide(x0, y0, xm, ym, outer, depth + 1);
            subdivide(xm, ym, x1, y1, outer, depth + 1);
        } else {
            points.emplace_back(x1, y1);
        }
    }

    void append(float appendLeft, float appendRight) {
        std::vector<float> stops = {appendLeft, appendRight};
        auto addElement = [&stops](const float *g) {
            const float cx = (g[0] + g[2]) / 2.0f;
            stops.push_back(cx);
            stops.push_back(g[0] + g[4]);
            stops.push_back(g[2] - g[4]);
            stops.push_back(g[0]);
            stops.push_back(g[2]);
        };
        addElement(ag);
        if (hasSecond) {
            addElement(bg);
            stops.push_back((ag[2] + bg[0]) * 0.5f);
        }
        stops.erase(std::remove_if(stops.begin(), stops.end(), [appendLeft, appendRight](float value) {
            return value < appendLeft || value > appendRight;
        }), stops.end());
        std::vector<float> distinct;
        for (float value : stops) {
            bool exists = false;
            for (float previous : distinct) {
                if (navigationFloatBits(previous) == navigationFloatBits(value)) {
                    exists = true;
                    break;
                }
            }
            if (!exists) distinct.push_back(value);
        }
        std::sort(distinct.begin(), distinct.end());

        env->CallVoidMethod(path, moveTo, appendLeft, edgeY(appendLeft, top));
        for (int pass = 0; pass < 2; ++pass) {
            const float outer = pass == 0 ? top : bottom;
            points.clear();
            if (pass == 0) {
                for (size_t i = 0; i + 1 < distinct.size(); ++i) {
                    const float x0 = distinct[i], x1 = distinct[i + 1];
                    subdivide(x0, edgeY(x0, outer), x1, edgeY(x1, outer), outer, 0);
                }
            } else {
                for (size_t i = distinct.size(); i-- > 1;) {
                    const float x0 = distinct[i], x1 = distinct[i - 1];
                    subdivide(x0, edgeY(x0, outer), x1, edgeY(x1, outer), outer, 0);
                }
            }
            for (const auto &point : points) env->CallVoidMethod(path, lineTo, point.first, point.second);
            if (pass == 0) env->CallVoidMethod(path, lineTo, appendRight, edgeY(appendRight, bottom));
        }
        env->CallVoidMethod(path, close);
    }

    void build() {
        const float firstCenterX = (ag[0] + ag[2]) / 2.0f;
        const float secondCenterX = hasSecond ? (bg[0] + bg[2]) / 2.0f : firstCenterX;
        if (!hasSecond || distance((ag[2] + (hasSecond ? bg[0] : ag[0])) * 0.5f, centerY) <= 0.0f) {
            append(rootX(left - guard, firstCenterX), rootX(right + guard, secondCenterX));
        } else {
            const float gap = (ag[2] + bg[0]) * 0.5f;
            append(rootX(ag[0] - guard, firstCenterX), rootX(gap, firstCenterX));
            append(rootX(gap, secondCenterX), rootX(bg[2] + guard, secondCenterX));
        }
    }
};
} // namespace

extern "C" JNIEXPORT jboolean JNICALL
Java_io_github_neoruaa_glasmio_glass_LiquidMenuNativeField_buildNavigationPathNative(
    JNIEnv *env, jobject, jfloatArray geometry, jobject path) {
    if (!geometry || !path || env->GetArrayLength(geometry) != 14) return JNI_FALSE;
    float g[14];
    env->GetFloatArrayRegion(geometry, 0, 14, g);
    if (env->ExceptionCheck()) return JNI_FALSE;
    for (float value : g) if (!std::isfinite(value)) return JNI_FALSE;
    NavigationUnionPath builder(env, path, g);
    if (!builder.moveTo || !builder.lineTo || !builder.close) return JNI_FALSE;
    builder.build();
    return env->ExceptionCheck() ? JNI_FALSE : JNI_TRUE;
}
#endif
