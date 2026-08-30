# Deploying QuestGrow

Single-replica Deployment on the **nuc-lab** Kubernetes cluster, at
**`questgrow.opscale.ir`**, managed by **ArgoCD** — same pattern as `downtime`.

## Pieces

| Piece | Where |
|---|---|
| Container image | `ghcr.io/playfoundryhq/questgrow:<version>` (public) — built by `scripts/release.sh` |
| Helm chart | `deploy/questgrow/`, published to `ghcr.io/playfoundryhq/charts/questgrow` (public, OCI) |
| Real values | `deploy/questgrow/values-nuc-lab.yaml` — bundled in the chart, referenced by `valueFiles` |
| ArgoCD Application | `OpScaleLab/nuc-lab-operation` → `gitops/apps/questgrow.yaml` |
| Ingress | Traefik `IngressRoute` on `questgrow.opscale.ir` + cert-manager `Certificate` (`letsencrypt-http01`) |
| DNS | `questgrow.opscale.ir` in ArvanCloud → the cluster's public ingress (same target as `downtime.opscale.ir`) |

## Storage / persistence

SQLite on a 1 Gi `local-path` PVC at `/data`. One family = a few MB. The PVC
has `helm.sh/resource-policy: keep` and the storageclass reclaim policy is
`Retain`, so the DB survives a release/app delete. Rollout is `Recreate` —
**never run more than one replica** without moving to Postgres first.

Backup: `kubectl -n questgrow exec deploy/questgrow -- tar c -C /data . > qg-backup.tar`
(SQLite WAL — grab all three `questgrow.db*` files, or checkpoint first).

## One-time setup

1. **Publish the packages.** In the `PlayFoundryHQ` org → Packages, set both
   `questgrow` and `charts/questgrow` visibility to **Public** (the repo is
   already public). This lets ArgoCD and the kubelet pull with no credential.

2. **Register the chart registry as an ArgoCD Repository** (out of band — not
   in the ops repo, same as `git-secret-controller`):

   ```
   kubectl --context nuc-lab -n argocd create secret generic ghcr-playfoundryhq-charts \
     --from-literal=type=helm \
     --from-literal=name=ghcr-playfoundryhq-charts \
     --from-literal=url=ghcr.io/playfoundryhq/charts \
     --from-literal=enableOCI=true
   kubectl --context nuc-lab -n argocd label secret ghcr-playfoundryhq-charts \
     argocd.argoproj.io/secret-type=repository
   ```

3. **DNS** — `questgrow.opscale.ir` in ArvanCloud → cluster ingress. (Done.)

4. **Merge the ArgoCD Application** — `OpScaleLab/nuc-lab-operation` PR that
   adds `gitops/apps/questgrow.yaml`. The `root` app-of-apps picks it up and
   the `questgrow` Application syncs (manual first sync: `prune/selfHeal:
   false`).

5. **Smoke test**: `curl https://questgrow.opscale.ir/health` →
   `{"status":"ok","api":"0.3.2"}`.

## Subsequent releases

```
./scripts/release.sh X.Y.Z
```
then bump `targetRevision` to `X.Y.Z` in `gitops/apps/questgrow.yaml` (PR).
ArgoCD syncs the new chart+image. Nothing else changes.

## Local sanity checks (no cluster writes)

```
helm lint deploy/questgrow -f deploy/questgrow/values-nuc-lab.yaml
helm template questgrow deploy/questgrow -f deploy/questgrow/values-nuc-lab.yaml \
  --namespace questgrow | kubectl --context nuc-lab apply --dry-run=server -f -
```
