# A4 Seed pool có chấm điểm (đợt 1)

Code ở `ranked/seed`, không hook vào file gốc SPD, không đụng `Dungeon.java`. Test: `SeedPoolTest` (headless).

## Spike "cùng seed, cùng đồ" (T-seed)
Đã có sẵn từ T0.6/T0.7, không cần spike mới:
- `LevelGenDeterminismTest.mobLootAndGrassDoNotChangeLaterFloors`: loot quái và giẫm cỏ không đổi tầng sau (F14, đã chạy thật).
- `RankedCleanModeTest.rankedFloorsIgnoreProfileAndPlay`: trong Ranked, hồ sơ và các ngoại lệ (Thief, Ring of Wealth, Transmutation, Cursed Wand, Exotic Crystals) không đổi digest tầng 1 đến 15.
- Mới: `SeedPoolTest` xác nhận đồ trên sàn tầng 1 đến 5 **không phụ thuộc class** (quét với cả 6 class ra cùng danh sách), nên quét một lần rồi chấm cho từng class.

## Đã làm
| Phần | Nội dung |
|---|---|
| `SeedScanner` | Sinh tầng 1 đến N theo thứ tự ở chế độ Ranked (hồ sơ sạch), liệt kê item trong các heap (kể cả rương, hài cốt) theo thứ tự ô. Không tính đồ rơi từ quái lúc chơi. Là công cụ cho server/offline: nó bắt đầu run mới nên không gọi khi đang chơi. |
| `SeedScorer` | Theo mặc định L18: chấm từng class, seed được đánh giá bằng class thấp nhất (`MIN = 18`) và có trần cho class cao nhất (`CAP = 36`). Điểm gồm sống sót (bình máu, Ankh, thoát thân...) và tốc độ (Magic Mapping, Teleportation, Haste...). Đồ trang bị tính khi không bị nguyền: wand, ring, artifact, vũ khí/giáp tốt hơn đồ khởi đầu, có cộng nửa điểm cho class dùng tốt (Mage wand, Rogue ring/artifact, Huntress vũ khí ném...). Scroll of Upgrade và Potion of Strength không tính vì seed nào cũng có số lượng như nhau. Bảng điểm sửa trong `TABLE`, không đụng luật. |
| `SeedScorer.pool(candidates)` | Lọc danh sách seed ứng viên; việc chọn ứng viên và giữ bí mật seed là việc của server (L18: bí mật tới lúc bắt đầu trận, dùng một lần). |

Ngưỡng hiện tại lấy từ 30 seed mẫu (điểm 11 đến 44, chênh giữa các class 0 đến 7): khoảng 23/30 seed lọt pool. Số liệu in ra ở test `poolFiltersSomeSeeds`.

## Còn để sau
- Chỉnh bảng điểm và ngưỡng khi có dữ liệu chơi thật (bot S5 chưa qua được boss).
- Định dạng file pool và nơi lưu phía server (gắn với A1).
- Tính cả đồ trong shop nếu mở rộng ra tầng 6 trở lên.
