#!/usr/bin/env bash
set -euo pipefail

REQUIRED_COMMANDS=(cat curl cut grep mkdir mktemp mv npx rm sha256sum sort tr wc)
for command in "${REQUIRED_COMMANDS[@]}"; do
    command -v "${command}" >/dev/null 2>&1 || {
        printf 'Required command is unavailable: %s\n' "${command}" >&2
        exit 1
    }
done

SCRIPT_PATH="${BASH_SOURCE[0]}"
SCRIPT_DIR="${SCRIPT_PATH%/*}"
if [[ "${SCRIPT_DIR}" == "${SCRIPT_PATH}" ]]; then
    SCRIPT_DIR="."
fi
ROOT_DIR="$(cd "${SCRIPT_DIR}/../.." && pwd)"
OUTPUT_PATH="${1:-${ROOT_DIR}/src/main/resources/map/world-map.svg}"
OUTPUT_NAME="${OUTPUT_PATH##*/}"
OUTPUT_DIR="${OUTPUT_PATH%/*}"
if [[ -z "${OUTPUT_NAME}" ]]; then
    printf 'Output path must name an SVG file\n' >&2
    exit 1
fi
if [[ "${OUTPUT_DIR}" == "${OUTPUT_PATH}" ]]; then
    OUTPUT_DIR="."
fi

MAPSHAPER_VERSION="0.7.61"
SOURCE_TAG="v5.1.2"
SOURCE_VERSION="5.1.1"
SOURCE_STEM="ne_10m_admin_0_countries_ukr"
SOURCE_BASE="https://raw.githubusercontent.com/nvkelso/natural-earth-vector/${SOURCE_TAG}/10m_cultural/${SOURCE_STEM}"
EXPECTED_REGION_COUNT="249"
EXPECTED_OUTPUT_SHA256="5d329b83c0f51b0996519f0fd62073aaf296a3f8013bdcdd73ec2efa80a79ee8"

mkdir -p "${OUTPUT_DIR}"
OUTPUT_DIR="$(cd "${OUTPUT_DIR}" && pwd)"
OUTPUT_PATH="${OUTPUT_DIR}/${OUTPUT_NAME}"
WORK_DIR="$(mktemp -d)"
OUTPUT_WORK_DIR="$(mktemp -d "${OUTPUT_DIR}/.${OUTPUT_NAME}.tmp.XXXXXX")"
cleanup() {
    rm -rf -- "${WORK_DIR}" "${OUTPUT_WORK_DIR}"
}
trap cleanup EXIT
SOURCE_DIR="${WORK_DIR}/source"
GENERATED_OUTPUT="${OUTPUT_WORK_DIR}/${OUTPUT_NAME}"
mkdir -p "${SOURCE_DIR}"

for extension in shp shx dbf prj VERSION.txt; do
    curl --fail --location --silent --show-error \
        "${SOURCE_BASE}.${extension}" \
        --output "${SOURCE_DIR}/${SOURCE_STEM}.${extension}"
done

cat >"${WORK_DIR}/source.sha256" <<'CHECKSUMS'
ec923b81a27e36efce7bb293c45ce87134f967330ef137d04e1be9c4940b6d82  ne_10m_admin_0_countries_ukr.shp
e5f658ee031f8492f109d4f46455f5171175bc0669b02c2e79be0438154d6647  ne_10m_admin_0_countries_ukr.shx
4fe3184c24f5cb7516aee1c20ebc6326a3ad6c2158cf844bcb5a1620c3670862  ne_10m_admin_0_countries_ukr.dbf
a02a27b1d1982c8516d83398e85a3c8b1aef1713c13ef4d84d7bde17430c07c4  ne_10m_admin_0_countries_ukr.prj
f9893302cd3158f3b5aea394dcd2a91574869e9e6ff69e9235b10a3bf8c983fb  ne_10m_admin_0_countries_ukr.VERSION.txt
CHECKSUMS
(
    cd "${SOURCE_DIR}"
    sha256sum --check --strict "${WORK_DIR}/source.sha256"
)

actual_version="$(tr -d '\r\n' <"${SOURCE_DIR}/${SOURCE_STEM}.VERSION.txt")"
if [[ "${actual_version}" != "${SOURCE_VERSION}" ]]; then
    printf 'Unexpected Natural Earth dataset version: %s\n' "${actual_version}" >&2
    exit 1
fi

npx --yes "mapshaper@${MAPSHAPER_VERSION}" \
    "${SOURCE_DIR}/${SOURCE_STEM}.shp" \
    -clean \
    -simplify weighted 15% keep-shapes \
    -proj robin \
    -each 'svg_id="region-"+ADM0_A3' \
    -o format=svg id-field=svg_id width=10000 "${GENERATED_OUTPUT}"

path_count="$({ grep -oE '<path[[:space:]]' "${GENERATED_OUTPUT}" || true; } | wc -l | tr -d '[:space:]')"
region_count="$({ grep -oE 'id="region-[^"]*"' "${GENERATED_OUTPUT}" || true; } | wc -l | tr -d '[:space:]')"
valid_region_count="$({
    grep -oE 'id="region-[^"]*"' "${GENERATED_OUTPUT}" \
        | grep -xE 'id="region-[A-Z0-9]{3}"' \
        || true
} | wc -l | tr -d '[:space:]')"
unique_region_count="$({
    grep -oE 'id="region-[^"]*"' "${GENERATED_OUTPUT}" || true
} | sort -u | wc -l | tr -d '[:space:]')"
if [[ "${path_count}" != "${EXPECTED_REGION_COUNT}" \
      || "${region_count}" != "${EXPECTED_REGION_COUNT}" \
      || "${valid_region_count}" != "${EXPECTED_REGION_COUNT}" \
      || "${unique_region_count}" != "${EXPECTED_REGION_COUNT}" ]]; then
    printf 'Expected %s valid unique SVG regions, found %s paths, %s region IDs, %s valid IDs, and %s unique IDs\n' \
        "${EXPECTED_REGION_COUNT}" \
        "${path_count}" \
        "${region_count}" \
        "${valid_region_count}" \
        "${unique_region_count}" >&2
    exit 1
fi

actual_output_sha256="$(sha256sum "${GENERATED_OUTPUT}" | cut -d ' ' -f 1)"
if [[ "${actual_output_sha256}" != "${EXPECTED_OUTPUT_SHA256}" ]]; then
    printf 'Unexpected world-map.svg SHA-256: %s (expected %s)\n' \
        "${actual_output_sha256}" "${EXPECTED_OUTPUT_SHA256}" >&2
    exit 1
fi

mv -- "${GENERATED_OUTPUT}" "${OUTPUT_PATH}"

printf 'Generated %s\n' "${OUTPUT_PATH}"
printf 'Natural Earth: %s %s, Ukraine POV\n' "${SOURCE_STEM}" "${SOURCE_VERSION}"
printf 'Mapshaper: %s; simplification: weighted 15%% keep-shapes; projection: Robinson\n' \
    "${MAPSHAPER_VERSION}"
printf 'Regions: %s unique; SHA-256: %s\n' "${unique_region_count}" "${actual_output_sha256}"
