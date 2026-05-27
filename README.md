# parer

Java/Spring Boot application with a multi-stage Docker build (DSpace style), designed for batch usage.

## Docker image

The `Dockerfile` uses two build args:

- `JDK_VERSION` (default `21`)
- `DOCKER_REGISTRY` (default `docker.io`)

Runtime image:

- include `jq`
- uses non-root user `parer`
- does **not** expose ports
- does **not** define default `ENTRYPOINT`/`CMD`

## Build

```bash
cd /home/francescopio/DSpace/git/parer
docker build --build-arg JDK_VERSION=21 --build-arg DOCKER_REGISTRY=docker.io -t parer:latest .
```

With a custom registry:

```bash
cd /home/francescopio/DSpace/git/parer
docker build --build-arg JDK_VERSION=21 --build-arg DOCKER_REGISTRY=registry.example.com -t registry.example.com/parer:latest .
```

## Local execution (batch)

Since the image has no default command, you must provide it at runtime:

```bash
docker run --rm parer:latest java -jar /app/app.jar --help
```

## Kubernetes usage

For the case where an operator connects to the Pod and runs the command manually:

1. start the container with a keep-alive command (e.g. `sleep infinity`) in the manifest
2. enter the Pod via `kubectl exec`
3. run the desired batch command

Quick example:

```bash
kubectl exec -it <pod-name> -- sh
java -jar /app/app.jar --help
```

Note: if you prefer to run the batch directly when the Pod starts (without manual exec), set `command`/`args` in the Deployment or Job.

