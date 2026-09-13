# VapeV4-Better

# ENGLISH

VapeV4 is open source, with a working Click GUI and saved settings.

## How to use

1. Open Minecraft 1.8.9 (Lunar or Forge).

2. Double-click on `Vape/Vape421Injector.exe`, choose `javaw.exe`, and press Enter.

3. In the game, press RShift to open the menu.

Details in `Vape/LEIA-ME.txt` (Portuguese).

## Settings

Saved automatically in `%APPDATA%\Vape421\vape421-config.json` seconds after any change (module on/off, value, bind, profile) and loaded automatically upon injection. A backup (`vape421-config.bak.json`) is kept in the same folder. Works on any Windows 10/11 x64 PC, regardless of the machine where it was compiled.

## Compiling from scratch

- Windows 10/11 x64
- JDK 21 (to run Gradle 8.8) + JDK 17 (payload toolchain) + JDK 8 (native JNI headers)
- Visual Studio 2022 or 2026 with C++ x64 + CMake
- Inside `VapeV4.21-main source code`, run `.\gradlew.bat prepareInjectionBundle`
- The bundle will appear in `build/injection/` (`Vape421Native.dll` + `Vape421Injector.exe`)

- How I support the OpenSource community: I authorize you to copy, modify, and redistribute the Vapev4-Better code/UI in your Nexyre project, including in closed/obfuscated builds (I am not responsible for lawsuits or copyright infringement by third parties).

# PORTUGUESE (BRASIL)
VapeV4 open source, com Click GUI funcionando e configurações salvando.

## Como usar

1. Abra o Minecraft 1.8.9 (Lunar ou Forge).
2. Duplo-clique em `Vape/Vape421Injector.exe`, escolha o `javaw.exe`, Enter.
3. No jogo, aperte RShift para abrir o menu.

Detalhes em `Vape/LEIA-ME.txt`.

## Configurações

Salvas sozinhas em `%APPDATA%\Vape421\vape421-config.json` segundos depois de
qualquer mudança (módulo ligado/desligado, valor, bind, perfil) e carregadas
automaticamente ao injetar. Um backup (`vape421-config.bak.json`) é mantido na
mesma pasta. Funciona em qualquer PC com Windows 10/11 x64, sem depender da
máquina onde foi compilado.

## Compilar do zero

- Windows 10/11 x64
- JDK 21 (para rodar o Gradle 8.8) + JDK 17 (toolchain do payload) + JDK 8 (headers JNI do nativo)
- Visual Studio 2022 ou 2026 com C++ x64 + CMake
- Dentro de `VapeV4.21-main source code`, rode `.\gradlew.bat prepareInjectionBundle`
- O bundle sai em `build/injection/` (`Vape421Native.dll` + `Vape421Injector.exe`)

- como eu apoio a comunidade OpenSouce: Eu autorizo você a copiar, modificar e redistribuir o código/UI do Vapev4-Better no seu projeto Nexyre, inclusive em builds fechadas/ofuscadas (não me reponsabilizo por processos ou quebra de direitos autorais de terceiros)
