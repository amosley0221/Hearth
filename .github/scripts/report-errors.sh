#!/usr/bin/env bash
# Turns Gradle/Kotlin errors in a build log into GitHub annotations,
# so they're visible from the API and the Checks tab without opening raw logs.
log="$1"
errors=$(grep -E "^e: |error:|What went wrong|Could not|FAILURE|> " "$log" | grep -v "^> Task" | head -n 120)
[ -z "$errors" ] && errors=$(tail -n 60 "$log")
i=0
while IFS= read -r line && [ $i -lt 45 ]; do
  esc=${line//'%'/'%25'}
  echo "::error title=build::${esc}"
  i=$((i+1))
done <<< "$errors"
exit 0
