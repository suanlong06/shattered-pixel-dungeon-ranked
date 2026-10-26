# Spike S1: sinh tầng 1 đến 15 có tất định không (26/10)

Chạy thật trên SPD v4.0.2 chưa sửa, headless (libGDX headless backend, không GL), JDK 21 local; CI dùng JDK 17.
Test: `core/src/test/.../ranked/test/LevelGenDeterminismTest.java`, chạy bằng `./gradlew :core:determinismTest`.

## Cách đo
- `Dungeon.init()` rồi `Dungeon.newLevel()` lần lượt tầng 1 đến 15 (giống đường debug của `InterlevelScene.descend`).
- Digest mỗi tầng = SHA-256 của JSON chuẩn hóa từ `Level.storeInBundle` (sắp xếp khóa; `mobs`, `heaps`, `blobs`, `plants`, `traps` sắp theo nội dung vì lưu từ tập không thứ tự).
  Bỏ `id` (đếm actor, không phải nội dung tầng) và `set_id` (xem phát hiện 4).
- So: hai lần trong cùng JVM; JVM con mới; JVM con với `-XX:hashCode=2` (mọi identity hash bằng nhau, lộ mọi chỗ duyệt set/map theo identity).
- 5 seed x 2 class (Warrior, Cleric).

## Kết quả

| # | Phát hiện | Nguồn | Mức |
|---|---|---|---|
| 1 | Hồ sơ mới (intro bật, chưa có trang guide): tầng 1 và 2 **khác nhau mỗi lần chạy**. Trang guide đặt bằng `Random.pushGenerator()` không seed, vị trí của nó làm lệch cả trang trí/bẫy của tầng | `EntranceRoom.placeEarlyGuidePages` | Chặn ranked; T0.7 (hồ sơ sạch) xử lý |
| 2 | Túi bán ở shop tầng 6, 11 đổi giữa các lần chạy ngay trong một JVM: `HashMap<Bag,Integer>` theo identity hash, lúc hòa (kho rỗng) thì phụ thuộc thứ tự duyệt | `ShopRoom.ChooseBag` | Lệch thật |
| 3 | Phòng bí mật Laboratory/Library: loại bình/scroll đổi **giữa các JVM** (`HashMap<Class,Float>` + `Random.chances`). Xác nhận F16 | `SecretLaboratoryRoom`, `SecretLibraryRoom` | Lệch thật |
| 4 | `MissileWeapon.setID = new SecureRandom().nextLong()`: token gộp chồng, không ảnh hưởng nội dung nhưng làm save/digest khác | `MissileWeapon.java:78` | Cần sửa cho digest/replay |
| 5 | **F14 sai một điểm**: loot của **Thief** (tầng 6 đến 10) là `RING` hoặc `ARTIFACT`; `randomUsingDefaults(ARTIFACT)` vẫn rút **bộ bài artifact dùng chung với sinh tầng**. Giết Thief rơi artifact làm đổi artifact ở tầng sau (đo được: seed 987654321 đổi tầng 11) | `Thief.java:52`, `Generator.randomUsingDefaults` | Thêm vào danh sách F19 |
| 6 | Cũng rút bộ bài ARTIFACT lúc chơi: Cursed Wand (biến đổi item) | `CursedWand.java:1175` | Thêm vào F19 |
| 7 | Xác nhận F19: Ring of Wealth, Transmutation đổi đúng một tầng sau; cầm Exotic Crystals đổi gần như **mọi** tầng | đo | Như F19 |
| 8 | Xác nhận F14 phần còn lại: loot quái khác (không phải Thief) và rút hạt/đá từ cỏ **không** đổi tầng sau | đo, 5 seed | Đúng |

Sau khi vá thử 3 file (đổi 4 `HashMap` sang `LinkedHashMap`, xem `S1-levelgen-fix.patch`, **chưa đưa vào code**), với hồ sơ "cựu"
(intro tắt, đủ trang guide) cả 10 tổ hợp seed/class x 15 tầng khớp tuyệt đối giữa 4 lần chạy, kể cả `-XX:hashCode=2`.

## Ý nghĩa cho chi phí
- **Phần sinh tầng rẻ hơn ước tính nhiều**: 3 file, 4 dòng, cộng việc của T0.7 (seed trang guide hoặc hồ sơ sạch, chặn 4 nguồn rút bộ bài ARTIFACT, Exotic Crystals).
- S1 **không** đo phần chiến đấu/lượt (RNG gameplay không seed, Actor `HashSet`, RNG hiệu ứng). Đó là phần lớn của ước tính 150 file; S2 và S6 mới đo được.
- Chạy headless không cần GL cho sinh tầng: không phải sửa sprite cho phần này.
