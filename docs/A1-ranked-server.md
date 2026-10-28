# A1: Ranked server (đợt 1)

Module mới `ranked-server/` (Java 11, không có libGDX backend). Chỉ dùng `core` cho luật kết thúc trận (`ranked/duel/MatchEnd`, A2) và định dạng seed pool (`ranked/seed/SeedPoolFile`, A4). Không sửa file gốc SPD, không đụng `Dungeon.java`. Chưa triển khai lên A53 (A7, cần hỏi user).

Chạy test: `./gradlew :ranked-server:test`. Chạy server: `./gradlew :ranked-server:run` (build fingerprint mặc định lấy từ core, có thể ghi đè bằng `-Dspdr.build`) (xem tham số ở `ServerMain`).

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
- ~~Vote đổi seed giữa trận và pause~~: đã làm (28/10), xem mục "Vote đổi seed và pause" bên dưới.
- ~~Xác minh bằng phát lại~~: đã làm ở A3 (`docs/A3-replay-verifier.md`), giao thức lên phiên bản 2 với frame `LOG`.
- Log lệnh theo đoạn và lệnh `CHOICE` (T0.5 mục 4), phía client (UI hàng chờ, gửi báo cáo), bảng xếp hạng, đổi mùa.
- Giới hạn tổng 90 phút vẫn là số tạm của A2.

## Test
`HubTest` (14 ca, đồng hồ giả): từ chối build lạ, tách nền tảng, thắng bằng hạ boss có đổi rating, chặn `CLEARED` sớm và tiến độ lùi, chết là thua, bỏ cuộc có strike, RESUME dưới 5 phút và thua khi quá, token của người khác, cả hai mất kết nối là void, hết giờ READY, khóa hàng chờ, seed dùng một lần, `Verifier` từ chối, khởi động lại thì void. `PartsTest`: codec, Glicko-2, ghép cặp, và một trận đầy đủ qua socket thật.

## Phía client (đợt 2)
Package `core/.../ranked/net/`, không đụng `Dungeon.java` (chỉ thêm một dòng ở `GameScene` và `HeroSelectScene`).

| Class | Việc |
|---|---|
| `NetFrame` | Bản client của `Frame`/`FrameCodec` (org.json, cùng định dạng dây) |
| `RankedClient` | Một kết nối: HELLO, PING 5 s, im lặng 15 s là đứt. Mất kết nối sau `START` thì tự nối lại (1, 2, 4 ... 10 s) trong cửa sổ 5 phút, `HELLO` + `RESUME`, rồi gửi lại báo cáo mới nhất. Không chạm trạng thái game |
| `ProgressTracker` | Từ trạng thái run ra `PROGRESS` / `DIED` / `CLEARED`, chỉ khi có thay đổi. Boss tính là đã hạ khi xuống sâu hơn tầng boss hoặc `Statistics.bossScores[i]` tăng (điểm boss chỉ tăng khi boss chết) |
| `RankedSession` | Chuyển sự kiện mạng về luồng render, bắt đầu run Ranked với seed trong `START` (slot riêng `MAX_SLOTS + 2`), gửi báo cáo sau 6 s đếm ngược |
| `WndRankedQueue` | Nút "Ranked duel" ở màn chọn nhân vật: kết nối, chờ, đối thủ + nút Accept (READY), Cancel |
| `RankedHud` | Dòng đối thủ trong game (tầng, boss, chết, mất kết nối), "đang nối lại", và kết quả + đổi rating |

Cấu hình (GameSettings): `ranked_server` (mặc định `127.0.0.1:7420`; ngoài localhost/LAN thì dùng TLS), `ranked_name`, `ranked_device_key` (tự sinh, L2). Build fingerprint là `spdr-` + 16 ký tự hex đầu của SHA-256 trên các file `.class` đã biên dịch của core (đường dẫn sắp xếp + nội dung), do task `:core:rankedFingerprint` ghi vào resource `ranked/build-fingerprint.txt`. Game đọc resource đó (có trong APK), server đọc cùng resource từ jar core nên cùng commit thì tự khớp. Thiếu resource (chạy từ IDE không qua Gradle) thì quay về `version+versionCode`.

Test: `ProgressTrackerTest` (core) và `ClientInteropTest` (ranked-server): `RankedClient` thật đấu với `Server` thật qua socket cục bộ, cả trận có rớt mạng giữa chừng rồi RESUME, và từ chối build lạ.

## Đợt 3
- **Bỏ cuộc**: nút Resign dưới dòng đối thủ khi trận đang chạy, hỏi lại trước (thua và tính strike).
- **Vào lại sau khi tắt app**: khi `START`, `RankedSession` lưu `ranked_resume` (matchId, token, đối thủ, tầng boss cuối, `seen` cập nhật mỗi 10 s) vào GameSettings; xóa khi có `RESULT` hoặc server báo không còn gì để resume. Nếu còn trong 5 phút tính từ `seen` và slot ranked còn save, nút ở màn chọn nhân vật thành "Rejoin ranked duel": `RankedClient.resume` nối lại với cửa sổ tính từ `seen`, khi `RESUMED` thì tải run từ slot như Continue. Log lệnh A3 lưu cùng save và quay về khi tải lại, rồi được gửi lại từ đầu (server giữ phần còn thiếu); client lưu game trước mỗi `LOG` nên save không bao giờ cũ hơn log server đã có (27/10, xem `docs/T0.8-deterministic-core.md`).
- Test thêm trong `ClientInteropTest`: client mới vào lại bằng token đã lưu rồi bỏ cuộc (thua/thắng đúng), token cũ bị từ chối, core có fingerprint đúng dạng.

Chưa làm: chưa thử trên Android thật (APK debug build được và có fingerprint, nhưng lúc làm không có máy nào cắm adb).

## Vote đổi seed và pause (L6, L19, chốt 28/10)
Giao thức lên phiên bản 3.

**Vote đổi seed** (mọi chế độ Duel, boss cuối 5, 10 hay 15):
- Client gửi `VOTE_REROLL` (`accept` mặc định true). Server báo cả hai bằng `REROLL_VOTE {mine, theirs, voteMs, ended?}`.
- Chỉ vote được khi cả hai còn ở tầng 5 trở lên (`rerollMaxDepth`), còn sống, chưa hạ boss cuối, và đối thủ đang kết nối. Một người xuống tầng 6 thì vote đang mở kết thúc (`TOO_DEEP`).
- Người kia đồng ý trong 30 s (`rerollVoteMs`) thì đổi seed; `accept: false` là từ chối (`DECLINED`), quá 30 s là `EXPIRED`, một người rời là `LEFT`.
- Mỗi trận đổi được 1 lần (`rerollsPerMatch`). Đây là con số tự chọn, user chưa nói; đổi trong `Hub.Config`.
- Khi đổi: server lấy seed mới từ pool (`Storage.rerollMatch`, cùng transaction cập nhật `match.seed`), xóa log lệnh và tiến độ của cả hai, gửi lại `START {seed, t0, reroll}` với đếm ngược mới. Client chơi lại từ tầng 1.
- Seed cũ vẫn đánh dấu thuộc trận này và **không** trả về pool: cả hai đã thấy nó. Pool hết seed thì vote kết thúc với `POOL_EMPTY`, giữ seed cũ.

**Pause 60 s** (`pauseBudgetMs`, mỗi người mỗi trận):
- `PAUSE` / `UNPAUSE`; server báo cả hai bằng `PAUSED {mine, paused, leftMs}`. Hết ngân sách thì server tự kết thúc pause.
- Pause chỉ dừng **đồng hồ của người pause**; đồng hồ của đối thủ vẫn chạy. `MatchEnd.Player.pausedMs` giữ phần thời gian đã pause; đồng hồ riêng = thời gian trận − `pausedMs`.
- Thời điểm hạ boss và lúc chết tính theo đồng hồ riêng. Vì vậy một lần hạ boss chỉ thắng khi đồng hồ riêng của đối thủ đã vượt mốc đó (hoặc đối thủ đã chết); trước đó đối thủ vẫn có thể hạ boss sớm hơn theo đồng hồ của mình.
- Hết giờ (90 phút) xét khi cả hai đồng hồ riêng đều hết. Người hết giờ trước thì báo cáo sau đó bị từ chối (`time is up`) và chờ người kia.
- Trong lúc pause, `LOG` và báo cáo bị từ chối. Client mở cửa sổ pause (chặn nhân vật) và chỉ cho pause khi nhân vật đang chờ lệnh. Thời gian pause đã dùng vẫn tính sau khi đổi seed.
- Mất kết nối vẫn tính theo thời gian trận như cũ (5 phút).

Test: `HubTest` thêm 9 ca (vote cần hai người và chơi lại từ đầu, đóng sau tầng 5, vote kết thúc khi xuống sâu, từ chối và hết hạn, pool rỗng, pause chỉ dừng đồng hồ người pause, hết ngân sách, chờ đồng hồ còn lại khi hạ boss, hết giờ chờ cả hai); `MatchEndTest` thêm 3 ca đồng hồ riêng; `ClientInteropTest.rerollAndPause` chạy qua `RankedClient` thật.
