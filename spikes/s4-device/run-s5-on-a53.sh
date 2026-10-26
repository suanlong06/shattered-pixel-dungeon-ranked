chroot /data/ubuntu /usr/bin/env -i PATH=/usr/local/jdk-17/bin:/usr/bin:/bin HOME=/root /bin/bash -c '
cd /mnt/android-tmp/spdr/assets
CP=../test-classes:../main-classes:../main-res:$(ls ../lib/*.jar | paste -sd:)
for seed in 12345 987654321 42; do
  s=$(date +%s%N)
  java -cp "$CP" com.shatteredpixel.shatteredpixeldungeon.ranked.test.S5Main $seed DUELIST 20000 15 400 > ../s5-$seed.txt 2>&1 &
  pid=$!; hwm=0
  while kill -0 $pid 2>/dev/null; do v=$(awk "/VmHWM/{print \$2}" /proc/$pid/status 2>/dev/null); [ -n "$v" ] && hwm=$v; sleep 0.1; done
  wait $pid; e=$(date +%s%N)
  echo "seed=$seed wall_ms=$(( (e-s)/1000000 )) maxRSS_kB=$hwm $(grep -E "^RESULT" ../s5-$seed.txt | grep -o "heroTurns=[0-9]*\|actorSteps=[0-9]*\|genMs=[0-9]*\| simMs=[0-9]*\|usPerHeroTurn=[0-9.]*\|usPerActorStep=[0-9.]*" | tr "\n" " ") $(grep TRACE ../s5-$seed.txt) $(grep -m1 Exception ../s5-$seed.txt)"
done
'
