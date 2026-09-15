#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
OUTPUT_PATH="${1:-${ROOT_DIR}/src/main/resources/map/world-map.svg}"
MAPSHAPER_VERSION="0.7.61"
SOURCE_TAG="v5.1.2"
SOURCE_VERSION="5.1.1"
SOURCE_STEM="ne_10m_admin_0_countries_ukr"
SOURCE_BASE="https://raw.githubusercontent.com/nvkelso/natural-earth-vector/${SOURCE_TAG}/10m_cultural/${SOURCE_STEM}"
EXPECTED_REGION_COUNT="249"
EXPECTED_OUTPUT_SHA256="5d329b83c0f51b0996519f0fd62073aaf296a3f8013bdcdd73ec2efa80a79ee8"

for command in curl npx sha256sum grep sort wc cut tr; do
    command -v "${command}" >/dev/null 2>&1 || {
        printf 'Required command is unavailable: %s\n' "${command}" >&2
        exit 1
    }
done

WORK_DIR="$(mktemp -d)"
trap 'rm -rf "${WORK_DIR}"' EXIT
SOURCE_DIR="${WORK_DIR}/source"
mkdir -p "${SOURCE_DIR}" "$(dirname "${OUTPUT_PATH}")"

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
    -o format=svg id-field=svg_id width=10000 "${OUTPUT_PATH}"

region_count="$(grep -o 'id="region-[^"]*"' "${OUTPUT_PATH}" | wc -l)"
unique_region_count="$(grep -o 'id="region-[^"]*"' "${OUTPUT_PATH}" | sort -u | wc -l)"
if [[ "${region_count}" != "${EXPECTED_REGION_COUNT}" || \
      "${unique_region_count}" != "${EXPECTED_REGION_COUNT}" ]]; then
    printf 'Expected %s unique SVG regions, found %s paths and %s unique IDs\n' \
        "${EXPECTED_REGION_COUNT}" "${region_count}" "${unique_region_count}" >&2
    exit 1
fi

actual_output_sha256="$(sha256sum "${OUTPUT_PATH}" | cut -d ' ' -f 1)"
if [[ "${actual_output_sha256}" != "${EXPECTED_OUTPUT_SHA256}" ]]; then
    printf 'Unexpected world-map.svg SHA-256: %s (expected %s)\n' \
        "${actual_output_sha256}" "${EXPECTED_OUTPUT_SHA256}" >&2
    exit 1
fi

printf 'Generated %s\n' "${OUTPUT_PATH}"
printf 'Natural Earth: %s %s, Ukraine POV\n' "${SOURCE_STEM}" "${SOURCE_VERSION}"
printf 'Mapshaper: %s; simplification: weighted 15%% keep-shapes; projection: Robinson\n' \
    "${MAPSHAPER_VERSION}"
printf 'Regions: %s unique; SHA-256: %s\n' "${unique_region_count}" "${actual_output_sha256}"
