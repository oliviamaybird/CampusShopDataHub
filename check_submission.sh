#!/usr/bin/env bash
#
# check_submission.sh — pre-flight check for your submission image tar.
#
# Verifies, the same way the auto-marker does, that your submission tar
#   1. loads into a clean Docker daemon as ONE tagged image,
#   2. exposes the HTTP port (8080/tcp),
#   3. starts as a container with the port published to a dynamic host port,
#   4. answers GET /actuator/health.
#
# Usage:
#   ./check_submission.sh <submission.tar> [options]
#
# Options:
#   -e KEY=VALUE      environment variable for the container (repeatable),
#                     e.g. -e ILP_ENDPOINT=https://... or -e RABBITMQ_HOST=host.docker.internal
#   --port N          container HTTP port (default 8080)
#   --health PATH     health endpoint path (default /actuator/health)
#   --timeout SEC     how long to wait for the service to become healthy (default 60)
#   --keep            leave the container running afterwards (prints how to stop it)
#
# Requires: docker, curl. Runs on macOS/Linux; on Windows use Git Bash or WSL.
# This tool never modifies your tar — it only loads and runs it locally.

set -u

CONTAINER_PORT=8080
HEALTH_PATH="/actuator/health"
TIMEOUT=60
KEEP=0
ENV_ARGS=()
TAR=""

RED=""; GREEN=""; YELLOW=""; BOLD=""; RESET=""
if [ -t 1 ]; then
    RED=$'\033[31m'; GREEN=$'\033[32m'; YELLOW=$'\033[33m'; BOLD=$'\033[1m'; RESET=$'\033[0m'
fi

ok()   { echo "${GREEN}  OK${RESET}    $*"; }
warn() { echo "${YELLOW}  WARN${RESET}  $*"; WARNINGS=$((WARNINGS + 1)); }
fail() { echo "${RED}  FAIL${RESET}  $*"; }
step() { echo; echo "${BOLD}[$1/5]${RESET} $2"; }

usage() { sed -n '2,24p' "$0" | sed 's/^# \{0,1\}//'; exit 1; }

while [ $# -gt 0 ]; do
    case "$1" in
        -e)        ENV_ARGS+=("-e" "$2"); shift 2 ;;
        --port)    CONTAINER_PORT="$2"; shift 2 ;;
        --health)  HEALTH_PATH="$2"; shift 2 ;;
        --timeout) TIMEOUT="$2"; shift 2 ;;
        --keep)    KEEP=1; shift ;;
        -h|--help) usage ;;
        -*)        echo "unknown option: $1"; usage ;;
        *)         TAR="$1"; shift ;;
    esac
done

[ -n "$TAR" ] || usage
WARNINGS=0
CONTAINER="submission-check-$$"
IMAGE=""

cleanup() {
    if [ "$KEEP" -eq 0 ] && [ -n "$CONTAINER" ]; then
        docker rm -f "$CONTAINER" >/dev/null 2>&1 || true
    fi
}
trap cleanup EXIT

# ---------------------------------------------------------------- 1: the file
step 1 "submission file"
if [ ! -f "$TAR" ]; then
    fail "file not found: $TAR"
    exit 1
fi
BASE=$(basename "$TAR")
case "$BASE" in
    ilp_submission_image.tar|acp_submission_image.tar)
        ok "file name '$BASE' matches what the marker expects" ;;
    *)
        warn "file is named '$BASE' — the marker expects 'ilp_submission_image.tar' / 'acp_submission_image.tar' (check your spec)" ;;
esac
SIZE=$(du -h "$TAR" | cut -f1)
ok "size: $SIZE"

# ------------------------------------------------------------- 2: docker load
step 2 "docker load (the marker loads your tar into a clean daemon)"
if ! docker version >/dev/null 2>&1; then
    fail "docker is not running / not installed"
    exit 1
fi
LOAD_OUTPUT=$(docker load -i "$TAR" 2>&1)
echo "$LOAD_OUTPUT" | sed 's/^/        /'
IMAGES=$(echo "$LOAD_OUTPUT" | sed -n 's/^Loaded image: //p')
IMAGE_IDS=$(echo "$LOAD_OUTPUT" | sed -n 's/^Loaded image ID: //p')
COUNT=$(echo "$IMAGES" | grep -c . || true)
if [ "$COUNT" -eq 0 ]; then
    if [ -n "$IMAGE_IDS" ]; then
        fail "the tar contains an image WITHOUT a name/tag — the marker cannot run it. Build with 'docker build -t <name> .' and save with 'docker save <name> -o $BASE'"
    else
        fail "no image could be loaded from the tar"
    fi
    exit 1
elif [ "$COUNT" -gt 1 ]; then
    fail "the tar contains $COUNT images — the marker expects exactly one:"
    echo "$IMAGES" | sed 's/^/        /'
    exit 1
fi
IMAGE=$(echo "$IMAGES" | head -1)
ok "loaded exactly one image: $IMAGE"

# ------------------------------------------------------------ 3: exposed port
step 3 "exposed port ${CONTAINER_PORT}/tcp"
EXPOSED=$(docker image inspect --format '{{json .Config.ExposedPorts}}' "$IMAGE" 2>/dev/null)
if echo "$EXPOSED" | grep -q "\"${CONTAINER_PORT}/tcp\""; then
    ok "image exposes ${CONTAINER_PORT}/tcp"
else
    warn "image does not declare EXPOSE ${CONTAINER_PORT} (found: ${EXPOSED:-none}) — add 'EXPOSE ${CONTAINER_PORT}' to your Dockerfile"
fi

# ------------------------------------------------------- 4: run the container
step 4 "starting the container (published to a dynamic host port, like the marker)"
if ! docker run -d --name "$CONTAINER" "${ENV_ARGS[@]+"${ENV_ARGS[@]}"}" -p "127.0.0.1:0:${CONTAINER_PORT}" "$IMAGE" >/dev/null 2>&1; then
    fail "the container could not be started"
    exit 1
fi
sleep 1
if [ "$(docker inspect --format '{{.State.Running}}' "$CONTAINER" 2>/dev/null)" != "true" ]; then
    fail "the container exited immediately — last log lines:"
    docker logs --tail 25 "$CONTAINER" 2>&1 | sed 's/^/        /'
    exit 1
fi
HOST_PORT=$(docker port "$CONTAINER" "${CONTAINER_PORT}/tcp" 2>/dev/null | head -1 | sed 's/.*://')
if [ -z "$HOST_PORT" ]; then
    fail "Docker could not publish container port ${CONTAINER_PORT} — is your service binding to it?"
    exit 1
fi
ok "container running, port ${CONTAINER_PORT} published on localhost:${HOST_PORT}"

# --------------------------------------------------------------- 5: health
step 5 "waiting for GET ${HEALTH_PATH} (timeout ${TIMEOUT}s)"
URL="http://localhost:${HOST_PORT}${HEALTH_PATH}"
DEADLINE=$(( $(date +%s) + TIMEOUT ))
HEALTH_RESULT="unreachable"
BODY=""
while [ "$(date +%s)" -lt "$DEADLINE" ]; do
    BODY=$(curl -s -m 2 -w '\n%{http_code}' "$URL" 2>/dev/null) || { sleep 1; continue; }
    HTTP_CODE=$(echo "$BODY" | tail -1)
    BODY=$(echo "$BODY" | sed '$d')
    if [ "$HTTP_CODE" = "200" ] && echo "$BODY" | grep -qi '"status"[[:space:]]*:[[:space:]]*"UP"'; then
        HEALTH_RESULT="up"
        break
    fi
    if [ -n "$HTTP_CODE" ] && [ "$HTTP_CODE" != "000" ]; then
        HEALTH_RESULT="responded:$HTTP_CODE"
    fi
    sleep 1
done

case "$HEALTH_RESULT" in
    up)
        ok "service is healthy: 200 $BODY" ;;
    responded:*)
        warn "service answered ${HEALTH_RESULT#responded:} with: ${BODY:-<empty>}"
        warn "that usually means a backing service is missing locally (RabbitMQ/Redis/LocalStack — start your docker compose rig); the marker provides them" ;;
    unreachable)
        fail "no answer on ${URL} within ${TIMEOUT}s — last container log lines:"
        docker logs --tail 25 "$CONTAINER" 2>&1 | sed 's/^/        /'
        exit 1 ;;
esac

# ----------------------------------------------------------------- summary
echo
if [ "$KEEP" -eq 1 ]; then
    echo "container kept running: http://localhost:${HOST_PORT}  (stop with: docker rm -f $CONTAINER)"
fi
if [ "$WARNINGS" -gt 0 ]; then
    echo "${YELLOW}${BOLD}RESULT: PASSED WITH ${WARNINGS} WARNING(S)${RESET} — read the WARN lines above before submitting."
else
    echo "${GREEN}${BOLD}RESULT: PASSED${RESET} — the tar loads, runs and answers health exactly as the marker expects."
fi
exit 0
