#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

echo "=== BitChord Linux Desktop Integration Installer ==="

APP_SOURCE="$ROOT_DIR/desktopApp/build/compose/binaries/main/app/BitChord"
ICON_PNG="$ROOT_DIR/desktopApp/packaging/icons/AppIcon.png"
ICON_SVG="$ROOT_DIR/desktopApp/src/main/composeResources/drawable/logo.svg"
DESKTOP_SRC="$ROOT_DIR/desktopApp/packaging/linux/bitchord.desktop"

# Check if application has been built
if [ ! -f "$APP_SOURCE/bin/BitChord" ]; then
    echo "Distribusi BitChord belum ditemukan di $APP_SOURCE"
    echo "Memulai proses build dengan Gradle..."
    (cd "$ROOT_DIR" && ./gradlew --no-daemon :desktopApp:createDistributable)
fi

PREFIX="${HOME}/.local"
BIN_DIR="${PREFIX}/bin"
APP_DIR="${PREFIX}/share/bitchord"
DESKTOP_DIR="${PREFIX}/share/applications"
ICON_DIR_512="${PREFIX}/share/icons/hicolor/512x512/apps"
ICON_DIR_SCALABLE="${PREFIX}/share/icons/hicolor/scalable/apps"

mkdir -p "$BIN_DIR" "$DESKTOP_DIR" "$ICON_DIR_512" "$ICON_DIR_SCALABLE"

echo "1. Memasang file aplikasi ke $APP_DIR..."
rm -rf "$APP_DIR"
mkdir -p "$APP_DIR"
cp -a "$APP_SOURCE/." "$APP_DIR/"

echo "2. Memasang wrapper executable di $BIN_DIR/bitchord..."
cat > "$BIN_DIR/bitchord" <<'EOF'
#!/bin/sh
exec "$HOME/.local/share/bitchord/bin/BitChord" "$@"
EOF
chmod +x "$BIN_DIR/bitchord"

echo "3. Memasang icon aplikasi..."
cp "$ICON_PNG" "$ICON_DIR_512/bitchord.png"
if [ -f "$ICON_SVG" ]; then
    cp "$ICON_SVG" "$ICON_DIR_SCALABLE/bitchord.svg"
fi

echo "4. Memasang desktop entry ke $DESKTOP_DIR/bitchord.desktop..."
sed -e "s|^Exec=bitchord|Exec=$BIN_DIR/bitchord|" \
    -e "s|^Icon=bitchord|Icon=$ICON_DIR_512/bitchord.png|" \
    "$DESKTOP_SRC" > "$DESKTOP_DIR/bitchord.desktop"
chmod +x "$DESKTOP_DIR/bitchord.desktop"

echo "5. Memperbarui desktop database..."
if command -v update-desktop-database >/dev/null 2>&1; then
    update-desktop-database "$DESKTOP_DIR" || true
fi
if command -v gtk-update-icon-cache >/dev/null 2>&1; then
    gtk-update-icon-cache -f -t "${PREFIX}/share/icons/hicolor" || true
fi

echo ""
echo "✅ Instalasi Berhasil!"
echo "BitChord sekarang sudah terdaftar di menu aplikasi Linux."
echo "Anda dapat menjalankannya lewat App Launcher (menu aplikasi) atau ketik: bitchord"
echo "Pastikan '$BIN_DIR' ada di PATH Anda jika ingin menjalankan dari terminal."
