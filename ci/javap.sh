#!/usr/bin/env bash
# Liest die echten Methoden-Signaturen (nur Namen/Typen) aus Minecraft, Fabric API und LWJGL.
set +e
mkdir -p ci-out
find ~/.gradle/caches/fabric-loom .gradle/loom-cache -name '*.jar' 2>/dev/null | grep -iv sources | sort > ci-out/jars.txt
find ~/.gradle/caches/modules-2 -name 'lwjgl*.jar' 2>/dev/null | grep -iv -e sources -e natives | sort >> ci-out/jars.txt
CP=$(cat ci-out/jars.txt | tr '\n' ':')
: > ci-out/javap.txt
while read -r cls; do
  [ -z "$cls" ] && continue
  echo "===== $cls" >> ci-out/javap.txt
  javap -p -cp "$CP" "$cls" >> ci-out/javap.txt 2>&1
done < ci/javap.txt
# Klassen-Namen nach Muster (ci/packages.txt, eine Regex pro Zeile)
: > ci-out/classes.txt
while read -r jar; do
  unzip -Z1 "$jar" 2>/dev/null | grep '\.class$' | grep -v '\$' | sed 's/\.class$//' >> ci-out/all-classes.tmp
done < ci-out/jars.txt
while read -r pat; do
  [ -z "$pat" ] && continue
  echo "===== $pat" >> ci-out/classes.txt
  grep -E "$pat" ci-out/all-classes.tmp | sort -u >> ci-out/classes.txt
done < ci/packages.txt
rm -f ci-out/all-classes.tmp
# Versionen von Mods auf Modrinth (für eingebaute Mods)
curl -s 'https://api.modrinth.com/v2/project/distanthorizons/version?loaders=%5B%22fabric%22%5D&game_versions=%5B%2226.3%22%5D' \
  | python3 -c 'import json,sys; [print(v["id"], v["version_number"], v["date_published"], [f["filename"] for f in v["files"]]) for v in json.load(sys.stdin)[:5]]' > ci-out/modrinth.txt 2>&1
