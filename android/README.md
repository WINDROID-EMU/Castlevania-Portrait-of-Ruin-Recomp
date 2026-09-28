# Castlevania: Portrait of Ruin — Android Subproject

Este diretório contém o módulo de aplicativo Android para o port nativo de **Castlevania: Portrait of Ruin**, utilizando a ferramenta de recompilação [ndsrecomp](https://github.com/RetroPortingToolKit/ndsrecomp).

---

## 📁 Estrutura de Diretórios

- **`app/src/main/java/`**: Código-fonte Java do aplicativo.
  - `TitleActivity.java`: Tela inicial com animações de entrada, verificação e seletor de arquivos de ROM via Android Storage Access Framework (SAF).
  - `MainActivity.java`: Atividade principal baseada em `SDLActivity` que carrega as bibliotecas nativas C++ (`SDL2` e `nds_runner`).
  - `VirtualControlsOverlay.java`: Camada transparente desenhada sobre o jogo contendo D-Pad, botões táteis (A, B, X, Y, L, R, Switch, Map) e manipulação multi-touch.
  - `SettingsDialog.java`: Diálogo de opções para personalização da opacidade dos botões, layout e preferências.
- **`app/src/main/res/`**: Recursos de layout XML, ícones do aplicativo e artes visuais dos botões.
- **`app/src/main/cpp/`**: `CMakeLists.txt` que orquestra a compilação do runner nativo e da biblioteca SDL2.
- **`thirdparty/SDL2/`**: Biblioteca SDL2 embutida configurada para geração de shared libraries JNI.

---

## 🔧 Requisitos de Ambiente

- **Android SDK:** Compile SDK 34, Min SDK 24, Target SDK 34
- **Android NDK:** `26.1.10909125`
- **CMake:** `3.22.1`
- **Java:** JDK 17 (OpenJDK / Eclipse Temurin)
- **Gradle:** 8.5 (com Android Gradle Plugin 8.3.2)
- **Arquitetura Alvo:** `arm64-v8a`

---

## 🚀 Como Compilar via Linha de Comando

Para gerar o APK de Debug:
```bash
./gradlew assembleDebug
```

Para gerar o APK de Release:
```bash
./gradlew assembleRelease
```

O arquivo compilado estará em:
`app/build/outputs/apk/release/app-release.apk`

---

## 📱 Instalação no Dispositivo via ADB

Com a depuração USB ativada em seu aparelho Android:
```bash
adb install -r app/build/outputs/apk/release/app-release.apk
```
