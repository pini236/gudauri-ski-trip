#!/usr/bin/env bash
# One cut of at most 30 seconds from the two declaration recordings (13.11.2026, Pini: Google asks for about 30 s).
# Editing only: the app and the recording are untouched. Segments are picked by the timeline files (seconds since the
# recording started, about +-1 s), so each has a little margin. Usage: cut30.sh <video dir>
set -u
V=${1:?video dir}
T1="$V/timeline-dayrec-video-1.txt"; T2="$V/timeline-dayrec-video-2.txt"
V1="$V/dayrec-service-declaration-1-day.mp4"; V2="$V/dayrec-service-declaration-2-descent.mp4"
OUTF="$V/dayrec-service-30s.mp4"
at() { grep -m1 -F "$2" "$1" | sed -E 's/^\+([0-9.]+)s.*/\1/'; }
a0=0; a1=$(at "$T1" "the live screen is up"); b0=$(at "$T1" "press Home"); c0=$(at "$T1" "open the notification shade")
s=$(at "$T1" "tap Stop in the notification"); d0=$(at "$T1" "back to the app"); e0=$(at "$T2" "open the day's timeline")
for x in a1 b0 c0 s d0 e0; do [ -n "${!x}" ] || { echo "missing time: $x"; exit 1; }; done
# segments: file index, start, end, speed (1 = normal)
seg() { awk -v i="$1" -v a="$2" -v b="$3" -v sp="$4" -v n="$5" 'BEGIN { s=a-0.3; if (s<0) s=0; printf "[%d:v]trim=start=%.2f:end=%.2f,setpts=(PTS-STARTPTS)/%s,fps=30,scale=720:-2,setsar=1[v%d];", i, s, b, sp, n }'; }
fc=""
fc+=$(seg 0 "$a0" "$(awk -v x="$a1" 'BEGIN{print x+1.5}')" 1 0)                                   # start, tap Record, live screen
fc+=$(seg 0 "$(awk -v x="$a1" 'BEGIN{print x+2}')" "$(awk -v x="$a1" 'BEGIN{print x+6}')" 2 1)    # the counter runs (2x)
fc+=$(seg 0 "$b0" "$(awk -v x="$b0" 'BEGIN{print x+2.5}')" 1 2)                                   # phone home, recording goes on
fc+=$(seg 0 "$c0" "$(awk -v x="$s" 'BEGIN{print x+2.5}')" 1 3)                                     # notification with Stop, tap Stop
fc+=$(seg 0 "$d0" "$(awk -v x="$d0" 'BEGIN{print x+4.5}')" 1 4)                                    # back in the app after Stop
fc+=$(seg 1 "$e0" "$(awk -v x="$e0" 'BEGIN{print x+5}')" 1 5)                                      # the day's timeline
fc+="[v0][v1][v2][v3][v4][v5]concat=n=6:v=1:a=0,null[out]"
# inputs 0 and 1 are the recordings WITH the touch circles
ffmpeg -loglevel error -y -i "$V1" -i "$V2" -filter_complex "$fc" -map "[out]" -c:v libx264 -preset medium -crf 23 -pix_fmt yuv420p -t 29.8 -an "$OUTF" 2>&1 | head -5
[ -s "$OUTF" ] || { echo "no output"; exit 1; }
len=$(ffprobe -v error -show_entries format=duration -of csv=p=0 "$OUTF")
printf 'dayrec-service-30s.mp4: %.1f seconds (limit 30).\nEdited from the two recordings in this folder (the ones with the touch circles), by ffmpeg only; the app was not changed.\nOrder: start and a real tap on Record, the live screen (the counter at 2x), the phone home with the recording going on,\nthe notification with Stop and the tap on it, back in the app after Stop, the day timeline. No text is added on the video.\nThe counter segment on the live screen is played at 2x (about 6 s of recording in 3 s). The lock screen is not in this cut (the emulator script does not lock the screen).\nSegments cut at the times of timeline-*.txt (about +-1 s), so each has a margin.\n' "$len" > "$V/dayrec-service-30s-README.txt"
echo "cut30: $len s"
awk -v l="$len" 'BEGIN { exit !(l <= 30.0) }' || { echo "longer than 30 s"; exit 1; }
