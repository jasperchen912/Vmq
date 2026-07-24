#!/usr/bin/env bash
set -Eeuo pipefail

database_path="${VMQ_H2_DATABASE_PATH:-/data/mq}"
backup_root="${VMQ_H2_BACKUP_DIR:-/backup}"
database_user="${VMQ_H2_USER:-sa}"
database_password="${VMQ_H2_PASSWORD:-}"
legacy_jar="/opt/h2/h2-1.4.197.jar"
current_jar="/opt/h2/h2-2.3.232.jar"
database_file="${database_path}.mv.db"
timestamp="$(date -u +%Y%m%dT%H%M%SZ)"
snapshot_dir="${backup_root}/vmq-h2-${timestamp}"
legacy_file_in_volume="${database_file}.h2-1.4-${timestamp}"
script_file="${snapshot_dir}/vmq-h2-1.4.sql"
count_query="SELECT CONCAT('PAY_ORDER=', (SELECT COUNT(*) FROM PAY_ORDER), ';PAY_QRCODE=', (SELECT COUNT(*) FROM PAY_QRCODE), ';SETTING=', (SELECT COUNT(*) FROM SETTING), ';TMP_PRICE=', (SELECT COUNT(*) FROM TMP_PRICE)) AS VMQ_COUNTS"
moved_legacy_file=0

if [[ ! -f "${database_file}" ]]; then
    echo "Legacy H2 file not found: ${database_file}" >&2
    exit 2
fi

mkdir -p "${snapshot_dir}"
database_uid="$(stat -c '%u' "${database_file}")"
database_gid="$(stat -c '%g' "${database_file}")"
cp -p "${database_file}" "${snapshot_dir}/mq.mv.db.original"

read_counts() {
    local jar_path="$1"
    java -cp "${jar_path}" org.h2.tools.Shell \
        -url "jdbc:h2:${database_path}" \
        -user "${database_user}" \
        -password "${database_password}" \
        -sql "${count_query}" \
        | tr -d '\r' \
        | grep '^PAY_ORDER='
}

rollback() {
    local exit_code=$?
    trap - ERR INT TERM
    if [[ "${moved_legacy_file}" -eq 1 ]]; then
        if [[ -f "${database_file}" ]]; then
            mv "${database_file}" "${snapshot_dir}/mq.mv.db.failed-h2-2.3"
        fi
        if [[ -f "${legacy_file_in_volume}" ]]; then
            mv "${legacy_file_in_volume}" "${database_file}"
        else
            cp -p "${snapshot_dir}/mq.mv.db.original" "${database_file}"
        fi
    fi
    echo "H2 conversion failed; the 1.4 database was restored. Evidence: ${snapshot_dir}" >&2
    exit "${exit_code}"
}
trap rollback ERR INT TERM

old_counts="$(read_counts "${legacy_jar}")"
printf '%s\n' "${old_counts}" > "${snapshot_dir}/counts-before.txt"

java -cp "${legacy_jar}" org.h2.tools.Script \
    -url "jdbc:h2:${database_path}" \
    -user "${database_user}" \
    -password "${database_password}" \
    -script "${script_file}"

mv "${database_file}" "${legacy_file_in_volume}"
moved_legacy_file=1

java -cp "${current_jar}" org.h2.tools.RunScript \
    -url "jdbc:h2:${database_path}" \
    -user "${database_user}" \
    -password "${database_password}" \
    -script "${script_file}"

new_counts="$(read_counts "${current_jar}")"
printf '%s\n' "${new_counts}" > "${snapshot_dir}/counts-after.txt"

if [[ "${old_counts}" != "${new_counts}" ]]; then
    echo "Row-count validation failed: ${old_counts} != ${new_counts}" >&2
    false
fi

chown "${database_uid}:${database_gid}" "${database_file}"
chmod --reference="${legacy_file_in_volume}" "${database_file}"
sync

trap - ERR INT TERM
echo "H2 conversion succeeded: ${old_counts}"
echo "Original database retained at: ${legacy_file_in_volume}"
echo "External snapshot and SQL retained at: ${snapshot_dir}"
