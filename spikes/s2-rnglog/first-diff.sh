# first divergence between two RNG logs: prints line no and 3 lines of context each
a=$1; b=$2
n=$(cmp "$a" "$b" 2>/dev/null | awk '{print $NF}' | tr -d ,)
[ -z "$n" ] && { echo "IDENTICAL ($(wc -l < $a) draws)"; exit 0; }
echo "first diff at line $n of $(wc -l < $a)/$(wc -l < $b)"
echo "-- last turn mark:"; head -n $n "$a" | grep '^#' | tail -1
echo "-- A:"; sed -n "$((n>2?n-2:1)),$((n+2))p" "$a"; echo "-- B:"; sed -n "$((n>2?n-2:1)),$((n+2))p" "$b"
