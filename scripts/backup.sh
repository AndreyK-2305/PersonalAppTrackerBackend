#!/bin/sh
# Copia diaria de la base (formato custom de pg_dump, comprimido) en /backups. Conserva
# BACKUP_KEEP_DAYS días. La primera copia se hace al arrancar; después, cada 24 horas.
# Restaurar: pg_restore --clean --if-exists -d personal_tracker <archivo>.dump (ver la guía).
set -eu

umask 077
keep_days="${BACKUP_KEEP_DAYS:-14}"

while true; do
    file="/backups/${PGDATABASE}-$(date -u +%Y%m%d-%H%M%S).dump"
    if pg_dump --format=custom --file="${file}.tmp"; then
        mv "${file}.tmp" "${file}"
        echo "$(date -u +%FT%TZ) copia creada: $(basename "${file}") ($(du -h "${file}" | cut -f1))"
    else
        rm -f "${file}.tmp"
        echo "$(date -u +%FT%TZ) ERROR: no fue posible crear la copia" >&2
    fi
    find /backups -name "${PGDATABASE}-*.dump" -mtime "+${keep_days}" -delete
    sleep 86400
done
