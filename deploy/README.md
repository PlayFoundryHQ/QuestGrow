# Deploying QuestGrow

The backend runs as a single-replica Deployment on the **nuc-lab** Kubernetes
cluster, reachable at **`questgrow.opscale.ir`**, managed by **ArgoCD**
(same pattern as the `downtime` project).

## Pieces

| Piece | Where |
|---|---|
| Container image | `ghcr.io/playfoundryhq/questgrow:<version>` — built by `scripts/release.sh` |
| Helm chart | `deploy/questgrow/`, published to `oci://ghcr.io/playfoundryhq/charts` as `questgrow` |
| Real values | `deploy/questgrow/values-nuc-lab.yaml` (in the chart) |
| ArgoCD Application | `OpScaleLab/nuc-lab-operation` → `gitops/apps/questgrow.yaml` (see below) |
| DNS | `questgrow.opscale.ir` A/CNAME in ArvanCloud → cluster ingress; ArvanCloud terminates TLS at its edge |

## Storage / persistence

SQLite on a 1 Gi `local-path` PVC mounted at `/data`. One family = a few MB.
The PVC has `helm.sh/resource-policy: keep` and the storageclass reclaim policy
is `Retain`, so the DB survives a release delete. Rollout strategy is
`Recreate` — **never run more than one replica** without moving to Postgres.

Backup: `kubectl -n questgrow exec deploy/questgrow -- sh -c 'cat /data/questgrow.db' > backup.db`
(SQLite in WAL mode — checkpoint first or copy all three `questgrow.db*` files).

## First-time deploy

1. Cut a release: `./scripts/release.sh 0.3.1` — builds & pushes the image and
   the chart, tags `v0.3.1`, creates the GitHub Release.
2. Add the ArgoCD Application to `OpScaleLab/nuc-lab-operation`
   (`gitops/apps/questgrow.yaml`):

   ```yaml
   apiVersion: argoproj.io/v1alpha1
   kind: Application
   metadata:
     name: questgrow
     namespace: argocd
   spec:
     project: default
     destination:
       server: https://kubernetes.default.svc
       namespace: questgrow
     source:
       repoURL: ghcr.io/playfoundryhq/charts
       chart: questgrow
       targetRevision: 0.3.1          # bump per release
       helm:
         releaseName: questgrow
         valueFiles: [values-nuc-lab.yaml]
     syncPolicy:
       automated: { prune: false, selfHeal: false }
       syncOptions: [CreateNamespace=true, ServerSideApply=true]
   ```

3. Add the `questgrow.opscale.ir` DNS record in ArvanCloud pointing at the
   cluster ingress (same target as `downtime.opscale.ir`).
4. ArgoCD (via the `root` app-of-apps) picks up the new Application and syncs.
5. Smoke test: `curl https://questgrow.opscale.ir/health` → `{"status":"ok",…}`.

## Subsequent releases

`./scripts/release.sh X.Y.Z`, then bump `targetRevision` in the ArgoCD
Application. That's it — ArgoCD syncs the new chart/image.

## Local sanity checks (no cluster writes)

```
helm lint deploy/questgrow -f deploy/questgrow/values-nuc-lab.yaml
helm template questgrow deploy/questgrow -f deploy/questgrow/values-nuc-lab.yaml
helm template questgrow deploy/questgrow -f deploy/questgrow/values-nuc-lab.yaml \
  --namespace questgrow | kubectl --context nuc-lab apply --dry-run=server -f -
```
