#!/bin/sh
set -e
systemctl disable --now frkn.service >/dev/null 2>&1 || true
rm -f /etc/systemd/system/frkn.service
rm -rf /usr/local/lib/frkn-service /var/lib/frkn
if [ -f /etc/systemd/system/erkin.service ]; then
    systemctl disable --now erkin.service >/dev/null 2>&1 || true
    rm -f /etc/systemd/system/erkin.service /var/lib/erkin/app-dir
    rm -rf /usr/local/lib/erkin-service
fi
systemctl stop erkin.service >/dev/null 2>&1 || true
rm -f /etc/systemd/system/multi-user.target.wants/erkin.service
systemctl daemon-reload
systemctl enable erkin.socket
systemctl restart erkin.socket
