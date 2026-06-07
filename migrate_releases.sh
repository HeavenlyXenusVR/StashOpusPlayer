#!/usr/bin/env bash
# migrate_releases.sh
# Streams each Android release from HeavenlyXenusVR/Lumisound →
# HeavenlyXenusVR/StashOpusPlayer, deleting each asset locally the moment
# it has been uploaded. Disk footprint: at most ONE asset file at a time.
#
# Usage:  bash migrate_releases.sh
# Needs:  gh (authenticated), python3

set -euo pipefail

OLD_REPO="HeavenlyXenusVR/Lumisound"
NEW_REPO="HeavenlyXenusVR/StashOpusPlayer"
WORK_DIR="$(mktemp -d /tmp/android_migrate_XXXXXX)"
trap 'rm -rf "$WORK_DIR"' EXIT

# ── Collect all non-iOS release tags, oldest first ───────────────────────────
echo "Fetching Android release list from $OLD_REPO…"
mapfile -t TAGS < <(
  gh release list -R "$OLD_REPO" --limit 200 --json tagName,createdAt \
    --jq '.[] | select(.tagName | startswith("ios/") | not) | .tagName' \
    2>/dev/null | tac
)

TOTAL=${#TAGS[@]}
echo "Found $TOTAL Android releases to migrate."
echo ""

DONE=0
FAILED=0

for TAG in "${TAGS[@]}"; do
  DONE=$((DONE + 1))
  echo "[$DONE/$TOTAL] $TAG"

  # ── 1. Fetch release metadata ───────────────────────────────────────────────
  RELEASE_JSON=$(
    gh release view "$TAG" -R "$OLD_REPO" \
      --json tagName,name,body,isDraft,isPrerelease 2>/dev/null || true
  )
  if [ -z "$RELEASE_JSON" ]; then
    echo "  ⚠  Cannot fetch metadata — skipping"
    FAILED=$((FAILED + 1))
    continue
  fi

  TITLE=$(   printf '%s' "$RELEASE_JSON" | python3 -c "import sys,json; print(json.load(sys.stdin)['name'])")
  BODY=$(    printf '%s' "$RELEASE_JSON" | python3 -c "import sys,json; print(json.load(sys.stdin).get('body','') or '')")
  IS_DRAFT=$(printf '%s' "$RELEASE_JSON" | python3 -c "import sys,json; print(json.load(sys.stdin)['isDraft'])")
  IS_PRE=$(  printf '%s' "$RELEASE_JSON" | python3 -c "import sys,json; print(json.load(sys.stdin)['isPrerelease'])")

  # ── 2. Fetch asset name list (no download yet) ──────────────────────────────
  mapfile -t ASSET_NAMES < <(
    gh release view "$TAG" -R "$OLD_REPO" --json assets \
      --jq '.assets[].name' 2>/dev/null || true
  )
  ASSET_COUNT=${#ASSET_NAMES[@]}

  # ── 3. Create release on StashOpusPlayer (no assets yet) ───────────────────
  BODY_FILE="$WORK_DIR/body_$$.md"
  printf '%s' "$BODY" > "$BODY_FILE"

  CREATE_ARGS=(
    "$TAG"
    --title "$TITLE"
    --notes-file "$BODY_FILE"
    --target main
    -R "$NEW_REPO"
  )
  [ "$IS_DRAFT" = "True" ] && CREATE_ARGS+=(--draft)
  [ "$IS_PRE"   = "True" ] && CREATE_ARGS+=(--prerelease)

  if ! gh release create "${CREATE_ARGS[@]}" 2>/dev/null; then
    echo "  ✗  gh release create failed — skipping"
    FAILED=$((FAILED + 1))
    rm -f "$BODY_FILE"
    continue
  fi
  rm -f "$BODY_FILE"
  echo "  ✓  Release created on $NEW_REPO ($ASSET_COUNT asset(s) to stream)"

  # ── 4. Stream assets: download one → upload → delete local ─────────────────
  ASSET_DIR="$WORK_DIR/asset_$$"
  mkdir -p "$ASSET_DIR"
  UPLOAD_OK=0
  UPLOAD_FAIL=0

  for ASSET_NAME in "${ASSET_NAMES[@]}"; do
    ASSET_PATH="$ASSET_DIR/$ASSET_NAME"

    # Download just this one file
    if ! gh release download "$TAG" \
          -R "$OLD_REPO" \
          -D "$ASSET_DIR" \
          --pattern "$ASSET_NAME" 2>/dev/null; then
      echo "    ✗  Download failed: $ASSET_NAME"
      UPLOAD_FAIL=$((UPLOAD_FAIL + 1))
      continue
    fi

    SIZE_MB=$(python3 -c "import os; print(f'{os.path.getsize(\"$ASSET_PATH\")/1048576:.1f}')" 2>/dev/null || echo "?")

    # Upload to new repo
    if gh release upload "$TAG" "$ASSET_PATH" \
          -R "$NEW_REPO" --clobber 2>/dev/null; then
      echo "    ✓  $ASSET_NAME (${SIZE_MB} MB) uploaded"
      UPLOAD_OK=$((UPLOAD_OK + 1))
    else
      echo "    ✗  Upload failed: $ASSET_NAME"
      UPLOAD_FAIL=$((UPLOAD_FAIL + 1))
    fi

    # Delete the local file immediately — this is the key optimisation
    rm -f "$ASSET_PATH"
  done

  rm -rf "$ASSET_DIR"

  # ── 5. Delete release + tag from Lumisound ─────────────────────────────────
  if gh release delete "$TAG" --yes --cleanup-tag -R "$OLD_REPO" 2>/dev/null; then
    echo "  ✓  Deleted from $OLD_REPO"
  else
    echo "  ⚠  Delete from $OLD_REPO failed (may already be gone)"
  fi

  echo "  Assets: ${UPLOAD_OK} uploaded, ${UPLOAD_FAIL} failed"
  echo ""
done

echo "=============================="
echo "Migration complete."
echo "  Processed : $DONE / $TOTAL"
echo "  Failed    : $FAILED"
echo "=============================="
