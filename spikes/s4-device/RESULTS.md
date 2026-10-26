# Spike S4 (phần thiết bị) — kết quả (2026-10-26)

Lệnh: `LevelGenRun 12345 WARRIOR 15 NONE` (sinh tầng 1–15, in DIGEST từng tầng), working dir = `core/src/main/assets`,
classpath = `sourceSets.test.runtimeClasspath` của `:core` (nhánh `claude/project-thread-2lxvfu` @ f4c160cde).

## Môi trường A53
OPPO A53 (CPH2127), Android 11, chroot Ubuntu 24.04 `/data/ubuntu`, aarch64, `nproc`=6,
RAM 7638 MB (lúc đo available ~4.9 GB, swap 4 GB), JDK Temurin 17.0.20.1 `/usr/local/jdk-17`.
Không có `/usr/bin/time` → wall time bằng `date +%s%N`, RSS tối đa bằng poll `VmHWM` trong `/proc/<pid>/status` mỗi 50 ms.
Bundle (classes + assets + 17 jar) 57,7 MB, để ở `/data/local/tmp/spdr`.

## Số liệu

| Lần chạy | Wall (ms) | Max RSS (kB) |
|---|---|---|
| A53 mặc định #1 | 2475 | 91 364 |
| A53 mặc định #2 | 2286 | 92 236 |
| A53 mặc định #3 | 2268 | 92 732 |
| A53 `-Xmx256m` #1 | 2287 | 91 696 |
| A53 `-Xmx256m` #2 | 2326 | 91 164 |
| A53 `-Xmx256m` #3 | 2350 | 92 244 |
| Windows MSI #1–#3 | 1841 / 1265 / 1430 | (không đo) |

Wall time tính cả khởi động JVM. `-Xmx256m` không ảnh hưởng (heap dùng thực tế nhỏ).

## Digest
- 6 lần chạy trên A53 cho output giống hệt nhau; 3 lần trên Windows cũng giống hệt nhau.
- A53 vs Windows: 14/15 tầng khớp; **tầng 6 khác**: Windows `79fd35503da4250c`, A53 `2d7bea1ee5f91336`.
  Tầng 11 khớp (`27a30ea21b1c18c0`) ở lần chạy này. Tầng 6/11 cũng là hai tầng lệch trong `:core:determinismTest`,
  nên khác biệt này có thể đến từ cùng nguồn bất định chứ chưa chắc do khác nền tảng — cần điều tra cùng S1.
