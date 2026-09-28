#!/bin/bash
set -e

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RUNNER="$DIR/ndsrecomp/runner/build-por/nds_runner"
CONFIG="$DIR/por_recomp/game.toml"
ROM="$DIR/Castlevania Portrait of Ruin.nds"

if [ ! -f "$RUNNER" ]; then
    echo "Erro: nds_runner não encontrado em $RUNNER"
    exit 1
fi

if [ ! -f "$ROM" ]; then
    echo "Erro: ROM não encontrada em $ROM"
    exit 1
fi

echo "=========================================================="
echo "Iniciando Castlevania: Portrait of Ruin (Recompilado)"
echo "=========================================================="
echo "Controles padrão:"
echo "  Z: Botão A"
echo "  X: Botão B"
echo "  S: Botão X"
echo "  A: Botão Y"
echo "  Q: Botão L"
echo "  W: Botão R"
echo "  Enter: Start"
echo "  Backspace: Select"
echo "  Setas: D-Pad"
echo "  Mouse: Tela Touch / Stylus (tela inferior)"
echo "=========================================================="

"$RUNNER" \
  --config "$CONFIG" \
  --freebios \
  --generated-firmware \
  --boot direct \
  --rom "$ROM" \
  --interactive \
  "$@"
