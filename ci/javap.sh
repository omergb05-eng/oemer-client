#!/usr/bin/env bash
# Liest die echten Methoden-Signaturen (nur Namen/Typen) aus dem Minecraft-Jar.
set +e
mkdir -p ci-out
find ~/.gradle/caches/fabric-loom .gradle/loom-cache -name '*.jar' 2>/dev/null | grep -iv sources | sort > ci-out/jars.txt
CP=$(grep -E 'minecraft-(common|clientOnly|merged|client)' ci-out/jars.txt | tr '\n' ':')
: > ci-out/javap.txt
while read -r cls; do
  [ -z "$cls" ] && continue
  echo "===== $cls" >> ci-out/javap.txt
  javap -p -cp "$CP" "$cls" >> ci-out/javap.txt 2>&1
done < ci/javap.txt
