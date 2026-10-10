#!/bin/sh
case "$1" in
    upgrade|1) exit 0 ;;
esac
systemctl daemon-reload >/dev/null 2>&1 || true
rm -rf /var/lib/erkin
