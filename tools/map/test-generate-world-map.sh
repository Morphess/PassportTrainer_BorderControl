#!/usr/bin/env bash
set -euo pipefail

REQUIRED_COMMANDS=(cat chmod cut mkdir mktemp rm sha256sum)
for command in "${REQUIRED_COMMANDS[@]}"; do
    command -v "${command}" >/dev/null 2>&1 || {
        printf 'Required test command is unavailable: %s\n' "${command}" >&2
        exit 1
    }
done

SCRIPT_PATH="${BASH_SOURCE[0]}"
SCRIPT_DIR="${SCRIPT_PATH%/*}"
if [[ "${SCRIPT_DIR}" == "${SCRIPT_PATH}" ]]; then
    SCRIPT_DIR="."
fi
ROOT_DIR="$(cd "${SCRIPT_DIR}/../.." && pwd)"
GENERATOR="${ROOT_DIR}/tools/map/generate-world-map.sh"
FIXTURE="${ROOT_DIR}/src/main/resources/map/world-map.svg"
EXPECTED_SHA256="5d329b83c0f51b0996519f0fd62073aaf296a3f8013bdcdd73ec2efa80a79ee8"
REAL_SHA256SUM="$(command -v sha256sum)"
WORK_DIR="$(mktemp -d)"
cleanup() {
    rm -rf -- "${WORK_DIR}"
}
trap cleanup EXIT
mkdir -p "${WORK_DIR}/bin"

cat >"${WORK_DIR}/bin/curl" <<'FAKE_CURL'
#!/usr/bin/env bash
set -euo pipefail
output=""
while (( $# > 0 )); do
    if [[ "$1" == "--output" ]]; then
        shift
        output="$1"
    fi
    shift
done
if [[ -z "${output}" ]]; then
    printf 'Fake curl received no output path\n' >&2
    exit 1
fi
if [[ "${output}" == *.VERSION.txt ]]; then
    printf '5.1.1\n' >"${output}"
else
    : >"${output}"
fi
FAKE_CURL

cat >"${WORK_DIR}/bin/npx" <<'FAKE_NPX'
#!/usr/bin/env bash
set -euo pipefail
destination="${!#}"
if [[ "${FAKE_MAPSHAPER_MODE}" == "valid" ]]; then
    cat "${MAP_FIXTURE}" >"${destination}"
else
    printf '<svg xmlns="http://www.w3.org/2000/svg"><path id="region-bad" d="M0 0Z"/></svg>\n' \
        >"${destination}"
fi
FAKE_NPX

cat >"${WORK_DIR}/bin/sha256sum" <<'FAKE_SHA'
#!/usr/bin/env bash
set -euo pipefail
if [[ "${1:-}" == "--check" ]]; then
    exit 0
fi
exec "${REAL_SHA256SUM}" "$@"
FAKE_SHA
chmod +x "${WORK_DIR}/bin/curl" "${WORK_DIR}/bin/npx" "${WORK_DIR}/bin/sha256sum"

export MAP_FIXTURE="${FIXTURE}"
export REAL_SHA256SUM
export PATH="${WORK_DIR}/bin:${PATH}"

happy_output="${WORK_DIR}/happy/world-map.svg"
FAKE_MAPSHAPER_MODE=valid "${GENERATOR}" "${happy_output}" >/dev/null
happy_sha256="$("${REAL_SHA256SUM}" "${happy_output}" | cut -d ' ' -f 1)"
if [[ "${happy_sha256}" != "${EXPECTED_SHA256}" ]]; then
    printf 'Happy-path generator output hash differs: %s\n' "${happy_sha256}" >&2
    exit 1
fi

failure_output="${WORK_DIR}/failure/world-map.svg"
mkdir -p "${failure_output%/*}"
cat "${FIXTURE}" >"${failure_output}"
before_sha256="$("${REAL_SHA256SUM}" "${failure_output}" | cut -d ' ' -f 1)"
if FAKE_MAPSHAPER_MODE=invalid "${GENERATOR}" "${failure_output}" >/dev/null 2>&1; then
    printf 'Generator accepted controlled invalid map output\n' >&2
    exit 1
fi
after_sha256="$("${REAL_SHA256SUM}" "${failure_output}" | cut -d ' ' -f 1)"
if [[ "${after_sha256}" != "${before_sha256}" ]]; then
    printf 'Failed generation modified the existing destination\n' >&2
    exit 1
fi

printf 'Generator happy path and failure safety verified\n'
