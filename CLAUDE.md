# CLAUDE.md — spd-ranked

## Mục tiêu
Bản fork của Shattered Pixel Dungeon (SPD), làm lại sạch từ đầu trên nền upstream mới nhất,
vẫn nhận được bản cập nhật từ upstream. Hai mảng chính:
1. **Ranked Duel** 1v1 có server riêng (matchmaking, rating theo mùa, chống gian lận, seed pool chấm điểm,
   ghost/replay/telemetry, Pack & Skin có denylist khi đấu ranked). Duel là cuộc đua song song từ tầng 1 lên boss tầng 5/10/15, ai hạ boss cuối trước thắng; Boss Practice (vào thẳng boss bằng kit, chơi đơn) là chế độ riêng.
   Server chạy 24/7 trên OPPO A53 đã root (chroot Ubuntu).
2. **Learning / QoL**: giúp người chơi hiểu và học (Death Analysis, Known Item Recognition, Food Forecast…),
   không làm nhân vật mạnh hơn.

Danh sách tính năng, nguyên tắc và thứ tự làm: [`docs/ROADMAP.md`](docs/ROADMAP.md).
Nghiên cứu và ý tưởng gốc: [`docs/ideas/research-va-y-tuong-da-chon.md`](docs/ideas/research-va-y-tuong-da-chon.md).

## Repo và remote
- `origin`   = https://github.com/suanlong06/shattered-pixel-dungeon-ranked (repo của mình)
- `upstream` = https://github.com/00-evan/shattered-pixel-dungeon (chỉ fetch; push đã bị tắt)
- Nền hiện tại: SPD v4.0.2 (`57a4e06a4`).

## Repo cũ — CHỈ THAM KHẢO
- `C:\Workspace\20_Projects\Shattered pixel dungeon Ranked!` là bản cũ (dựa trên SPD v3.3.8 + fork versus-pixel-dungeon).
- Không sửa, không commit, không build trong repo cũ. Chỉ đọc để hiểu tính năng/thiết kế
  (ví dụ `TECHNICAL_DESIGN.md`, `docs/`).
- Không copy hay cherry-pick nguyên commit: code nền đã khác (v3.3.8 → v4.0.2). Viết lại từng tính năng trên nền mới.
- Quyết định/thiết kế cũ có thể đã lỗi thời; xác nhận lại với chủ repo trước khi áp dụng.
- Bản sao lưu repo cũ: `C:\Workspace\20_Projects\_backup-ranked-old\`.

## Quy tắc nhánh
- `master` = upstream + các tính năng đã hoàn thành. Không commit thẳng việc dở dang lên `master`.
- Mỗi tính năng một nhánh `feature/<tên>`, merge vào `master` khi xong.

## Nhận cập nhật từ upstream
```bash
git fetch upstream
git checkout master
git merge upstream/master
git push origin master
# sau đó merge master vào các nhánh feature/* đang làm
```

## Quy ước code để dễ merge upstream
- Hạn chế sửa trực tiếp file gốc của SPD; ưu tiên thêm class/package mới và chỉ móc (hook) vài dòng vào code gốc.
- Khi buộc phải sửa file gốc, giữ thay đổi nhỏ và đánh dấu bằng comment (ví dụ `// spd-ranked:`) để dễ tìm khi conflict.

## Nguyên tắc
- Ranked và solo tách biệt; mọi tính năng hỗ trợ có cờ bật/tắt, trong ranked hai người chơi có cùng bộ tính năng.
- Tính năng hiển thị thông tin không được thay đổi RNG/kết quả mô phỏng (giữ replay và digest khớp).
- Mỗi tính năng có test trước khi merge vào `master`.

## Build và chạy (desktop)
```bash
./gradlew desktop:debug     # chạy game
./gradlew desktop:release   # tạo desktop/build/libs/desktop-<version>.jar
```
Cần JDK 17. Xem thêm `docs/getting-started-desktop.md`.
