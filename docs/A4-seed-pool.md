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

## Đợt 2
- `SeedPoolFile`: file pool dạng text, mỗi dòng `seed min max`; dòng đầu là comment ghi phiên bản và ngưỡng đã dùng (`MIN`, `CAP`, độ sâu) để biết pool nào được tạo bằng ngưỡng cũ.
- `SeedPoolMain` (test sources, như `GoldenMain`): quét `count` ứng viên `first + i*step` và ghi file pool. Test kiểm tra ghi rồi đọc lại ra đúng danh sách seed.

## Đợt 3: lưu pool trên server
- Server nạp file pool qua `-Dspdr.seedpool=<file>` mỗi lần khởi động vào bảng `seed` (SQLite, schema 3). Mỗi seed giữ điểm `min`/`max`, dòng header của pool (ngưỡng đã dùng), thời điểm nạp, trận đã dùng và thời điểm dùng. Nạp lại cùng file không đổi trạng thái seed đã dùng.
- Seed phát theo đúng thứ tự trong file (cột `pos`; trước đây vô tình theo thứ tự số vì `seed` là rowid).
- `Storage.startMatch` lấy seed và tạo trận trong cùng một transaction: không có trận nào thiếu seed, không seed nào dùng cho hai trận, kể cả khi lỗi giữa chừng.
- Header khác ngưỡng hiện tại thì in cảnh báo. `-Dspdr.retireStalePools=true` ngừng phát các seed chưa dùng của pool cũ (seed đã dùng vẫn giữ làm hồ sơ trận).
- Khởi động in số seed còn lại và cảnh báo khi pool rỗng.
- Test: `SeedStoreTest` (thứ tự file, dùng một lần, rollback khi tạo trận lỗi, pool rỗng, giữ trạng thái qua khởi động lại, loại pool cũ, nâng cấp DB schema 2).
- Trận bị hủy trước `START` (hết giờ READY, rời trận, server khởi động lại) trả seed lại pool vì seed chưa lộ cho ai (user chốt 2026-10-28). Cột `match.started` (schema 4) ghi lúc gửi `START`, ghi trước khi gửi; seed của trận đã `START` không bao giờ quay lại pool. Trận cũ trước schema 4 coi như đã `START`.

## Còn để sau
- Chỉnh bảng điểm và ngưỡng khi có dữ liệu chơi thật (bot S5 chưa qua được boss).
- Tính cả đồ trong shop nếu mở rộng ra tầng 6 trở lên.
