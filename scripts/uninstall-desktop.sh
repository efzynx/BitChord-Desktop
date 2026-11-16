#!/usr/bin/env bash
set -euo pipefail

echo "=== BitChord Linux Desktop Uninstaller ==="

PREFIX="${HOME}/.local"
BIN_DIR="${PREFIX}/bin"
APP_DIR="${PREFIX}/share/bitchord"
DESKTOP_DIR="${PREFIX}/share/applications"
ICON_DIR_512="${PREFIX}/share/icons/hicolor/512x512/apps"
ICON_DIR_SCALABLE="${PREFIX}/share/icons/hicolor/scalable/apps"

echo "Menghapus instalasi BitChord..."

rm -f "$BIN_DIR/bitchord"
rm -rf "$APP_DIR"
rm -f "$DESKTOP_DIR/bitchord.desktop"
rm -f "$ICON_DIR_512/bitchord.png"
rm -f "$ICON_DIR_SCALABLE/bitchord.svg"

if command -v update-desktop-database >/dev/null 2>&1; then
    update-desktop-database "$DESKTOP_DIR" || true
fi
if command -v gtk-update-icon-cache >/dev/null 2>&1; then
    gtk-update-icon-cache -f -t "${PREFIX}/share/icons/hicolor" || true
fi

echo "✅ BitChord berhasil dihapus dari sistem."
