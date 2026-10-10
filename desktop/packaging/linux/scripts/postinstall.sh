#!/bin/sh
systemctl disable --now frkn.service >/dev/null 2>&1 || true
systemctl daemon-reload >/dev/null 2>&1 || true
systemctl enable erkin.service >/dev/null 2>&1 || true
systemctl restart erkin.service >/dev/null 2>&1 || true
