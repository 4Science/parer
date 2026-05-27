# bitbucket-pipelines.yml — guida operativa

Pipeline CI/CD del worker Java ParER:
- builda l'immagine Docker e la pusha su ECR
- aggiorna automaticamente `worker.image.tag` in `parer-values/dev/` e `parer-values/staging/`
- aggiornamento dei tenant customer (eu/test, eu/prod, us/test, us/prod) **solo** via trigger manuale
- ogni env ha il suo trigger manuale dedicato (`deploy-on-dev`, `deploy-on-staging`, `deploy-on-production`) per re-deploy mirato

## Trigger

| Evento                                                  | Comportamento                                                            |
| ------------------------------------------------------- | ------------------------------------------------------------------------ |
| Commit su `main`                                        | `build-and-push` → `deploy-on-dev` → `deploy-on-staging`                 |
| UI → Run pipeline → custom → `build-and-push`           | Solo build + push (su qualsiasi branch)                                  |
| UI → Run pipeline → custom → `deploy-on-dev`            | Solo commit GitOps su `parer-values/dev/*` (no rebuild)                  |
| UI → Run pipeline → custom → `deploy-on-staging`        | Solo commit GitOps su `parer-values/staging/*` (no rebuild)              |
| UI → Run pipeline → custom → `deploy-on-production`     | Solo commit GitOps su `parer-values/{eu,us}/{test,prod}/*` (no rebuild)  |

## Tag immagine prodotto dal build

```
<ECR_REGISTRY>/parer:<APP_VERSION>-<SHA8>
<ECR_REGISTRY>/parer:latest
```

- `APP_VERSION` viene dal `<version>` del `pom.xml` (rimuove `-SNAPSHOT`).
- `SHA8` sono i primi 8 caratteri del commit SHA.
- `latest` è un puntatore mobile, **non usarlo nel chart** in ambienti staging/prod — usa sempre il tag immutabile.

## Determinazione del tag nello step di deploy

Il deploy script seleziona `IMAGE_TAG` con questa precedenza:

1. **`image.env`** artifact dal `build-and-push` step (path `branches.main` → freshly built tag)
2. **Variable Bitbucket** `IMAGE_TAG` se settata dall'operatore (qualsiasi `deploy-on-*` custom)
3. **Default**: calcolato dal `pom.xml` + commit corrente (`<pom-version>-<sha8>`)

In ogni caso, prima di committare, il deploy script verifica che il tag esista in ECR con `aws ecr describe-images`. Se manca → fallisce con messaggio chiaro che spiega cosa controllare.

## Variabili richieste

### Workspace (Workspace Settings → Pipelines → Workspace variables)

Già configurate (riutilizzate da fuseki):

| Variabile               | Scope     | Note                                                          |
| ----------------------- | --------- | ------------------------------------------------------------- |
| `AWS_ACCESS_KEY_ID`     | secured   | IAM user `BitBucketRunners` con `ecr:Push` sul repo `parer`   |
| `AWS_SECRET_ACCESS_KEY` | secured   |                                                               |
| `AWS_REGION`            | plain     | `eu-west-1`                                                   |
| `BB_USER`               | plain     | Author del commit GitOps                                      |
| `BB_EMAIL`              | plain     | Author email                                                  |

### Repository (Repository Settings → Pipelines → Repository variables)

Da configurare per questo repo:

| Variabile                   | Scope     | Note                                                                                       |
| --------------------------- | --------- | ------------------------------------------------------------------------------------------ |
| `PARER_VALUES_REPO_TOKEN`   | secured   | Repository Access Token su `parer-values` con scope `repositories:write`                   |
| `PARER_VALUES_REPO_NAME`    | plain     | `bitbucket.org/4Science/parer-values.git`                                                  |

### Deployment environments (Repository Settings → Deployments)

Una variabile per env (il nome dell'env viene auto-iniettato come `$BITBUCKET_DEPLOYMENT_ENVIRONMENT`, no bisogno di `ENV_NAME`):

| Deployment   | Variabili                                       | Custom pipeline che lo usa            |
|--------------|-------------------------------------------------|---------------------------------------|
| `dev`        | `VALUES_DIRS=dev`                               | branches.main + custom.deploy-on-dev  |
| `staging`    | `VALUES_DIRS=staging`                           | branches.main + custom.deploy-on-staging |
| `production` | `VALUES_DIRS="eu/test eu/prod us/test us/prod"` | custom.deploy-on-production           |

## ECR

- Account: `891377365430` (bitbucket-runners)
- Region: `eu-west-1`
- Repository: `parer`
- URI: `891377365430.dkr.ecr.eu-west-1.amazonaws.com/parer`

Resource policy del repository:
- `AllowCrossAccountPush` per IAM user `BitBucketRunners`
- `AllowCrossAccountPull` per gli account dei cluster EKS che pullano l'immagine

## Build

- Runner: self-hosted ARM (`linux.arm64`, label `arm64.group`), size 2x
- Build single-arch `linux/arm64` (allineato al `nodeSelector: kubernetes.io/arch: arm64`
  del chart `parer`)
- Buildx con registry cache (`--cache-from/to` su `parer:cache`)
- `--provenance=false` per evitare gli attestation manifest OCI

## Deploy manuali (custom pipelines)

Quattro custom pipeline disponibili dall'UI Bitbucket (Run pipeline → custom):

| Pipeline                | Cosa fa                                                                                |
|-------------------------|----------------------------------------------------------------------------------------|
| `build-and-push`        | Builda e pusha l'immagine. Nessun commit GitOps.                                       |
| `deploy-on-dev`         | Solo commit GitOps su `parer-values/dev/*` con `worker.image.tag = $IMAGE_TAG`.        |
| `deploy-on-staging`     | Idem, su `parer-values/staging/*`.                                                     |
| `deploy-on-production`  | Idem, su tutti e 4 i path customer (`eu/test eu/prod us/test us/prod`) insieme.        |

Ogni `deploy-on-*` chiede in input `IMAGE_TAG` (variable, opzionale):
- vuoto → usa il default = tag dell'HEAD del branch su cui giri (`<pom-version>-<sha8>`)
- valore esplicito → promuove quel tag specifico (es. rollback dopo regressione, o ridistribuzione di una versione validata altrove)

Cosa fa ciascun `deploy-on-*` step:
- Risolve `IMAGE_TAG` (default o esplicito)
- Verifica che il tag esista in ECR (`aws ecr describe-images`). Se manca → fail con messaggio esplicito
- Clona `parer-values`, esegue `yq -i ".worker.image.tag = \"<tag>\""` su ogni file dei path dell'env
- Commit + push su `main` di `parer-values`
- ArgoCD risincronizza i tenant interessati entro ~20 secondi

### Esempi tipici

- **Re-deploy dev di un fix isolato già pushato a main**: Run pipeline → custom → `deploy-on-dev`, IMAGE_TAG vuoto → usa l'HEAD corrente.
- **Rollback prod a un tag noto stabile**: Run pipeline → custom → `deploy-on-production`, IMAGE_TAG = `0.0.1-abcd1234`.
- **Promozione "ho testato un tag su staging, lo voglio in prod"**: leggi il tag corrente in `parer-values/staging/<qualunque>`, run `deploy-on-production` con quel tag.
