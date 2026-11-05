#!/bin/bash
# Checks the string resources. Str.t() falls back to English without a word when a key is missing, so a typo'd key or a
# forgotten translation would otherwise go unnoticed. Fails when:
#   - a locale (res/values-xx) lacks a key of res/values, or has one res/values does not (stale)
#   - a key uses a different number of % placeholders than the English text
#   - the code calls Str.t(..., "key", ...) with a key that is not in res/values/strings.xml
# Keys that are only warned about: in res/values but used nowhere.
# usage: tools/check-strings.sh
set -u
cd "$(dirname "$0")/.."

BASE=res/values/strings.xml
UNTRANSLATED=" app_name "   # brand name: locales may leave these out
fail=0
tmp=$(mktemp -d); trap 'rm -rf "$tmp"' EXIT

keys() { grep -oE '<string name="[A-Za-z0-9_]+"' "$1" | sed -E 's/<string name="([^"]+)"/\1/' | sort -u; }
placeholders() { grep -E "<string name=\"$1\"" "$2" | grep -oE '%([0-9]+\$)?[sdf]' | wc -l; }   # key, file

keys "$BASE" > "$tmp/base"

for f in res/values-*/strings.xml; do
    [ -f "$f" ] || continue
    loc=$(basename "$(dirname "$f")")
    keys "$f" > "$tmp/loc"
    for k in $(comm -23 "$tmp/base" "$tmp/loc"); do
        case "$UNTRANSLATED" in *" $k "*) ;; *) echo "$loc: missing '$k'"; fail=1 ;; esac
    done
    for k in $(comm -13 "$tmp/base" "$tmp/loc"); do echo "$loc: '$k' is not in $BASE (stale?)"; fail=1; done
    for k in $(comm -12 "$tmp/base" "$tmp/loc"); do
        [ "$(placeholders "$k" "$BASE")" = "$(placeholders "$k" "$f")" ] || { echo "$loc: '$k' has a different number of % placeholders than English"; fail=1; }
    done
done

# keys the code asks for
for k in $(grep -rhoE 'Str\.t\([^"]*"[A-Za-z0-9_]+"' src | sed -E 's/.*"([^"]+)"$/\1/' | sort -u); do
    grep -qx "$k" "$tmp/base" || { echo "code: Str.t key '$k' is not in $BASE"; fail=1; }
done

# keys nobody asks for (warning only)
for k in $(cat "$tmp/base"); do
    grep -rqF "\"$k\"" src || grep -rqF "@string/$k" AndroidManifest.xml res || echo "warning: '$k' is not used anywhere"
done

[ $fail = 0 ] && echo "strings: ok"
exit $fail
