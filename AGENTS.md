# Lumigram — Telegram for Android форк с плагинами на Python/C++

## Проект

Lumigram — форк exteraGram (Telegram for Android v12.6.4). Цель: платформа для плагинов на Python и C++ с системой разрешений, SUDO/root-доступом, Secure Vault и Lumi Store.

- **Java/Kotlin**: Android SDK 36, AGP 9.1.0, Java 21
- **C++**: NDK r27.3, CMake 4.0+, C++17, Android STL `c++_static`
- **Native lib**: `liblumi.49.so` (имя в Java: `lumi.49`)
- **Минимальная API**: 23
- **Архитектуры**: arm64-v8a (основная), armeabi-v7a

## Сборка

```bash
# Полная сборка debug APK (arm64-v8a)
./gradlew assembleDebug

# Только native (C++) библиотеки
./gradlew :TMessagesProj:externalNativeBuildDebug

# Только модуль приложения
./gradlew :TMessagesProj_App:assembleDebug

# Unit-тесты
./gradlew :TMessagesProj_AppTests:testDebugUnitTest
```

## Структура native кода

```
TMessagesProj/jni/
├── CMakeLists.txt              # Главный CMake (lumi.49 shared lib)
├── lumigram_core.cpp/.h        # JNI мост для C++ плагинов (NativePluginBridge)
├── lumigram_plugin_api.h       # Публичный API для C++ плагинов
├── plugin_example.cpp          # Пример C++ плагина
├── jni.c                       # Основной JNI слой Telegram (AES, crypto)
├── colorado/                   # Anti-tamper (check_signature() теперь return true)
│   ├── colorado.cpp
│   └── colorado.h
├── tgnet/                      # MTProto networking (статическая библиотека)
├── opus/                       # Аудиокодек (встроенная компиляция)
├── ffmpeg/                     # Предсобранные .a библиотеки
├── boringssl/                  # Криптография (предсобранная)
├── voip/                       # tgcalls/tgvoip (WebRTC)
└── third_party/                # libyuv, openh264
```

## Java плагинная система

- `com.lumigram.messenger.plugins.*` — все классы системы плагинов
- `NativePluginBridge.java` — JNI bridge для C++ плагинов (dlopen)
- `PluginManager.java` — жизненный цикл плагинов
- `PermissionManager.java` — 22 разрешения (NORMAL/SENSITIVE/DANGEROUS)
- `PluginLogger.java` — SQLite логгер (10000 записей)
- `SecureVault.java` — AES-256-GCM через Android Keystore
- `SudoManager.java` — root/Shizuku выполнение команд
- `PluginArchiveParser.java` — парсинг .plugin архивов
- `BasePlugin.java` — хуки: onPluginLoad, onUpdateHook, onMessageSend и др.

## C++ плагины (NativePluginBridge)

JNI сигнатуры (Java → C++):
- `nativePing()` → `Java_com_lumigram_messenger_plugins_NativePluginBridge_nativePing`
- `nativeLoadPlugin(String,String,String)` → `JNICALL jlong` (возвращает handle)
- `nativeCallOnLoad(long)` → `JNICALL jboolean`
- `nativeCallOnUnload(long)` → `JNICALL jboolean`
- `nativeUnloadPlugin(long)` → `JNICALL void`

API для C++ плагинов (`lumigram_plugin_api.h`):
- `lumigram_plugin_init(info)` — инициализация (обязательно)
- `lumigram_plugin_on_load()` — загрузка
- `lumigram_plugin_on_unload()` — выгрузка
- `lumigram_plugin_on_update(name, account, json)` — апдейт

## Правила для AI

1. **Перед коммитом** проверить `git status`, `git diff`, изучить историю коммитов.
2. **Не менять** конфигурацию сборки без явного запроса
3. **Код**: C++17, комментарии на английском (минимум), следовать существующему стилю
4. **JNI**: проверять соответствие сигнатур Java ↔ C++ при любых изменениях
5. **Баги**: сначала найти причину, потом фиксить. Не гадать.
6. **CMakeLists.txt**: если добавляешь/убираешь .cpp файлы — обновляй CMakeLists.txt
7. **Build**: всегда запускать `./gradlew :TMessagesProj:externalNativeBuildDebug` после изменения C++ кода для проверки компиляции
