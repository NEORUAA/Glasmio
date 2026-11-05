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

#if defined(__aarch64__)
// Lane-wise float operations for 2- and 4-lane NEON vectors. Comparisons and selection reproduce
// the scalar std::min/max/clamp tie and NaN rules, so each lane is bit-identical to the scalar code.
template <typename V> struct Lanes;
template <> struct Lanes<float32x2_t> {
    using V = float32x2_t;
    using M = uint32x2_t;
    static V dup(float v) { return vdup_n_f32(v); }
    static V add(V a, V b) { return vadd_f32(a, b); }
    static V sub(V a, V b) { return vsub_f32(a, b); }
    static V mul(V a, V b) { return vmul_f32(a, b); }
    static V div(V a, V b) { return vdiv_f32(a, b); }
    static V sqrt(V a) { return vsqrt_f32(a); }
    static V abs(V a) { return vabs_f32(a); }
    static V neg(V a) { return vneg_f32(a); }
    static M lt(V a, V b) { return vclt_f32(a, b); }
    static M gt(V a, V b) { return vcgt_f32(a, b); }
    static M le(V a, V b) { return vcle_f32(a, b); }
    static V select(M m, V a, V b) { return vbsl_f32(m, a, b); }
};
template <> struct Lanes<float32x4_t> {
    using V = float32x4_t;
    using M = uint32x4_t;
    static V dup(float v) { return vdupq_n_f32(v); }
    static V add(V a, V b) { return vaddq_f32(a, b); }
    static V sub(V a, V b) { return vsubq_f32(a, b); }
    static V mul(V a, V b) { return vmulq_f32(a, b); }
    static V div(V a, V b) { return vdivq_f32(a, b); }
    static V sqrt(V a) { return vsqrtq_f32(a); }
    static V abs(V a) { return vabsq_f32(a); }
    static V neg(V a) { return vnegq_f32(a); }
    static M lt(V a, V b) { return vcltq_f32(a, b); }
    static M gt(V a, V b) { return vcgtq_f32(a, b); }
    static M le(V a, V b) { return vcleq_f32(a, b); }
    static V select(M m, V a, V b) { return vbslq_f32(m, a, b); }
};

// RoundedField::distance and normal(x, y, 0) over SIMD lanes. Lanes hold either different fields
// at one point (FieldLanes<float32x2_t>::pair) or one field at different points
// (FieldLanes<float32x4_t>::broadcast). Every lane repeats the scalar operations in order.
template <typename V> struct FieldLanes {
    using L = Lanes<V>;
    using M = typename L::M;
    V cx, cy, radius, inverseX, inverseY, halfWidth, halfHeight;
    V expanded, curvatureX, curvatureY, baseRadius;
    M circular;
    bool anyCircular, allCircular;

    // std::min(a, b) is (b < a) ? b : a; std::max(a, b) is (a < b) ? b : a.
    static V minOf(V a, V b) { return L::select(L::lt(b, a), b, a); }
    static V maxOf(V a, V b) { return L::select(L::lt(a, b), b, a); }
    // std::clamp(v, 0, 1) is (v < 0) ? 0 : (1 < v) ? 1 : v.
    static V saturateLanes(V v) {
        const V zero = L::dup(0.0f), one = L::dup(1.0f);
        return L::select(L::lt(v, zero), zero, L::select(L::lt(one, v), one, v));
    }

    V distance(V x, V y) const {
        const V zero = L::dup(0.0f), one = L::dup(1.0f);
        const V qx = L::sub(L::abs(L::mul(L::sub(x, cx), inverseX)), halfWidth);
        const V qy = L::sub(L::abs(L::mul(L::sub(y, cy), inverseY)), halfHeight);
        const V bx = L::add(qx, baseRadius), by = L::add(qy, baseRadius);
        const V interior = minOf(maxOf(bx, by), zero);
        V round = zero;
        if (anyCircular) {
            const V dx = maxOf(bx, zero), dy = maxOf(by, zero);
            round = L::sub(L::add(interior, L::sqrt(L::add(L::mul(dx, dx), L::mul(dy, dy)))), radius);
            if (allCircular) return round;
        }
        const V ux = L::div(L::add(qx, expanded), expanded);
        const V uy = L::div(L::add(qy, expanded), expanded);
        const V zx = maxOf(ux, zero), zy = maxOf(uy, zero);
        const V maximum = maxOf(zx, zy);
        const V t = L::select(L::gt(maximum, zero), L::div(minOf(zx, zy), maximum), zero);
        const V length = L::sqrt(L::add(L::mul(zx, zx), L::mul(zy, zy)));
        V polynomial = L::mul(L::dup(-0.9260540008544922f), t);
        polynomial = L::mul(L::add(polynomial, L::dup(3.1560099124908447f)), t);
        polynomial = L::mul(L::sub(polynomial, L::dup(3.6412200927734375f)), t);
        polynomial = L::mul(L::add(polynomial, L::dup(1.268030047416687f)), t);
        polynomial = L::mul(L::mul(L::add(polynomial, L::dup(0.2685309946537018f)), t), t);
        const V continuous = L::sub(L::add(length, one),
            L::div(one, L::sub(one, L::mul(polynomial, minOf(length, one)))));
        const V ax = maxOf(L::sub(L::mul(L::dup(kExpansion), ux), L::dup(kAdjustment)), zero);
        const V ay = maxOf(L::sub(L::mul(L::dup(kExpansion), uy), L::dup(kAdjustment)), zero);
        const V correction = L::add(L::sub(L::dup(0.3458344340324402f), continuous),
            L::mul(L::dup(0.6541655659675598f), L::sqrt(L::add(L::mul(ax, ax), L::mul(ay, ay)))));
        const V sign = L::select(L::lt(zx, zy), one, L::dup(-1.0f));
        const V weight = saturateLanes(L::add(L::mul(sign, L::sub(t, one)), L::dup(0.5f)));
        const V result = L::add(interior, L::mul(expanded, L::add(L::sub(continuous, one),
            L::mul(correction, L::add(curvatureX, L::mul(L::sub(curvatureY, curvatureX), weight))))));
        return anyCircular ? L::select(circular, round, result) : result;
    }

    void normal(V x, V y, V &outX, V &outY) const {
        const V zero = L::dup(0.0f), one = L::dup(1.0f), epsilon = L::dup(0.000001f);
        const V lx = L::mul(L::sub(x, cx), inverseX);
        const V ly = L::mul(L::sub(y, cy), inverseY);
        const V qx = L::add(L::sub(L::abs(lx), halfWidth), baseRadius);
        const V qy = L::add(L::sub(L::abs(ly), halfHeight), baseRadius);
        V nx = maxOf(qx, zero), ny = maxOf(qy, zero);
        const V cornerLength = L::sqrt(L::add(L::mul(nx, nx), L::mul(ny, ny)));
        const M corner = L::gt(cornerLength, epsilon);
        const M horizontal = L::gt(qx, qy);
        nx = L::select(corner, L::div(nx, cornerLength), L::select(horizontal, one, zero));
        ny = L::select(corner, L::div(ny, cornerLength), L::select(horizontal, zero, one));
        nx = L::select(L::lt(lx, zero), L::neg(nx), nx);
        ny = L::select(L::lt(ly, zero), L::neg(ny), ny);
        const V length = L::sqrt(L::add(L::mul(nx, nx), L::mul(ny, ny)));
        const M degenerate = L::le(length, epsilon);
        outX = L::select(degenerate, zero, L::mul(L::div(nx, length), inverseX));
        outY = L::select(degenerate, zero, L::mul(L::div(ny, length), inverseY));
    }
};

// Lane 0 holds [a], lane 1 holds [b].
inline FieldLanes<float32x2_t> fieldPair(const RoundedField &a, const RoundedField &b) {
    auto pair = [](float u, float v) {
        const float lanes[2] = {u, v};
        return vld1_f32(lanes);
    };
    FieldLanes<float32x2_t> f;
    f.cx = pair(a.cx, b.cx);
    f.cy = pair(a.cy, b.cy);
    f.radius = pair(a.radius, b.radius);
    f.inverseX = pair(a.inverseX, b.inverseX);
    f.inverseY = pair(a.inverseY, b.inverseY);
    f.halfWidth = pair(a.halfWidth, b.halfWidth);
    f.halfHeight = pair(a.halfHeight, b.halfHeight);
    f.expanded = pair(a.expanded, b.expanded);
    f.curvatureX = pair(a.curvatureX, b.curvatureX);
    f.curvatureY = pair(a.curvatureY, b.curvatureY);
    f.baseRadius = pair(a.baseRadius, b.baseRadius);
    const uint32_t flags[2] = {a.circular ? ~0u : 0u, b.circular ? ~0u : 0u};
    f.circular = vld1_u32(flags);
    f.anyCircular = a.circular || b.circular;
    f.allCircular = a.circular && b.circular;
    return f;
}

// All four lanes hold [a].
inline FieldLanes<float32x4_t> fieldBroadcast(const RoundedField &a) {
    FieldLanes<float32x4_t> f;
    f.cx = vdupq_n_f32(a.cx);
    f.cy = vdupq_n_f32(a.cy);
    f.radius = vdupq_n_f32(a.radius);
    f.inverseX = vdupq_n_f32(a.inverseX);
    f.inverseY = vdupq_n_f32(a.inverseY);
    f.halfWidth = vdupq_n_f32(a.halfWidth);
    f.halfHeight = vdupq_n_f32(a.halfHeight);
    f.expanded = vdupq_n_f32(a.expanded);
    f.curvatureX = vdupq_n_f32(a.curvatureX);
    f.curvatureY = vdupq_n_f32(a.curvatureY);
    f.baseRadius = vdupq_n_f32(a.baseRadius);
    f.circular = vdupq_n_u32(a.circular ? ~0u : 0u);
    f.anyCircular = f.allCircular = a.circular;
    return f;
}
#endif

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

// Upper bound of the rounding noise of the navigation union field near its zero, in pixels of
// field value (observed noise is about 6e-5 for coordinates up to ~1100).
constexpr float kNavigationFieldNoise = 2e-4f;
// Distance from a search endpoint within which a crossing may be a graze (see RootSearch).
constexpr float kNavigationGrazeZone = 1.0f;
// Subdivision accepts a chord whose roots stay within this distance of it.
constexpr float kRefineTolerance = 0.125f;
// Half-width of the verified window around a predicted root (see RootSearch::startDecision), and the
// allowance for Float32 rounding of the window positions and of the caller's comparison.
constexpr float kNavigationDecisionWindow = 0.11f;
constexpr float kNavigationDecisionSlack = 0.002f;
// A nearly coaxial ray's tail counts as inside when one capsule field stays below minus this.
constexpr float kNavigationTailMargin = 0.01f;

// Produces exactly the result of a 20-step bisection of [outside, inside] (the field is positive
// outside the contour), with far fewer field evaluations. It runs as a resumable state machine:
// start() and resume() return true while [probe] needs a field value, and false once [result] is
// final, so several searches can share vectorized field evaluations.
//
// A safeguarded Illinois iteration first encloses the sign change in a bracket of evaluated
// points [lo: f > 0, hi: f <= 0] no wider than the bisection's final interval. The bisection is
// then replayed step by step: along the monotonic search ray, a midpoint at or outside lo is
// positive and one at or inside hi is not, so only midpoints within the rounding-noise band around
// the bracket are evaluated. Every midpoint, decision and the result are therefore identical to
// the plain bisection. An optional hint (an adjacent contour chord) first tries a narrow enclosing
// window; it only reduces the number of evaluations.
//
// Inference requires a ray with a single crossing (singleCrossing); a window bracket also needs
// the inside endpoint to be inside the contour, which insideIsInterior may assert in advance.
// Otherwise, and whenever the evaluated values contradict monotonicity, the plain bisection runs.
struct RootSearch {
    static constexpr int kBisectionSteps = 20;
    enum class Phase : uint8_t { WindowA, WindowB, Lo, Hi, Inside, Illinois, Replay, Bisect, DecideA, DecideB,
                                 DecideInside };

    float probe = 0.0f, result = 0.0f;
    // Set instead of [result] when a decision query proved |result - center| <= kRefineTolerance.
    bool decided = false;

    // Decision query: the caller only needs to know whether the bisection result lies within
    // kRefineTolerance of a predicted [center]. The prediction is verified by evaluating the field at
    // center +- kNavigationDecisionWindow. Along a monotonic ray every point outside a value above
    // twice the rounding noise is positive, and every point inside a value at or below minus twice
    // the noise is not, so the bisection's final interval meets that window. If the values do not
    // prove it, the exact search starts afresh, exactly as start() would.
    // [decidable] and [decisionInterior] state what the decision may assume; they can be proven for
    // rays whose exact search still needs the plain bisection (singleCrossing false).
    bool startDecision(float outsideValue, float insideValue, bool singleCrossing, bool insideIsInterior,
                       float hintValue, float center, bool decidable, bool decisionInterior) {
        setup(outsideValue, insideValue, singleCrossing, insideIsInterior, hintValue);
        knownInside = decisionInterior;
        if (decidable && tolerance > 0.0f && std::isfinite(center) &&
            kNavigationDecisionWindow + tolerance + kNavigationDecisionSlack <= kRefineTolerance) {
            const float window = std::copysign(kNavigationDecisionWindow, inside - outside);
            a = center - window;
            b = center + window;
            if ((a - outside) * (inside - a) > 0.0f && (b - outside) * (inside - b) > 0.0f &&
                std::abs(b - inside) > kNavigationGrazeZone && std::abs(a - outside) > kNavigationGrazeZone)
                return request(Phase::DecideA, a);
        }
        return startExact();
    }

    bool start(float outsideValue, float insideValue, bool singleCrossing, bool insideIsInterior,
               float hintValue = std::numeric_limits<float>::quiet_NaN()) {
        setup(outsideValue, insideValue, singleCrossing, insideIsInterior, hintValue);
        return startExact();
    }

    bool resume(float value) {
        switch (phase) {
        case Phase::DecideA:
            return value > 2.0f * kNavigationFieldNoise ? request(Phase::DecideB, b) : startExact();
        case Phase::DecideB:
            if (!(value <= -2.0f * kNavigationFieldNoise)) return startExact();
            // As for a window bracket, the inside endpoint must be inside the contour.
            return knownInside ? finishDecided() : request(Phase::DecideInside, inside);
        case Phase::DecideInside:
            return value <= 0.0f ? finishDecided() : startExact();
        case Phase::WindowA:
            if (value > 0.0f) {
                lo = a; flo = value; windowLo = true;
                return request(Phase::WindowB, b);
            }
            hi = a; fhi = value; windowHi = true;
            return afterWindow();
        case Phase::WindowB:
            if (value <= 0.0f) { hi = b; fhi = value; windowHi = true; }
            else { lo = b; flo = value; }
            return afterWindow();
        case Phase::Lo:
            flo = value;
            return afterLo();
        case Phase::Hi:
            fhi = value;
            return afterHi();
        case Phase::Inside:
            return decide(value <= 0.0f);
        case Phase::Illinois:
            if (value > 0.0f) {
                lo = probe; flo = value;
                if (side == 1) fhi *= 0.5f;
                side = 1;
            } else {
                hi = probe; fhi = value;
                if (side == -1) flo *= 0.5f;
                side = -1;
            }
            ++iteration;
            return illinois();
        case Phase::Replay: {
            const bool positive = value > 0.0f;
            if (positive) {
                if (position >= alongHi) return beginBisect();
                if (position > alongLo) { lo = probe; alongLo = position; }
                l = probe;
            } else {
                if (position <= alongLo) return beginBisect();
                if (position < alongHi) { hi = probe; alongHi = position; }
                h = probe;
            }
            ++step;
            return replay();
        }
        case Phase::Bisect:
            if (value > 0.0f) l = probe; else h = probe;
            ++step;
            return bisect();
        }
        return false;
    }

private:
    float outside = 0.0f, inside = 0.0f, tolerance = 0.0f;
    float lo = 0.0f, hi = 0.0f, flo = 0.0f, fhi = 0.0f, a = 0.0f, b = 0.0f;
    float margin = 0.0f, direction = 0.0f, band = 0.0f, alongLo = 0.0f, alongHi = 0.0f;
    float l = 0.0f, h = 0.0f, position = 0.0f, hint = 0.0f;
    bool interior = false, windowLo = false, windowHi = false, single = false, knownInside = false;
    int side = 0, iteration = 0, step = 0;
    Phase phase = Phase::Bisect;

    void setup(float outsideValue, float insideValue, bool singleCrossing, bool insideIsInterior, float hintValue) {
        outside = outsideValue;
        inside = insideValue;
        single = singleCrossing;
        interior = insideIsInterior;
        hint = hintValue;
        decided = false;
        tolerance = std::abs(inside - outside) * (1.0f / float(1 << kBisectionSteps));
    }

    bool startExact() {
        lo = outside;
        hi = inside;
        flo = fhi = 0.0f;
        windowLo = windowHi = false;
        if (!single) return beginBisect();
        if (std::isfinite(hint) && tolerance > 0.0f) {
            const float window = std::copysign(0.5f, inside - outside);
            a = hint - window;
            b = hint + window;
            if ((a - outside) * (inside - a) > 0.0f && (b - outside) * (inside - b) > 0.0f)
                return request(Phase::WindowA, a);
        }
        return afterWindow();
    }

    bool finishDecided() {
        decided = true;
        return false;
    }

    bool request(Phase next, float at) {
        phase = next;
        probe = at;
        return true;
    }

    bool afterWindow() { return windowLo ? afterLo() : request(Phase::Lo, lo); }
    bool afterLo() { return windowHi ? afterHi() : request(Phase::Hi, hi); }
    bool afterHi() {
        // The outside endpoint lies beyond the union's guard and is always positive. A window
        // bracket still has to confirm that the inside endpoint is inside the contour (a column
        // between disconnected capsules has no crossing before it).
        if (windowHi && !interior) return request(Phase::Inside, inside);
        return decide(true);
    }

    bool decide(bool endpointsOrdered) {
        if (!(endpointsOrdered && flo > 0.0f && fhi <= 0.0f && tolerance > 0.0f)) return beginBisect();
        // Rounding makes the field non-monotonic within roughly 1e-4 of its zero, where the
        // plain bisection's decisions are decided by that rounding. Midpoints are inferred only
        // when they lie a noise margin beyond the evaluated bracket, measured with the slope of
        // the initial (wide) bracket; every other midpoint is evaluated exactly as before.
        const float span = std::abs(hi - lo);
        const float slope = span > 0.0f ? (flo - fhi) / span : 0.0f;
        margin = std::max(tolerance, slope > 0.0f ? kNavigationFieldNoise / slope : std::abs(inside - outside));
        side = 0;
        iteration = 0;
        return illinois();
    }

    bool illinois() {
        if (iteration < 12 && std::abs(hi - lo) > tolerance) {
            float x = lo + flo * (hi - lo) / (flo - fhi);
            // Keep every probe at least half the tolerance inside the bracket, so that a
            // converged estimate is enclosed from both sides on the following steps.
            const float nudge = std::copysign(tolerance * 0.5f, hi - lo);
            if (!(std::abs(x - lo) >= tolerance * 0.5f)) x = lo + nudge;
            if (!(std::abs(hi - x) >= tolerance * 0.5f)) x = hi - nudge;
            if (!((x - lo) * (hi - x) > 0.0f)) x = (lo + hi) * 0.5f;
            if (!(x == lo || x == hi)) return request(Phase::Illinois, x);
        }
        return beginReplay();
    }

    bool beginReplay() {
        // Near a search endpoint the ray can graze the contour (a capsule tip on the centre
        // line), where the field stays within rounding of zero over a long stretch and is not
        // monotonic. Such columns are rare and keep the plain bisection.
        if (std::abs(hi - inside) <= kNavigationGrazeZone || std::abs(lo - outside) <= kNavigationGrazeZone)
            return beginBisect();
        // Positions are measured along the ray (increasing towards inside). An inverted bracket,
        // or a later evaluation that contradicts it, means the field is not monotonic here.
        direction = inside - outside;
        band = margin * std::abs(direction);
        alongLo = (lo - outside) * direction;
        alongHi = (hi - outside) * direction;
        if (alongHi <= alongLo) return beginBisect();
        l = outside;
        h = inside;
        step = 0;
        return replay();
    }

    bool replay() {
        while (step < kBisectionSteps) {
            const float mid = (l + h) * 0.5f;
            const float along = (mid - outside) * direction;
            bool positive;
            if (mid == lo) positive = true;
            else if (mid == hi) positive = false;
            else if (along <= alongLo - band) positive = true;
            else if (along >= alongHi + band) positive = false;
            else {
                position = along;
                return request(Phase::Replay, mid);
            }
            if (positive) l = mid; else h = mid;
            ++step;
        }
        result = (l + h) * 0.5f;
        return false;
    }

    bool beginBisect() {
        l = outside;
        h = inside;
        step = 0;
        return bisect();
    }

    bool bisect() {
        if (step < kBisectionSteps) return request(Phase::Bisect, (l + h) * 0.5f);
        result = (l + h) * 0.5f;
        return false;
    }
};

struct NavigationUnionPath {
    // Closed contours, each written as [n, x0, y0, ..., x(n-1), y(n-1)]: moveTo the first point,
    // lineTo the others, then close. Kotlin replays them into the Path.
    std::vector<float> contours;
    RoundedField a, b;
#if defined(__aarch64__)
    FieldLanes<float32x2_t> fields;
    FieldLanes<float32x4_t> fieldA, fieldB;
#endif
    bool hasSecond;
    float smoothness;
    const float *ag;
    const float *bg;
    float centerY, guard, left, top, right, bottom;
    // With a common vertical centre, every column of either capsule is an interval around
    // centerY, so each search ray towards centerY crosses the union's contour at most once.
    bool singleCrossingRays;
    // Centres within the host's one-pixel coaxial limit but not common: each column ray is
    // proven to cross once for decisions (see tailInside).
    bool nearlyCoaxial;

    // One contour root on a vertical ray: y on column x between [outer] and centerY.
    struct RootRequest {
        float x, outer, hint, center;  // center: decision query prediction, NaN for an exact root
        bool single, interior, decidable, decisionInterior;
    };
    struct Segment {
        float x0, y0, x1, y1;
        int depth;
        bool leaf;
    };
    std::vector<RootRequest> requests;
    std::vector<float> roots;
    // decided[i]: request i was a decision query that proved |root - prediction| <= kRefineTolerance.
    std::vector<char> decided;
    std::vector<Segment> segments[2], refined;
    std::vector<int> pending, stage, survivors;
    std::vector<float> mids;
    std::vector<char> refine, exactMid;

    explicit NavigationUnionPath(const float *geometry)
        : a(geometry, true), b(geometry + 6, true),
#if defined(__aarch64__)
          fields(fieldPair(a, b)), fieldA(fieldBroadcast(a)), fieldB(fieldBroadcast(b)),
#endif
          hasSecond(geometry[13] != 0.0f), smoothness(geometry[12]), ag(geometry), bg(geometry + 6) {
        left = hasSecond ? std::min(ag[0], bg[0]) : ag[0];
        top = hasSecond ? std::min(ag[1], bg[1]) : ag[1];
        right = hasSecond ? std::max(ag[2], bg[2]) : ag[2];
        bottom = hasSecond ? std::max(ag[3], bg[3]) : ag[3];
        centerY = hasSecond ? ((ag[1] + ag[3]) / 2.0f + (bg[1] + bg[3]) / 2.0f) * 0.5f
                            : (ag[1] + ag[3]) / 2.0f;
        guard = smoothness * 0.25f + 1.0f;
        singleCrossingRays = !hasSecond ||
            std::abs((ag[1] + ag[3]) * 0.5f - (bg[1] + bg[3]) * 0.5f) <= 1e-3f;
        nearlyCoaxial = !singleCrossingRays &&
            std::abs((ag[1] + ag[3]) * 0.5f - (bg[1] + bg[3]) * 0.5f) <= 1.0f;
        top -= guard;
        bottom += guard;
    }

    float saturatedUnion(float ad, float bd) const {
        // The mixed weight is saturated even at the maximum possible radius.
        // Preserve the original saturated arithmetic without evaluating normals.
        return ad <= bd ? bd + (ad - bd) : bd;
    }

    float blendedUnion(float ad, float bd, Normal an, Normal bn) const {
        const float radius = smoothness * saturate(0.5f - 0.5f * (an.x * bn.x + an.y * bn.y));
        const float h = radius <= 0.000001f ? (ad < bd ? 1.0f : 0.0f)
                                           : saturate(0.5f + 0.5f * (bd - ad) / radius);
        return bd + (ad - bd) * h - radius * h * (1.0f - h);
    }

    float distance(float x, float y) const {
        if (!hasSecond) return a.distance(x, y);
#if defined(__aarch64__)
        // Both capsule fields in one pair of NEON lanes, bit-identical to the scalar calls.
        const float32x2_t both = fields.distance(vdup_n_f32(x), vdup_n_f32(y));
        const float ad = vget_lane_f32(both, 0);
        const float bd = vget_lane_f32(both, 1);
#else
        const float ad = a.distance(x, y);
        const float bd = b.distance(x, y);
#endif
        if (std::abs(ad - bd) >= smoothness) return saturatedUnion(ad, bd);
#if defined(__aarch64__)
        float32x2_t normalX, normalY;
        fields.normal(vdup_n_f32(x), vdup_n_f32(y), normalX, normalY);
        const Normal an{vget_lane_f32(normalX, 0), vget_lane_f32(normalY, 0)};
        const Normal bn{vget_lane_f32(normalX, 1), vget_lane_f32(normalY, 1)};
#else
        const Normal an = a.normal(x, y, 0.0f), bn = b.normal(x, y, 0.0f);
#endif
        return blendedUnion(ad, bd, an, bn);
    }

#if defined(__aarch64__)
    // distance() at four points, each lane bit-identical to the scalar call (saturatedUnion and
    // blendedUnion repeated lane-wise).
    float32x4_t distance4(float32x4_t px, float32x4_t py) const {
        const float32x4_t ad = fieldA.distance(px, py);
        if (!hasSecond) return ad;
        const float32x4_t bd = fieldB.distance(px, py);
        const float32x4_t zero = vdupq_n_f32(0.0f), one = vdupq_n_f32(1.0f), half = vdupq_n_f32(0.5f);
        const float32x4_t difference = vsubq_f32(ad, bd);
        const uint32x4_t saturated = vcgeq_f32(vabsq_f32(difference), vdupq_n_f32(smoothness));
        const float32x4_t saturatedValue = vbslq_f32(vcleq_f32(ad, bd), vaddq_f32(bd, difference), bd);
        if (vminvq_u32(saturated) != 0u) return saturatedValue;
        float32x4_t anx, any, bnx, bny;
        fieldA.normal(px, py, anx, any);
        fieldB.normal(px, py, bnx, bny);
        const float32x4_t dot = vaddq_f32(vmulq_f32(anx, bnx), vmulq_f32(any, bny));
        const float32x4_t radius = vmulq_f32(vdupq_n_f32(smoothness),
            FieldLanes<float32x4_t>::saturateLanes(vsubq_f32(half, vmulq_f32(half, dot))));
        const float32x4_t nearest = vbslq_f32(vcltq_f32(ad, bd), one, zero);
        const float32x4_t smooth = FieldLanes<float32x4_t>::saturateLanes(
            vaddq_f32(half, vdivq_f32(vmulq_f32(half, vsubq_f32(bd, ad)), radius)));
        const float32x4_t weight = vbslq_f32(vcleq_f32(radius, vdupq_n_f32(0.000001f)), nearest, smooth);
        const float32x4_t blended = vsubq_f32(vaddq_f32(bd, vmulq_f32(difference, weight)),
                                              vmulq_f32(vmulq_f32(radius, weight), vsubq_f32(one, weight)));
        return vbslq_f32(saturated, saturatedValue, blended);
    }
#endif

    template <typename Field>
    static float findRoot(float outside, float inside, Field &&field, const RootRequest &r,
                          char *decided = nullptr) {
        RootSearch search;
        if (startSearch(search, outside, inside, r)) {
            while (search.resume(field(search.probe))) {
            }
        }
        if (decided) *decided = search.decided;
        return search.result;
    }

    static bool startSearch(RootSearch &search, float outside, float inside, const RootRequest &r) {
        return std::isnan(r.center) ? search.start(outside, inside, r.single, r.interior, r.hint)
            : search.startDecision(outside, inside, r.single, r.interior, r.hint, r.center, r.decidable,
                                   r.decisionInterior);
    }

    float rootX(float outside, float inside) {
        // The inside endpoint is a capsule centre on the centre line.
        const float none = std::numeric_limits<float>::quiet_NaN();
        return findRoot(outside, inside, [this](float x) { return distance(x, centerY); },
                        RootRequest{centerY, outside, none, none, singleCrossingRays, true, false, false});
    }

    // On a common centre line, a column within either capsule's horizontal span (by one pixel)
    // meets centerY well inside that capsule, and the union is never further out than it.
    bool centreInside(float x) const {
        return singleCrossingRays && ((x > ag[0] + 1.0f && x < ag[2] - 1.0f) ||
            (hasSecond && x > bg[0] + 1.0f && x < bg[2] - 1.0f));
    }

    void request(float x, float outer, float hint) {
        push(x, outer, hint, std::numeric_limits<float>::quiet_NaN());
    }

    // Asks whether the root on column x lies within kRefineTolerance of the prediction [expected];
    // [hint] seeds the exact search when the prediction cannot be verified.
    void requestDecision(float x, float outer, float hint, float expected) {
        push(x, outer, hint, expected);
    }

    void push(float x, float outer, float hint, float center) {
        bool single = singleCrossingRays, interior = centreInside(x);
        bool decidable = single, decisionInterior = interior;
        // Only a decision relies on the proven tail; the exact search keeps the plain bisection.
        if (nearlyCoaxial && !std::isnan(center)) decidable = decisionInterior = tailInside(x, outer);
        requests.push_back({x, outer, hint, center, single, interior, decidable, decisionInterior});
    }

    // With nearly common centres, a column ray is monotonic like a coaxial one until it reaches the
    // nearer capsule centre [m]; only the short tail [m, centerY] can rise again. Each capsule field
    // is largest at an end of that tail, and the union (a polynomial smooth minimum) never exceeds
    // either capsule field, so when one capsule is clearly negative at both ends the tail (centerY
    // included) is inside and the ray crosses the contour once.
    bool tailInside(float x, float outer) const {
        const float ca = (ag[1] + ag[3]) * 0.5f, cb = (bg[1] + bg[3]) * 0.5f;
        const float m = outer < centerY ? std::min(ca, cb) : std::max(ca, cb);
        return std::max(a.distance(x, m), a.distance(x, centerY)) <= -kNavigationTailMargin ||
               std::max(b.distance(x, m), b.distance(x, centerY)) <= -kNavigationTailMargin;
    }

    // Solves every pending request into [roots] (same order). Each root is the exact bisection
    // result, so the evaluation order is free: on AArch64 four searches advance together and
    // share one four-lane field evaluation, overlapping their dependency chains.
    void solveRequests() {
        const int count = static_cast<int>(requests.size());
        roots.resize(count);
        decided.resize(count);
#if defined(__aarch64__)
        struct Lane {
            RootSearch search;
            float x = 0.0f;
            int index = -1;
        };
        Lane lanes[4];
        int next = 0;
        auto fill = [&](Lane &lane) {
            while (next < count) {
                const RootRequest &r = requests[next];
                lane.index = next++;
                lane.x = r.x;
                if (startSearch(lane.search, r.outer, centerY, r)) return;
                roots[lane.index] = lane.search.result;
                decided[lane.index] = lane.search.decided;
            }
            lane.index = -1;
        };
        for (Lane &lane : lanes) fill(lane);
        float xs[4], ys[4], values[4];
        for (;;) {
            int live = -1;
            for (int k = 0; k < 4; ++k) if (lanes[k].index >= 0) { live = k; break; }
            if (live < 0) break;
            for (int k = 0; k < 4; ++k) {
                const Lane &lane = lanes[k].index >= 0 ? lanes[k] : lanes[live];
                xs[k] = lane.x;
                ys[k] = lane.search.probe;
            }
            vst1q_f32(values, distance4(vld1q_f32(xs), vld1q_f32(ys)));
            for (int k = 0; k < 4; ++k) {
                Lane &lane = lanes[k];
                if (lane.index < 0) continue;
                if (!lane.search.resume(values[k])) {
                    roots[lane.index] = lane.search.result;
                    decided[lane.index] = lane.search.decided;
                    fill(lane);
                }
            }
        }
#else
        for (int i = 0; i < count; ++i) {
            const RootRequest &r = requests[i];
            const float x = r.x;
            roots[i] = findRoot(r.outer, centerY, [this, x](float y) { return distance(x, y); }, r, &decided[i]);
        }
#endif
        requests.clear();
    }

    // Adaptive subdivision of both edges, breadth first: every level resolves the midpoints of
    // all open segments together, then the quarter points of the segments that still need them,
    // exactly as the depth-first rule does (mid, then quarter, then three-quarter, short-circuit).
    // Each segment is replaced in place by its halves, so the leaves keep the depth-first order.
    void subdivideAll(const float *outer) {
        for (;;) {
            pending.clear();
            for (int pass = 0; pass < 2; ++pass)
                for (int i = 0; i < static_cast<int>(segments[pass].size()); ++i)
                    if (!segments[pass][i].leaf) pending.push_back(pass * (1 << 24) + i);
            if (pending.empty()) return;
            auto at = [this](int id) -> Segment & { return segments[id >> 24][id & ((1 << 24) - 1)]; };
            const int count = static_cast<int>(pending.size());
            mids.assign(count, 0.0f);
            refine.assign(count, 0);
            exactMid.assign(count, 0);
            // A segment at the depth limit stays a leaf whatever its roots are.
            stage.clear();
            for (int k = 0; k < count; ++k) if (at(pending[k]).depth < 16) stage.push_back(k);
            for (int k : stage) {
                const Segment &s = at(pending[k]);
                const float x = (s.x0 + s.x1) * 0.5f;
                // A wide segment is always split at its midpoint root. Otherwise the chord predicts
                // the midpoint root, and only a failed prediction needs the root itself.
                if (std::abs(s.x1 - s.x0) > 8.0f) request(x, outer[pending[k] >> 24], (s.y0 + s.y1) * 0.5f);
                else requestDecision(x, outer[pending[k] >> 24], (s.y0 + s.y1) * 0.5f, (s.y0 + s.y1) * 0.5f);
            }
            solveRequests();
            survivors.clear();
            for (int j = 0; j < static_cast<int>(stage.size()); ++j) {
                const int k = stage[j];
                const Segment &s = at(pending[k]);
                if (decided[j]) { survivors.push_back(k); continue; }
                mids[k] = roots[j];
                exactMid[k] = 1;
                refine[k] = std::abs(s.x1 - s.x0) > 8.0f ||
                    std::abs(mids[k] - (s.y0 + s.y1) * 0.5f) > kRefineTolerance;
                if (!refine[k]) survivors.push_back(k);
            }
            stage.swap(survivors);
            for (int quarter = 1; quarter <= 3 && !stage.empty(); quarter += 2) {
                for (int k : stage) {
                    const Segment &s = at(pending[k]);
                    const float x = s.x0 + (s.x1 - s.x0) * (quarter == 1 ? 0.25f : 0.75f);
                    const float expected = s.y0 + (s.y1 - s.y0) * (quarter == 1 ? 0.25f : 0.75f);
                    // The exact search keeps its usual hint: the half chord through the midpoint root,
                    // or through the verified midpoint prediction.
                    const float mid = exactMid[k] ? mids[k] : (s.y0 + s.y1) * 0.5f;
                    const float hint = quarter == 1 ? (s.y0 + mid) * 0.5f : (mid + s.y1) * 0.5f;
                    requestDecision(x, outer[pending[k] >> 24], hint, expected);
                }
                solveRequests();
                survivors.clear();
                for (int j = 0; j < static_cast<int>(stage.size()); ++j) {
                    const int k = stage[j];
                    const Segment &s = at(pending[k]);
                    const float expected = s.y0 + (s.y1 - s.y0) * (quarter == 1 ? 0.25f : 0.75f);
                    if (!decided[j] && std::abs(roots[j] - expected) > kRefineTolerance) refine[k] = 1;
                    else survivors.push_back(k);
                }
                stage.swap(survivors);
            }
            // Split segments whose midpoint prediction held but a quarter's did not: the new vertex
            // needs the exact midpoint root.
            stage.clear();
            for (int k = 0; k < count; ++k) {
                if (!refine[k] || exactMid[k]) continue;
                const Segment &s = at(pending[k]);
                request((s.x0 + s.x1) * 0.5f, outer[pending[k] >> 24], (s.y0 + s.y1) * 0.5f);
                stage.push_back(k);
            }
            if (!stage.empty()) {
                solveRequests();
                for (int j = 0; j < static_cast<int>(stage.size()); ++j) mids[stage[j]] = roots[j];
            }
            for (int pass = 0; pass < 2; ++pass) {
                refined.clear();
                int k = 0;
                for (int i = 0; i < static_cast<int>(segments[pass].size()); ++i) {
                    const Segment &s = segments[pass][i];
                    if (s.leaf) { refined.push_back(s); continue; }
                    while (pending[k] != pass * (1 << 24) + i) ++k;
                    const float xm = (s.x0 + s.x1) * 0.5f;
                    if (s.depth < 16 && refine[k]) {
                        refined.push_back({s.x0, s.y0, xm, mids[k], s.depth + 1, false});
                        refined.push_back({xm, mids[k], s.x1, s.y1, s.depth + 1, false});
                    } else {
                        refined.push_back({s.x0, s.y0, s.x1, s.y1, s.depth, true});
                    }
                }
                segments[pass].swap(refined);
            }
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

        // Stop roots for both edges (no hint), plus the contour's start and end.
        const float outer[2] = {top, bottom};
        const int n = static_cast<int>(distinct.size());
        for (int pass = 0; pass < 2; ++pass)
            for (int i = 0; i < n; ++i) request(distinct[i], outer[pass], std::numeric_limits<float>::quiet_NaN());
        request(appendLeft, top, std::numeric_limits<float>::quiet_NaN());
        request(appendRight, bottom, std::numeric_limits<float>::quiet_NaN());
        solveRequests();
        const std::vector<float> stopRoots(roots.begin(), roots.end());
        auto stopRoot = [&](int pass, int i) { return stopRoots[pass * n + i]; };
        const float startY = stopRoots[2 * n], endY = stopRoots[2 * n + 1];

        segments[0].clear();
        segments[1].clear();
        for (int i = 0; i + 1 < n; ++i)
            segments[0].push_back({distinct[i], stopRoot(0, i), distinct[i + 1], stopRoot(0, i + 1), 0, false});
        for (int i = n; i-- > 1;)
            segments[1].push_back({distinct[i], stopRoot(1, i), distinct[i - 1], stopRoot(1, i - 1), 0, false});
        subdivideAll(outer);

        const size_t points = 2 + segments[0].size() + segments[1].size();
        contours.reserve(contours.size() + 1 + 2 * points);
        contours.push_back(static_cast<float>(points));
        auto point = [this](float x, float y) {
            contours.push_back(x);
            contours.push_back(y);
        };
        point(appendLeft, startY);
        for (const Segment &s : segments[0]) point(s.x1, s.y1);
        point(appendRight, endY);
        for (const Segment &s : segments[1]) point(s.x1, s.y1);
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

// Writes the contours (see NavigationUnionPath::contours) into [output] and returns the number of
// floats written, or minus the required length when [output] is too short, or 0 on invalid input.
extern "C" JNIEXPORT jint JNICALL
Java_io_github_neoruaa_glasmio_glass_LiquidMenuNativeField_buildNavigationPathNative(
    JNIEnv *env, jobject, jfloatArray geometry, jfloatArray output) {
    if (!geometry || !output || env->GetArrayLength(geometry) != 14) return 0;
    float g[14];
    env->GetFloatArrayRegion(geometry, 0, 14, g);
    if (env->ExceptionCheck()) return 0;
    for (float value : g) if (!std::isfinite(value)) return 0;
    NavigationUnionPath builder(g);
    builder.build();
    const jint count = static_cast<jint>(builder.contours.size());
    if (count > env->GetArrayLength(output)) return -count;
    env->SetFloatArrayRegion(output, 0, count, builder.contours.data());
    return env->ExceptionCheck() ? 0 : count;
}
#endif
