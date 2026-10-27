# A1: Ranked server (đợt 1)

Module mới `ranked-server/` (Java 11, không có libGDX backend). Chỉ dùng `core` cho luật kết thúc trận (`ranked/duel/MatchEnd`, A2) và định dạng seed pool (`ranked/seed/SeedPoolFile`, A4). Không sửa file gốc SPD, không đụng `Dungeon.java`. Chưa triển khai lên A53 (A7, cần hỏi user).

Chạy test: `./gradlew :ranked-server:test`. Chạy server: `./gradlew :ranked-server:run -Dspdr.build=<fingerprint>` (xem tham số ở `ServerMain`).

## Thành phần
| Class | Việc |
|---|---|
| `Frame`, `FrameCodec` | Giao thức phiên bản 1: `[4 byte BE][UTF-8 JSON]`, `{type, seq, matchId, data}`, tối đa 64 KB một frame |
| `Hub` | Toàn bộ logic, không phụ thuộc socket; mọi lời gọi nhận giờ server nên test dùng đồng hồ giả |
| `Matchmaker` | Hàng chờ tách theo nền tảng (L3). Cửa sổ chênh rating 100, nới 10/giây, tối đa 500; người chờ lâu nhất được ghép với người gần rating nhất |
| `Glicko2` | Glicko-2 chuẩn, mỗi trận là một kỳ rating |
| `Storage` | SQLite (WAL), schema theo `PRAGMA user_version`: player (khóa thiết bị + tên, L2), rating theo mùa và nền tảng, seed (dùng một lần), match, strike |
| `Server`, `ServerMain` | Socket một luồng mỗi kết nối, TLS khi có keystore PKCS12; im lặng 15 s (3 lần PING 5 s) là đứt |

## Vòng đời trận
`QUEUE` → ghép cặp → `MATCH_FOUND` (tên, rating đối thủ, resume token) → cả hai `READY` → `START` (seed và `t0`; seed bí mật tới đây, L18) → đếm ngược → chạy. Client gửi `PROGRESS` / `DIED` / `CLEARED` / `RESIGN`; server gọi `MatchEnd.judge` mỗi báo cáo và mỗi giây, rồi gửi `RESULT`.

- Đối thủ chỉ thấy tầng, số boss, đã chết, còn kết nối (`OPPONENT`), không thấy bản đồ hay đồ.
- Tiến độ chỉ được tăng; `CLEARED` khi chưa tới tầng boss cuối bị từ chối.
- Mất kết nối: dưới 5 phút thì `HELLO` lại rồi `RESUME` bằng token (token của người khác bị từ chối); quá 5 phút thì thua (A2).
- Void, không tính rating: cả hai mất kết nối, rời trận trước khi bắt đầu, hết 60 s chờ `READY`, server khởi động lại giữa trận, hoặc `Verifier` từ chối.
- Strike: bỏ cuộc, thua vì mất kết nối, rời trước khi bắt đầu, không `READY`. Đủ 3 strike trong 24 giờ thì khóa hàng chờ.
- Handshake từ chối khác phiên bản giao thức hoặc khác build fingerprint (phát lại chỉ đúng trên cùng bytecode).

## Cố ý chưa làm
- **Vote đổi seed giữa trận** và **pause 180 s**: user chưa chốt (L6/L19). Nếu cần sẽ là thêm loại frame và một trường trong `Hub.Match`, không đổi phần còn lại.
- ~~Xác minh bằng phát lại~~: đã làm ở A3 (`docs/A3-replay-verifier.md`), giao thức lên phiên bản 2 với frame `LOG`.
- Log lệnh theo đoạn và lệnh `CHOICE` (T0.5 mục 4), phía client (UI hàng chờ, gửi báo cáo), bảng xếp hạng, đổi mùa.
- Giới hạn tổng 90 phút vẫn là số tạm của A2.

## Test
`HubTest` (14 ca, đồng hồ giả): từ chối build lạ, tách nền tảng, thắng bằng hạ boss có đổi rating, chặn `CLEARED` sớm và tiến độ lùi, chết là thua, bỏ cuộc có strike, RESUME dưới 5 phút và thua khi quá, token của người khác, cả hai mất kết nối là void, hết giờ READY, khóa hàng chờ, seed dùng một lần, `Verifier` từ chối, khởi động lại thì void. `PartsTest`: codec, Glicko-2, ghép cặp, và một trận đầy đủ qua socket thật.
