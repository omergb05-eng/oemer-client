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

# ---- komplette Signaturen (für die Umstellung auf neue Minecraft-Versionen) ----
find ~/.gradle/caches/modules-2 -path '*net.fabricmc.fabric-api*' -name '*.jar' 2>/dev/null | grep -iv -e sources -e javadoc | sort > ci-out/fabric-jars.txt
ALLCP=$(cat ci-out/jars.txt ci-out/fabric-jars.txt | tr '\n' ':')
: > ci-out/all-classes.list
for jar in $(grep -E 'minecraft-(clientonly|common)-deobf' ci-out/jars.txt) $(cat ci-out/fabric-jars.txt); do
  unzip -Z1 "$jar" 2>/dev/null | grep '\.class$' | grep -v -e module-info -e '/mixin/' | grep -E '^(com/mojang/blaze3d|net/minecraft/client|net/minecraft/world|com/mojang/renderpearl|net/minecraft/server/level|net/minecraft/network/protocol|net/minecraft/network/chat|net/minecraft/core|net/minecraft/resources|net/minecraft/util|net/minecraft/server/packs|net/minecraft/world/phys|net/fabricmc/fabric/api)' | sed 's/\.class$//' >> ci-out/all-classes.list
done
sort -u ci-out/all-classes.list -o ci-out/all-classes.list
tr '/' '.' < ci-out/all-classes.list | xargs -n 300 javap -p -J-Xmx3g -cp "$ALLCP" 2>ci-out/javap-all.err | gzip -9 > ci-out/javap-all.txt.gz
cp ci-out/all-classes.list ci-out/classes-all.txt
rm -f ci-out/all-classes.list
for f in $(find ~/.gradle/caches/modules-2 -path '*net.fabricmc.fabric-api*' -name '*.jar'); do :; done
