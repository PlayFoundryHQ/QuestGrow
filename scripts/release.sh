#!/usr/bin/env bash
# Cut a QuestGrow release from a clean local checkout.
#
#   ./scripts/release.sh 0.3.1                 # backend image + git tag + GitHub release
#   ./scripts/release.sh 0.3.1 --with-apk      # also build+attach the signed Android APK
#   ./scripts/release.sh 0.3.1 --dry-run       # build + test, no push / tag / release
#
# Flow (all local, no cloud CI — see docs/memory cicd-approach):
#   verify tree → run tests → bump pyproject version → build & push image →
#   git tag vX.Y.Z → gh release create → print the k8s image-tag bump to do next.
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

say "preflight"
[[ -z "$(git status --porcelain)" ]]              || { echo "working tree not clean"; exit 1; }
[[ "$(git branch --show-current)" == "main" ]]    || { echo "not on main"; exit 1; }
git fetch -q origin
[[ "$(git rev-parse HEAD)" == "$(git rev-parse origin/main)" ]] || { echo "local main != origin/main"; exit 1; }
git rev-parse -q --verify "refs/tags/$tag" >/dev/null && { echo "tag $tag already exists"; exit 1; }
sha="$(git rev-parse --short HEAD)"

say "tests"
./scripts/test.sh backend
(( with_apk )) && ./scripts/test.sh android

say "version bump → $ver"
python3 - "$ver" <<'PY'
import re,sys,pathlib
v=sys.argv[1]; p=pathlib.Path("pyproject.toml")
p.write_text(re.sub(r'(?m)^version = ".*"$', f'version = "{v}"', p.read_text(), count=1))
PY
# keep the runtime /health banner in sync (api.py reads pyproject at import? no — grep it)
grep -q "\"$ver\"" pyproject.toml || { echo "version bump did not take"; exit 1; }

say "helm lint"
helm lint deploy/questgrow -f deploy/questgrow/values-nuc-lab.yaml

say "build image  $IMAGE:$tag  (+ :$sha, :latest)"
docker build -t "$IMAGE:$tag" -t "$IMAGE:$sha" -t "$IMAGE:latest" .
docker run --rm -d --name qg-relcheck -p 18099:8000 "$IMAGE:$tag" >/dev/null
for i in $(seq 1 20); do curl -sf localhost:18099/health >/dev/null && break || sleep 0.5; done
curl -sf localhost:18099/health && echo "  ✓ image healthy" || { docker logs qg-relcheck; docker rm -f qg-relcheck; exit 1; }
docker rm -f qg-relcheck >/dev/null

apk=""
if (( with_apk )); then
  say "android release APK"
  QG_VERSION_NAME="$ver" android/gradlew -p android :app:assembleRelease -q
  apk="android/app/build/outputs/apk/release/app-release.apk"
  [[ -f "$apk" ]] || { echo "APK not produced"; exit 1; }
fi

if (( dry )); then
  say "dry run — reverting version bump, nothing pushed"
  git checkout -- pyproject.toml
  exit 0
fi

say "commit + tag + push"
git add pyproject.toml && git commit -q -m "release: v$ver"
git tag -a "$tag" -m "QuestGrow $tag"
git push -q origin main "$tag"
docker push "$IMAGE:$tag"; docker push "$IMAGE:$sha"; docker push "$IMAGE:latest"

say "package + push helm chart"
# chart version tracks the app version so ArgoCD targetRevision moves together
chart_ver="$ver"
sed -i -E "s/^version: .*/version: $chart_ver/; s/^appVersion: .*/appVersion: \"$ver\"/" deploy/questgrow/Chart.yaml
sed -i -E "s/^( *tag: ).*/\1\"$ver\"/" deploy/questgrow/values-nuc-lab.yaml
git add deploy/questgrow/Chart.yaml deploy/questgrow/values-nuc-lab.yaml \
  && git commit -q -m "release: chart v$chart_ver (image $ver)" && git push -q origin main
helm package deploy/questgrow --version "$chart_ver" --app-version "$ver" -d /tmp
helm push "/tmp/questgrow-$chart_ver.tgz" "$CHART_REGISTRY"
rm -f "/tmp/questgrow-$chart_ver.tgz"

say "github release"
notes="$(git log --pretty='- %s' "$(git describe --tags --abbrev=0 HEAD^ 2>/dev/null || git rev-list --max-parents=0 HEAD)"..HEAD^ | grep -v '^- release:' || true)"
gh release create "$tag" ${apk:+"$apk"} \
  --title "QuestGrow $tag" \
  --notes "Backend image: \`$IMAGE:$tag\`

$notes"

say "done"
echo "  image: $IMAGE:$tag"
echo "  chart: $CHART_REGISTRY/questgrow  $chart_ver"
echo "  next:  bump the QuestGrow ArgoCD Application targetRevision to $chart_ver"
echo "         (in OpScaleLab/nuc-lab-operation gitops/apps) and let ArgoCD sync."
