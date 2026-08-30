#!/usr/bin/env bash
# Cut a QuestGrow release from a clean local checkout.
#
#   ./scripts/release.sh 0.3.1                 # backend image + git tag + GitHub release
#   ./scripts/release.sh 0.3.1 --with-apk      # also build+attach the signed Android APK
#   ./scripts/release.sh 0.3.1 --dry-run       # build + test, no push / tag / release
#
# Flow (all local, no cloud CI):
#   verify tree → run backend tests → bump versions (pyproject + api.py + chart
#   + nuc-lab values) → build & health-check image → one commit → git tag vX.Y.Z
#   → push image (ghcr, tag X.Y.Z + sha + latest) → push chart (OCI) →
#   gh release create → print the ArgoCD targetRevision bump to do next.
# Image/chart tags are bare X.Y.Z; the git tag is vX.Y.Z.
set -euo pipefail
cd "$(dirname "$0")/.."

REGISTRY="${QUESTGROW_REGISTRY:-ghcr.io/playfoundryhq}"
IMAGE="$REGISTRY/questgrow"
CHART_REGISTRY="${QUESTGROW_CHART_REGISTRY:-oci://ghcr.io/playfoundryhq/charts}"

ver="${1:?usage: release.sh X.Y.Z [--with-apk] [--dry-run]}"
[[ "$ver" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] || { echo "version must be X.Y.Z"; exit 1; }
tag="v$ver"
with_apk=0; dry=0
for a in "${@:2}"; do case "$a" in
  --with-apk) with_apk=1 ;; --dry-run) dry=1 ;; *) echo "unknown flag $a"; exit 1 ;;
esac; done

say() { echo; echo "━━ $*"; }
: "${ANDROID_HOME:=${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}}"
[[ -d "$ANDROID_HOME" ]] && export ANDROID_HOME

say "preflight"
[[ -z "$(git status --porcelain)" ]]              || { echo "working tree not clean"; exit 1; }
[[ "$(git branch --show-current)" == "main" ]]    || { echo "not on main"; exit 1; }
git fetch -q origin
[[ "$(git rev-parse HEAD)" == "$(git rev-parse origin/main)" ]] || { echo "local main != origin/main"; exit 1; }
git rev-parse -q --verify "refs/tags/$tag" >/dev/null && { echo "tag $tag already exists"; exit 1; }
command -v gh docker helm >/dev/null || { echo "need gh, docker, helm on PATH"; exit 1; }
# ghcr auth (idempotent — uses the gh token, needs write:packages)
if ! (( dry )); then
  gh auth token | docker login ghcr.io -u "$(gh api user --jq .login)" --password-stdin >/dev/null
  gh auth token | helm registry login ghcr.io -u "$(gh api user --jq .login)" --password-stdin >/dev/null
fi
sha="$(git rev-parse --short HEAD)"

say "tests"
./scripts/test.sh backend
(( with_apk )) && ./scripts/test.sh android

say "bump versions → $ver  (pyproject + chart + nuc-lab values)"
python3 - "$ver" <<'PY'
import re,sys,pathlib
v=sys.argv[1]
def sub(path,*pairs):
    p=pathlib.Path(path); t=p.read_text()
    for pat,rep in pairs: t=re.sub(pat,rep,t,count=1,flags=re.M)
    p.write_text(t)
sub("pyproject.toml", (r'^version = ".*"$', f'version = "{v}"'))
sub("src/questgrow/api.py", (r'(FastAPI\(title="QuestGrow API", version=")[^"]*(")', rf'\g<1>{v}\g<2>'))
sub("deploy/questgrow/Chart.yaml",
    (r'^version: .*$', f'version: {v}'),
    (r'^appVersion: .*$', f'appVersion: "{v}"'))
sub("deploy/questgrow/values-nuc-lab.yaml", (r'^( *tag: ).*$', rf'\g<1>"{v}"'))
PY
grep -q "\"$ver\"" pyproject.toml || { echo "version bump did not take"; exit 1; }

say "helm lint"
helm lint deploy/questgrow -f deploy/questgrow/values-nuc-lab.yaml

say "build image  $IMAGE:$ver  (+ :$sha, :latest)"
docker build -t "$IMAGE:$ver" -t "$IMAGE:$sha" -t "$IMAGE:latest" .
docker run --rm -d --name qg-relcheck -p 18099:8000 "$IMAGE:$ver" >/dev/null
for i in $(seq 1 20); do curl -sf localhost:18099/health >/dev/null && break || sleep 0.5; done
curl -sf localhost:18099/health && echo "  ✓ image healthy" || { docker logs qg-relcheck; docker rm -f qg-relcheck; exit 1; }
docker rm -f qg-relcheck >/dev/null

apk=""
if (( with_apk )); then
  say "android release APK"
  IFS=. read -r vmaj vmin vpat <<<"$ver"
  vcode=$(( vmaj*10000 + vmin*100 + vpat ))   # 0.3.3 → 303, monotonic per semver
  QG_VERSION_NAME="$ver" QG_VERSION_CODE="$vcode" \
    android/gradlew -p android :app:assembleRelease -q
  apk="android/app/build/outputs/apk/release/app-release.apk"
  [[ -f "$apk" ]] || { echo "APK not produced"; exit 1; }
  # fail loud if it silently fell back to the debug key
  bt=$(ls -d "$HOME"/Android/Sdk/build-tools/* | sort -V | tail -1)
  "$bt/apksigner" verify --print-certs "$apk" | grep -q "CN=QuestGrow" \
    || { echo "APK is not signed with the QuestGrow upload key (keystore.properties missing?)"; exit 1; }
fi

if (( dry )); then
  say "dry run — reverting bumps, nothing pushed"
  git checkout -- pyproject.toml src/questgrow/api.py deploy/questgrow/
  exit 0
fi

say "release notes"
prev="$(git describe --tags --abbrev=0 2>/dev/null || true)"
range="${prev:+$prev..}HEAD"
notes="$(git log --pretty='- %s' $range | grep -vE '^- release:' | head -40 || true)"

say "commit + tag + push"
git add pyproject.toml src/questgrow/api.py deploy/questgrow/Chart.yaml deploy/questgrow/values-nuc-lab.yaml
git commit -q -m "release: v$ver — backend image + chart"
git tag -a "$tag" -m "QuestGrow $tag"
git push -q origin main "$tag"

say "push image + chart"
docker push "$IMAGE:$ver"; docker push "$IMAGE:$sha"; docker push "$IMAGE:latest"
helm package deploy/questgrow --version "$ver" --app-version "$ver" -d /tmp
helm push "/tmp/questgrow-$ver.tgz" "$CHART_REGISTRY"
rm -f "/tmp/questgrow-$ver.tgz"

say "github release"
gh release create "$tag" ${apk:+"$apk"} \
  --title "QuestGrow $tag" \
  --notes "Backend image: \`$IMAGE:$ver\`
Helm chart: \`$CHART_REGISTRY/questgrow\` \`$ver\`

$notes"

say "done"
echo "  image: $IMAGE:$ver"
echo "  chart: $CHART_REGISTRY/questgrow  $ver"
echo "  next:  bump the QuestGrow ArgoCD Application targetRevision to $ver in"
echo "         OpScaleLab/nuc-lab-operation gitops/apps, let ArgoCD sync."
