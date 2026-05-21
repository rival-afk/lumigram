# План разработки Lumigram

## Обзор

Lumigram — форк Telegram for Android, цель которого — создание открытой платформы для плагинов на Python и C++. Проект базируется на Telegram v12.6.4 (Android), Android SDK 36, NDK 27.3, CMake 4.0+.

---

## 1. Текущее состояние (что уже реализовано)

### Java-ядро плагинной системы (`com.lumigram.messenger.plugins`)

| Компонент | Файл | Статус |
|---|---|---|
| `PluginManager` — управление жизненным циклом плагинов (установка, загрузка, выгрузка, краши) | `PluginManager.java` | ✅ Реализован |
| `PluginController` — упрощённый контроллер (сканирование, прокси) | `PluginController.java` | ✅ Реализован |
| `PluginManifest` — модель манифеста (id, тип, permissions, требования) | `PluginManifest.java` | ✅ Реализован |
| `PluginArchiveParser` — парсинг `.plugin` архивов (JSON-манифест) | `PluginArchiveParser.java` | ✅ Реализован |
| `BasePlugin` — абстрактный базовый класс (хуки: onPluginLoad, onUpdateHook, onMessageHook) | `BasePlugin.java` | ✅ Реализован |
| `PluginLifecycle` — enum состояний (INSTALLED, LOADING, RUNNING, ERROR и т.д.) | `PluginLifecycle.java` | ✅ Реализован |
| `PluginSafeMode` — безопасный режим при краше плагина | `PluginSafeMode.java` | ✅ Реализован |
| `PythonRuntime` — обёртка над Chaquopy (запуск Python, pip install) | `PythonRuntime.java` | ✅ Реализован (требует Chaquopy) |
| `PythonPackageRegistry` — реестр Python-зависимостей | `PythonPackageRegistry.java` | ✅ Реализован |
| `NativePluginBridge` — JNI-мост для C++ плагинов (dlopen, вызов onLoad/onUnload) | `NativePluginBridge.java` | ✅ Реализован |
| `JavaDynamicProxyFactory` — создание динамических прокси | `proxy/JavaDynamicProxyFactory.java` | ✅ Реализован |
| `StaticProxyRegistry` — перехват статических методов | `proxy/StaticProxyRegistry.java` | ✅ Реализован |

### C++ ядро (`TMessagesProj/jni/`)

| Компонент | Файл | Статус |
|---|---|---|
| JNI-реализация NativePluginBridge: загрузка `.so` через dlopen | `lumigram_core.cpp` | ✅ Реализован |
| API плагина: init, on_load, on_unload, on_update | `lumigram_core.h` | ✅ Реализован |
| Интеграция в CMake: сборка как часть `liblumi.so` | `CMakeLists.txt` | ✅ Реализован |

### Инфраструктура сборки

- Gradle AGP 9.1.0, Java 21
- NDK r27.3, CMake 4.0+
- Сборка native библиотеки `lumi` (shared library)
- Статические библиотеки: tgnet, ffmpeg, opus, libyuv, boringssl, sqlite, rlottie, flac, voip (tgcalls/tgvoip)

---

## 2. Этапы разработки

### Этап 1: Доработка ядра плагинной системы ✅

**Приоритет: высокий**

- [x] Разделить `PluginManager` и `PluginController` — избавиться от дублирования
  - `PluginController` теперь делегирует всё `PluginManager.getInstance()`
  - Единый фильтр расширений через `PluginArchiveParser.isPluginFile()`
  - `PluginController` сокращён со 119 до 47 строк
- [x] Добавить валидацию подписи `.plugin` архива (ECDSA P-256)
  - Создан `PluginSignatureValidator.java` — верификация через `signature.sig` + `signer` в манифесте
  - Встроенный ключ `lumigram_official`, поддержка PEM-ключей
  - Интеграция в `installPlugin()` и `rescanInstalledPlugins()`
- [x] Реализовать очередь загрузки с приоритетами
  - `PriorityBlockingQueue<PluginLoadTask>` + фоновый тред `lumigram-loader`
  - Поле `priority` в `PluginManifest`, сортировка по убыванию
  - Методы `loadPlugin()` (асинхронно) и `loadPluginNow()` (синхронно)
- [x] Добавить поддержку `min_sdk` и `min_lumigram_version` с блокировкой неподходящих плагинов
  - Статический метод `PluginManager.checkPluginCompatibility()`
  - Версионное сравнение через семантическое разбиение
  - Проверка на Android API level
- [x] Тесты: юнит-тесты для PluginArchiveParser, PluginManifest, PluginLifecycle
  - `PluginManifestTest.java` — поля, типы, неизменяемость списков
  - `PluginLifecycleTest.java` — все состояния, fromString
  - `PluginSignatureValidatorTest.java` — алгоритмы, парсинг ключей
  - `PluginManagerCompatibilityTest.java` — minSdk, minLumigramVersion

### Этап 2: Интеграция Chaquopy (Python Runtime) ✅

**Приоритет: высокий**

- [x] Добавить Chaquopy в `build.gradle` как опциональную зависимость:
  - Плагин `com.chaquo.python` версии `16.0.0` добавлен в корневой `build.gradle` и `TMessagesProj/build.gradle`
  - Maven-репозиторий Chaquopy добавлен в `settings.gradle`
- [x] Настроить Chaquopy в `TMessagesProj/build.gradle`:
  ```groovy
  python {
      version = "3.11"
      pip { install "cryptography"; install "requests" }
  }
  ```
- [x] Доработать `PythonRuntime.createSession()` — поддержка изолированного venv
  - `injectPluginPath()` — изоляция `sys.path` для каждого плагина
  - `requirements.txt` → pip install в изолированную директорию
- [x] Перехват `import` для контроля доступа: подстановка директории плагина в `sys.path[0]`

### Этап 3: Permission System ✅

**Приоритет: высокий**

- [x] Создан `PermissionManager.java`:
  - 22 зарегистрированных разрешения с уровнями NORMAL / SENSITIVE / DANGEROUS
  - Маппинг строковых разрешений → Android Permissions (`checkSdkInt()`)
  - `PermissionDialogProvider` с разделением на required/optional
  - Хранение выданных разрешений в `SharedPreferences`
- [x] Интеграция в `PluginManager`:
  - Проверка разрешений перед загрузкой (в `loadPlugin`, `loadPluginNow`, loader thread)
  - Вызов `permissionManager.requestPermissions()` на этапе `PluginLifecycle.LOADING`
  - Отказ → `PluginLifecycle.DISABLED`
- [x] Поддержка отзыва разрешений (`revoke`, `revokeAll` при удалении плагина)
- [x] Реестр разрешений доступен через `PermissionManager.getRegistry()`

### Этап 4: Логирование и Crash Handler ✅

**Приоритет: средний**

- [x] Создан `PluginLogger.java`:
  - SQLite-хранилище (через `org.telegram.SQLite.SQLiteDatabase`)
  - 5 уровней: DEBUG, INFO, WARNING, ERROR, FATAL
  - 8 категорий: PLUGIN_LOADER, PLUGIN_EXECUTION, PERMISSIONS, UI, NETWORK, SUDO, SECURITY, GENERAL
  - Каждая запись: id, timestamp, level, category, pluginId, message, stackTrace
  - Автоtrim до 10000 записей
  - Фильтрация по level, category, pluginId с пагинацией
- [x] Интеграция в `PluginManager.handlePluginCrash()`:
  - Автоматическое логирование краша с full stacktrace
  - Категория `PLUGIN_EXECUTION`, уровень `ERROR`
- [x] `PluginLogger.formatEntry()` — форматирование для UI и copy-to-clipboard

### Этап 5: Совместимость с exteraGram ✅

**Приоритет: средний**

- [x] Имплементированы все хуки API exteraGram в `BasePlugin`:
  - `onPluginLoad()`, `onPluginUnload()`, `onUpdateHook()` — базовые
  - `onMessageSend(account, message)` — перехват отправки сообщений
  - `onMessageReceived(account, message)` — перехват получения сообщений
  - `onChatOpen(account, chatId)` / `onChatClose(account, chatId)` — открытие/закрытие чата
  - `onFileDownload(account, file)` — события скачивания файлов
  - `onCallEvent(account, event, call)` — события звонков
  - `onButtonClick(account, button)` — клики по кнопкам
- [x] Поддержка формата `.explug`:
  - Расширение `.explug` в списке распознаваемых (`PluginArchiveParser.PLUGIN_EXTENSIONS`)
  - Манифест `explug.json` в списке кандидатов
- [x] Динамические прокси (`JavaDynamicProxyFactory`) и статический перехват (`StaticProxyRegistry`) для совместимости с API перехвата exteraGram

### Этап 6: Защищённое хранилище (Secure Vault) ✅

**Приоритет: средний**

- [x] Создан `SecureVault.java`:
  - Шифрование AES-256-GCM через Android Keystore (основной)
  - Fallback на файловый ключ при отсутствии Keystore
  - Каждый плагин имеет изолированный `.vault` файл
  - API: `get(pluginId, key)`, `set(pluginId, key, value)`, `delete(pluginId, key)`, `clearPluginData(pluginId)`
- [x] Интеграция в `PluginManager.initialize()`
- [x] Разрешение `lumigram.secret_storage` зарегистрировано в `PermissionManager`

### Этап 7: SUDO / Shizuku интеграция ✅

**Приоритет: низкий**

- [x] Создан `SudoManager.java`:
  - `execute(command)` — синхронное выполнение привилегированной команды
  - `executeAsync(command, callback)` — асинхронное выполнение с колбэком
  - Автоматическое определение root-доступа (проверка `su` + известные пути)
  - Таймаут выполнения (30с по умолчанию)
  - `Backend.ROOT` / `Backend.SHIZUKU` / `Backend.NONE`
- [x] Интеграция в `PluginManager.initialize()`
- [x] Разрешения: `sudo.root_command`, `sudo.shizuku_api`, `sudo.gpu_access`, `sudo.network_capture` зарегистрированы в `PermissionManager`

### Этап 8: C++ плагины — шаблоны и документация ✅

**Приоритет: низкий**

- [x] Создан заголовочный файл `TMessagesProj/jni/lumigram_plugin_api.h`:
  - `LumigramPluginInfo` — структура с информацией о плагине
  - `lumigram_plugin_init`, `on_load`, `on_unload`, `on_update` — точки входа
  - `LUMIGRAM_PLUGIN_EXPORT` — макрос для экспорта символов
- [x] Создан пример плагина `TMessagesProj/jni/plugin_example.cpp`:
  - Полная имплементация всех 4 функций API
  - Логирование через `android/log.h`
- [x] Создан `CMakeLists_plugin_example.txt` — шаблон сборки:
  - Поддержка arm64-v8a и armeabi-v7a
  - Оптимизации NEON для ARM
  - Линковка с `log` и `android`
- [x] Формат .plugin для C++:
  ```
  my_plugin.plugin/
  ├── manifest.json   # type: "cpp", cpp_library: "plugin.so"
  └── plugin.so       # скомпилированная библиотека
  ```

### Этап 9: Lumi Store ✅

**Приоритет: низкий**

- [x] Создан `LumiStore.java` (клиент):
  - `fetchListings(callback)` — получение списка плагинов из репозитория (JSON API)
  - `downloadPlugin(context, listing, callback)` — скачивание с прогрессом
  - Автоматическая установка через `PluginManager.installPlugin()`
  - Формат API: `GET /api/v1/list` → `{ plugins: [{ id, name, version, download_url, ... }] }`
  - Конфигурируемый URL хранилища (`setStoreUrl()`)

---

## 3. Архитектура сборки C++ (текущая)

### Структура JNI

```
TMessagesProj/jni/
├── CMakeLists.txt              # Главный CMake (648 строк)
├── lumigram_core.cpp           # JNI мост для плагинов (NativePluginBridge)
├── lumigram_core.h             # Заголовок с API плагина
├── jni.c                       # Основной JNI слой Telegram
├── tgnet/                      # MTProto/net (статическая библиотека)
├── opus/                       # Аудиокодек
├── ffmpeg/                     # Видео (предсобранные .a)
├── boringssl/                  # Криптография
├── rlottie/                    # Анимации Lottie
├── sqlite/                     # База данных
├── voip/                       # Голосовые звонки (tgcalls/tgvoip)
├── third_party/libyuv/         # Обработка изображений
└── ...                         # Прочие зависимости
```

### Цепочка сборки native кода

1. Gradle → CMake (через `externalNativeBuild`)
2. CMake → компиляция `liblumi.so` (shared library)
3. `liblumi.so` линкуется со статическими библиотеками (tgnet, ffmpeg, opus, sqlite, etc.)
4. JNI функции в `lumigram_core.cpp` регистрируют `NativePluginBridge`
5. C++ плагины загружаются через `dlopen()` внутри `lumigram_core.cpp`

### Параметры сборки (из CMakeLists.txt)

| Параметр | Значение |
|---|---|
| C++ Standard | C++17 |
| C Standard | C11 |
| Android STL | `c++_static` |
| Min API | 23 |
| NDK | 27.3.13750724 |
| CMake | 4.0.0+ |
| Флаги | `-ffast-math -Os -funroll-loops -fno-strict-aliasing` |

---

## 4. Формат .plugin (подробно)

```
plugin_id.plugin (zip-архив)
├── manifest.json
├── main.py                  # для Python-плагинов
├── plugin.so                # для C++-плагинов
├── plugin_x86.so            # опционально
├── libs/
│   ├── helper.py
│   └── ...
├── assets/
│   ├── icon.png
│   └── ...
└── requirements.txt         # Python зависимости (pip)
```

**manifest.json:**
```json
{
    "id": "plugin_id",
    "name": "Plugin Name",
    "version": "1.0.0",
    "author": "@username",
    "description": "Description",
    "min_sdk": 26,
    "min_lumigram_version": "1.0.0",
    "type": "python",
    "entrypoint": "main.py",
    "cpp_library": "plugin.so",
    "requirements": "requirements.txt",
    "permissions": ["telegram.read_messages"],
    "optional_permissions": ["sudo.root_command"],
    "modules": ["libs/helper.py"]
}
```

---

## 5. Разрешения (модель безопасности)

### Telegram API
- `telegram.read_messages` — чтение сообщений
- `telegram.send_messages` — отправка сообщений
- `telegram.read_contacts` — чтение контактов
- `telegram.modify_chats` — управление чатами
- `telegram.call` — звонки
- `telegram.read_stories` — сторис
- `telegram.delete_messages` — удаление сообщений

### Android / Система
- `android.camera`, `android.microphone`, `android.location`
- `android.storage.read`, `android.storage.write`, `android.storage.system`

### SUDO (привилегированные)
- `sudo.root_command` — выполнение команд от root
- `sudo.shizuku_api` — доступ к Shizuku API
- `sudo.network_capture` — перехват трафика
- `sudo.gpu_access` — доступ к GPU

### Специальные Lumigram
- `lumigram.ghost_mode` — скрытие онлайна
- `lumigram.spy_mode` — сохранение удалённых сообщений
- `lumigram.secret_storage` — защищённое хранилище
- `lumigram.external_network` — внешние сетевые соединения

---

## 6. Принципы

1. **Полная совместимость с плагинами exteraGram** — все хуки и API
2. **Безопасность по умолчанию** — новый плагин не имеет прав
3. **Отказоустойчивость** — падение плагина не убивает клиент (safe mode)
4. **Модульное ядро** — слабая связанность компонентов
5. **Изоляция Python** — виртуальное окружение + контроль import
6. **C++ плагины** — dlopen, динамическая загрузка, доступ к TDLib
