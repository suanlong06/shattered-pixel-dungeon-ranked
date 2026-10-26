#!/usr/bin/env bash
# Spike S3: chạy S3Math trên JVM máy này và trên ART của thiết bị qua adb, rồi so sánh.
# Cần: JDK 17, Android SDK (build-tools có d8, platforms/android-*), adb, thiết bị đã cắm,
# và SPD-classes đã build (./gradlew :SPD-classes:jar).
set -euo pipefail
export MSYS_NO_PATHCONV=1
cd "$(dirname "$0")"
ROOT=../..
SDK=${ANDROID_HOME:-$LOCALAPPDATA/Android/Sdk}
D8=$(ls -d "$SDK"/build-tools/*/ | tail -1)d8.bat
ANDROID_JAR=$(ls "$SDK"/platforms/*/android.jar | tail -1)
ADB=$SDK/platform-tools/adb.exe
SPD_JAR=$ROOT/SPD-classes/build/libs/SPD-classes-4.0.2.jar
OUT=build

rm -rf $OUT && mkdir -p $OUT/classes
javac -encoding UTF-8 --release 11 -cp "$SPD_JAR" -d $OUT/classes src/*.java
java -cp "$OUT/classes;$SPD_JAR" S3Math spd | tr -d '\r' > $OUT/jvm.txt

(cd $OUT/classes && jar cf ../s3.jar *.class)
"$D8" --release --min-api 30 --lib "$ANDROID_JAR" --output $OUT $OUT/s3.jar "$SPD_JAR" 2>&1 | grep -v '^Warning' || true
(cd $OUT && jar cf s3-dex.jar classes.dex)
"$ADB" push $OUT/s3-dex.jar /data/local/tmp/s3-dex.jar >/dev/null
"$ADB" shell "dalvikvm -cp /data/local/tmp/s3-dex.jar S3Math spd" | tr -d '\r' > $OUT/art.txt

wc -l $OUT/jvm.txt $OUT/art.txt
# Đếm dòng khác nhau theo nhóm + hàm
paste $OUT/jvm.txt $OUT/art.txt | awk -F'\t' '{k=$1"\t"$2; n[k]++; if ($5!=$10 || $4!=$9) d[k]++} END {for (k in n) printf "%s\t%d/%d\n", k, d[k]+0, n[k]}' | sort
