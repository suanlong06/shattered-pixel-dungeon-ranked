# A7: Triển khai server Ranked lên OPPO A53 (đợt 1, 28/10)

Server chạy trong chroot Ubuntu `/data/ubuntu` (JDK 17 `/usr/local/jdk-17`), thư mục riêng `/root/spdr-ranked`.
Không đụng bản server cũ (`/root/ranked`, lệnh `a53-ranked`, thiết kế trước, đang STOPPED).

## Cài / cập nhật (từ PC, adb đã cắm)
1. `ANDROID_HOME=<sdk> ./gradlew :ranked-server:installDist` (cùng commit với APK để build fingerprint khớp).
2. Seed pool (bí mật, không commit): chạy `SeedPoolMain <first> <count> <step> <file>` với classpath test của core,
   working dir `core/src/main/assets`. Lần này: 3000 ứng viên → 2138 seed, ~10 s trên PC.
3. Đẩy lên máy: `adb push ranked-server/build/install/ranked-server/lib`, seed pool và
   `ranked-server/deploy/spdr-ranked` (đổi sang LF) vào `/data/local/tmp/spdr-ranked/`.
4. Trong chroot (chạy bằng một file script, không gửi lệnh ghép inline qua adb): chép `lib` và `seedpool.txt`
   (chmod 600, xóa bản trong `/data/local/tmp`) vào `/root/spdr-ranked`, cài `spdr-ranked` vào `/usr/local/bin`, rồi `spdr-ranked start`.

Điều khiển: `su -c "chroot /data/ubuntu /usr/local/bin/spdr-ranked start|stop|restart|status|logs"`.
Cổng 7420, DB `/root/spdr-ranked/ranked.db`, log `/var/log/spdr-ranked.log`, `-Xmx384m`.

## Đã đo / đã thử
- Khởi động: nạp 2138 seed, lắng nghe 7420 trong khoảng 1 s; RSS ~58 MB lúc rảnh, ~62 MB sau một trận.
- Tiến trình sống sau khi thoát adb shell.
- Client Android (APK debug, ART) trên chính A53 nối `127.0.0.1:7420`; người chơi thứ hai là bot `RankedClient`
  (nền tảng android) chạy trên PC nối qua Wi-Fi `192.168.0.8:7420`. Ghép cặp → Accept → `START` → GameScene Ranked
  có dòng đối thủ và nút Resign → bot bỏ cuộc → client hiện "You won the duel. Rating 1500 -> 1662", bot nhận LOSS 1338.
- GameScene thường (không Ranked) cũng chạy trên ART: vào tầng 1, đi lại, lưu và hiện lại ở "Các lượt chơi đang diễn ra".

## Chưa làm (cần user quyết)
- **Tự khởi động khi máy reboot / tự hồi sinh khi chết**: máy đã có supervisor và boot hook riêng (`a53-supervise`, Magisk);
  chưa gắn server mới vào đó để không động tới hạ tầng đang chạy.
- **TLS**: đang TCP thường, nghe 0.0.0.0 nên ai trong LAN cũng nối được. Ra Internet (Tailscale/VPS, L1) thì cần keystore PKCS12 (`spdr.keystore`).
- **Sao lưu DB** theo `T0.5` (WAL, không "dừng rồi chép").

## Ghi chú khi thử bằng adb
- Màn chọn nhân vật lần đầu ẩn nút tùy chọn (intro); đặt `intro=false` trong `shared_prefs/ShatteredPixelDungeon.xml` qua `run-as` để hiện nút "Ranked duel".
- ColorOS đôi khi đóng băng app khi màn hình tắt (`ColorHansManager ... Freeze`); khởi động lại app là chạy tiếp.
- `deviceKey` phải dài ít nhất 16 ký tự, nếu không server trả `bad deviceKey or name`.
