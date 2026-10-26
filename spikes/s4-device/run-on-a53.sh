# Chạy trong Android root shell: vào chroot, chạy LevelGenRun và đo wall time + VmHWM (RSS tối đa).
chroot /data/ubuntu /usr/bin/env -i PATH=/usr/local/jdk-17/bin:/usr/bin:/bin HOME=/root /bin/bash -c '
cd /mnt/android-tmp/spdr/assets
CP=../test-classes:../main-classes:../main-res:$(ls ../lib/*.jar | paste -sd:)
run() {
  tag=$1; shift
  s=$(date +%s%N)
  java "$@" -cp "$CP" com.shatteredpixel.shatteredpixeldungeon.ranked.test.LevelGenRun 12345 WARRIOR 15 NONE > ../out-$tag.txt 2>&1 &
  pid=$!; hwm=0
  while kill -0 $pid 2>/dev/null; do
    v=$(awk "/VmHWM/{print \$2}" /proc/$pid/status 2>/dev/null); [ -n "$v" ] && hwm=$v
    sleep 0.05
  done
  wait $pid; rc=$?
  e=$(date +%s%N)
  echo "$tag rc=$rc wall_ms=$(( (e-s)/1000000 )) maxRSS_kB=$hwm md5=$(md5sum < ../out-$tag.txt | cut -c1-32)"
}
run def1; run def2; run def3
run x256a -Xmx256m; run x256b -Xmx256m; run x256c -Xmx256m
'
