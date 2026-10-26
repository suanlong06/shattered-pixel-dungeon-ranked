# Spike S3 — kết quả (2026-10-26)

So sánh bit-chính-xác output của `S3Math spd` (68.144 dòng) trên ba nền:

| Nền | Chi tiết |
|---|---|
| JVM Windows | Temurin 17.0.20.1, x86_64, MSI |
| ART | OPPO A53 (CPH2127), Android 11 / API 30, arm64, `dalvikvm` từ dex (d8 34.0.0, min-api 30) |
| JVM A53 | Temurin 17.0.20.1 aarch64 trong chroot Ubuntu 24.04 `/data/ubuntu` |

Input: 2000 double cố định (biên, ±0, ±Inf, NaN, subnormal, số lớn, pattern splitmix64);
RNG: 8 seed × 64 lần rút cho mỗi hàm.

## RNG — khớp 100% trên cả ba nền
`java.util.Random` (nextInt, nextInt(bound), nextLong, nextFloat, nextDouble, nextGaussian) và
`com.watabou.utils.Random` (Int, Int(max), Long, Float, NormalIntRange, NormalFloat): 0 dòng khác ở mọi cặp.

## StrictMath — khớp bit trên cả ba nền (trừ bit pattern của NaN)
Chỉ khác ở NaN: JVM Windows trả `fff8000000000000`, ART và JVM A53 trả `7ff8000000000000`
(acos/asin 1466, sqrt/log 999, pow 629, sin/cos/tan 2 dòng). Vẫn là NaN, `==`/`isNaN` cho kết quả như nhau.

## Math — lệch thật (thường 1 ulp), số dòng khác /2000 (không tính NaN-only)

| Hàm | Win vs ART | Win vs JVM A53 | ART vs JVM A53 |
|---|---|---|---|
| sin | 58 | 126 | 96 |
| cos | 56 | 153 | 119 |
| tan | 63 | 63 | 0 |
| atan2 | 328 | 0 | 328 |
| cbrt | 162 | 0 | 162 |
| exp | 1 | 62 | 63 |
| log | 0 | 31 | 31 |
| pow | 0 | 22 | 22 |
| atan, floor, round, round(float), hypot, sqrt, asin, acos | 0 | 0 | 0 |

## Kết luận
- RNG của SPD (và `java.util.Random`) cho cùng chuỗi trên HotSpot x86_64, HotSpot aarch64 và ART.
- `Math.*` siêu việt **không** tất định giữa nền tảng — kể cả giữa hai bản HotSpot cùng phiên bản khác kiến trúc.
  Code ảnh hưởng mô phỏng (server tính kết quả, replay, digest) phải dùng `StrictMath` hoặc tránh sin/cos/tan/atan2/cbrt/exp/log/pow.
- Không băm raw bits của NaN vào digest (chuẩn hoá bằng `Double.doubleToLongBits`).

Chạy lại: `./gradlew :SPD-classes:jar` rồi `bash spikes/s3-math/run.sh` (JVM Windows vs ART).
