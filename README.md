# VapeV4-Better

Vape V4 open source, com Click GUI funcionando e configurações salvando.

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
