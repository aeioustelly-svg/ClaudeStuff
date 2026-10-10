#!/bin/sh
# Converts the "La crucea din mormant" mp3 into the mono Ogg Vorbis file the disc plays.
# Mono, so the jukebox and the arena music fade with distance like vanilla discs.
#   tools/import_song.sh path/to/La_crucea_din_mormant.mp3
# If the song's length changes, update ModItems.DISC_LENGTH_TICKS (seconds x 20).
set -e
[ -n "$1" ] || { echo "usage: $0 song.mp3" >&2; exit 1; }
out="$(dirname "$0")/../src/main/resources/assets/aceliada/sounds/la_crucea_din_mormant.ogg"
ffmpeg -hide_banner -loglevel error -y -i "$1" -ac 1 -ar 44100 -c:a libvorbis -q:a 4 -map_metadata -1 "$out"
ffprobe -hide_banner "$out" 2>&1 | grep Duration
