# Lumigram Plugin Development Guide

## Содержание
1. [Архитектура](#архитектура)
2. [Формат .plugin архива](#формат-plugin-архива)
3. [Python плагины](#python-плагины)
4. [C++ плагины](#c-плагины)
5. [Система разрешений](#система-разрешений)
6. [Хуки и события](#хуки-и-события)
7. [Secure Vault](#secure-vault)
8. [Sudo / Root](#sudo--root)
9. [Логирование](#логирование)
10. [Lumi Store](#lumi-store)
11. [Как дополнить это руководство](#как-дополнить-это-руководство)

---

## Архитектура

Lumigram поддерживает два типа плагинов:

| Тип | Runtime | Скорость | API |
|-----|---------|----------|-----|
| **Python** | Chaquopy (CPython embedded) | Средняя | Python + Java bridge |
| **C++** | Native .so через dlopen() | Высокая | C API (lumigram_plugin_api.h) |

**Жизненный цикл плагина:**
```
UNINSTALLED → INSTALLED → VALIDATING → LOADING → RUNNING
                                                    ↓
                                                 ERROR
                                               DISABLED
```

**Ключевые компоненты:**
- `com.lumigram.messenger.plugins.PluginManager` — центральный менеджер (синглтон)
- `com.lumigram.messenger.plugins.PluginController` — публичный фасад
- `com.lumigram.messenger.plugins.BasePlugin` — абстрактный базовый класс
- `com.lumigram.messenger.plugins.NativePluginBridge` — JNI мост для C++

---

## Формат .plugin архива

Плагин распространяется как ZIP-архив с расширением `.plugin`.

### Структура архива

```
myplugin.plugin
├── lumigram-plugin.json        # Манифест (обязательно)
├── plugin.py                   # Точка входа Python (для Python плагинов)
├── libplugin.so                # Скомпилированная библиотека (для C++ плагинов)
├── requirements.txt            # Python зависимости (опционально)
├── ...другие файлы...
└── signature.sig               # ECDSA подпись манифеста (опционально)
```

### Манифест (lumigram-plugin.json)

```json
{
  "id": "my_plugin",
  "name": "My Plugin",
  "version": "1.0.0",
  "author": "Your Name",
  "description": "What this plugin does",
  "type": "python",
  "entrypoint": "plugin:Plugin",
  "cpp_library": "libplugin.so",
  "min_sdk": 26,
  "min_lumigram_version": "1.0.0",
  "priority": 0,
  "signer": "lumigram_official",
  "signature_algo": "ecdsa-p256",
  "permissions": [
    "telegram.read_messages",
    "telegram.send_messages"
  ],
  "optional_permissions": [
    "android.location"
  ],
  "requirements": "requirements.txt",
  "python": {
    "requirements": ["requests==2.28.0"]
  }
}
```

**Поля:**
- `type` — `"python"` или `"cpp"`
- `entrypoint` — для Python: `"модуль:Класс"` (по умолч. `"plugin:Plugin"`)
- `cpp_library` — для C++: путь к .so внутри архива
- `permissions` — список обязательных разрешений
- `optional_permissions` — список опциональных разрешений

### Установка

Поместите `.plugin` файл в директорию:
```
{filesDir}/lumigram/plugins/
```

После перезапуска или вызова `PluginManager.rescanInstalledPlugins()` плагин будет обнаружен и загружен.

---

## Python плагины

### Пример минимального Python плагина

**plugin.py:**
```python
import json

class Plugin:
    def on_plugin_load(self):
        print("[MyPlugin] Loaded!")
        return True

    def on_plugin_unload(self):
        print("[MyPlugin] Unloaded!")
        return True

    def on_update_hook(self, update_name, account, update_json):
        print(f"[MyPlugin] Update: {update_name}")
        return None  # None = продолжить цепочку
```

**lumigram-plugin.json:**
```json
{
  "id": "hello_lumi",
  "name": "Hello Lumigram",
  "version": "1.0.0",
  "type": "python",
  "entrypoint": "plugin:Plugin",
  "permissions": []
}
```

### Доступные Python хуки

| Метод | Описание |
|--------|----------|
| `on_plugin_load()` | Вызывается при загрузке плагина |
| `on_plugin_unload()` | Вызывается при выгрузке |
| `on_update_hook(update_name, account, update_json)` | MTProto update с сервера |

### Python зависимости

Укажите `requirements.txt` внутри архива или `python.requirements` в манифесте:
```json
"python": {
  "requirements": ["requests==2.28.0", "beautifulsoup4"]
}
```

### Ограничения Python плагинов

- Требуется Chaquopy runtime (встроен в Lumigram)
- Доступна полная стандартная библиотека Python 3
- Java bridge доступен через `chaquopy` (см. документацию Chaquopy)

---

## C++ плагины

### API (lumigram_plugin_api.h)

Каждый C++ плагин должен реализовать следующие функции:

```c
#include "lumigram_plugin_api.h"

#define PLUGIN_ID "my_cpp_plugin"
#define PLUGIN_VERSION "1.0.0"

static bool loaded = false;

// Обязательно: инициализация
extern "C" int lumigram_plugin_init(const LumigramPluginInfo *info) {
    if (!info || info->api_version != LUMIGRAM_PLUGIN_API_VERSION)
        return -1;
    // Проверьте info->plugin_id и info->plugin_version
    return 0;
}

// Опционально: вызывается после успешной загрузки
extern "C" int lumigram_plugin_on_load() {
    if (loaded) return 0;
    loaded = true;
    // Ваша инициализация
    return 0;
}

// Опционально: вызывается при выгрузке
extern "C" int lumigram_plugin_on_unload() {
    if (!loaded) return 0;
    loaded = false;
    // Ваша очистка
    return 0;
}

// Опционально: вызывается при получении MTProto update
extern "C" int lumigram_plugin_on_update(const char *update_name,
                                          int account,
                                          const char *update_json) {
    if (!update_name) return -1;
    // Обработка update
    return 0;
}
```

### Компиляция C++ плагина

```bash
# Для arm64-v8a
aarch64-linux-android21-clang++ \
    -shared \
    -fPIC \
    -std=c++17 \
    -I/path/to/lumigram_plugin_api.h \
    -o libplugin.so \
    plugin.cpp

# Добавьте в .plugin архив
zip myplugin.plugin lumigram-plugin.json libplugin.so
```

### Пример полного C++ плагина

См. `TMessagesProj/jni/plugin_example.cpp` в репозитории.

### Ограничения C++ плагинов

- Должны быть скомпилированы под архитектуру устройства (arm64-v8a / armeabi-v7a)
- Экспортируемые символы должны иметь `__attribute__((visibility("default")))`
- Доступен только C API из `lumigram_plugin_api.h`
- Нет прямого доступа к Java/Kotlin API

---

## Система разрешений

22 разрешения в 3 уровнях:

### NORMAL (авто-выдаются)

| ID | Описание |
|----|----------|
| `telegram.read_messages` | Чтение сообщений |
| `telegram.send_messages` | Отправка сообщений |
| `telegram.read_contacts` | Чтение контактов |
| `telegram.call` | Звонки |
| `telegram.read_stories` | Просмотр историй |
| `android.camera` | Камера |
| `android.microphone` | Микрофон |
| `lumigram.ghost_mode` | Ghost Mode (невидимка) |

### SENSITIVE (требуется подтверждение)

| ID | Описание |
|----|----------|
| `telegram.modify_chats` | Создание/удаление чатов |
| `telegram.delete_messages` | Удаление сообщений |
| `android.location` | Геолокация |
| `android.storage.read` | Чтение хранилища |
| `android.storage.write` | Запись в хранилище |
| `lumigram.spy_mode` | Spy Mode (сохранение удалённых сообщений) |
| `lumigram.secret_storage` | Secure Vault |
| `lumigram.external_network` | Внешние сетевые соединения |

### DANGEROUS (явное согласие + предупреждение)

| ID | Описание |
|----|----------|
| `android.storage.system` | Доступ к /data и /system |
| `sudo.root_command` | Выполнение команд как root |
| `sudo.shizuku_api` | Shizuku API |
| `sudo.network_capture` | Перехват трафика |
| `sudo.gpu_access` | Доступ к GPU |

---

## Хуки и события

В `BasePlugin` определены следующие хуки:

| Метод | Описание | Статус |
|-------|----------|--------|
| `onPluginLoad()` | Инициализация плагина | ✅ Реализован |
| `onPluginUnload()` | Очистка плагина | ✅ Реализован |
| `onUpdateHook(name, account, update)` | MTProto update | ✅ Диспатчится |
| `onMessageHook(account, message)` | Обработка сообщения | ⏳ Не диспатчится |
| `onMessageSend(account, message)` | Отправка сообщения | ⏳ Не диспатчится |
| `onMessageReceived(account, message)` | Получение сообщения | ⏳ Не диспатчится |
| `onChatOpen(account, chatId)` | Открытие чата | ⏳ Не диспатчится |
| `onChatClose(account, chatId)` | Закрытие чата | ⏳ Не диспатчится |
| `onButtonClick(account, button)` | Клик по кнопке | ⏳ Не диспатчится |
| `onFileDownload(account, file)` | Загрузка файла | ⏳ Не диспатчится |
| `onCallEvent(account, event, call)` | Событие звонка | ⏳ Не диспатчится |

> ⏳ Хуки, помеченные "Не диспатчится", требуют интеграции в Telegram клиент (`MessagesController.java`, `ChatActivity.java`, `SendMessagesHelper.java`). Для добавления см. раздел [Как дополнить это руководство](#как-дополнить-это-руководство).

### HookResult

```java
HookResult.pass()         // Продолжить цепочку
HookResult.handled()      // Потреблять событие
HookResult.handled(data)  // Потреблять с возвратом данных
```

---

## Secure Vault

Шифрованное хранилище AES-256-GCM для плагинов.

```java
SecureVault vault = new SecureVault(context);

// Сохранить
vault.set("plugin_id", "api_key", "secret-value");

// Прочитать
String value = vault.get("plugin_id", "api_key");

// Удалить
vault.delete("plugin_id", "api_key");

// Очистить всё для плагина
vault.clearPluginData("plugin_id");
```

- Ключи хранятся в Android Keystore
- Файлы: `{filesDir}/lumigram/vault/{pluginId}.vault`
- Требуется разрешение `lumigram.secret_storage`

---

## Sudo / Root

Выполнение привилегированных команд:

```java
SudoManager sudo = new SudoManager();

// Синхронно
String result = sudo.execute("id");

// Асинхронно
sudo.executeAsync("ls /data", (stdout, stderr, exitCode) -> {
    // callback
});
```

- Требуется разрешение `sudo.root_command`
- Проверяет `su` в стандартных путях
- Shizuku backend пока не реализован

---

## Логирование

SQLite-логгер с ротацией (макс. 10 000 записей):

```java
PluginLogger logger = new PluginLogger(context);

logger.d("plugin_id", "Debug message");
logger.i("plugin_id", "Info message");
logger.w("plugin_id", "Warning");
logger.e("plugin_id", "Error", exception);
logger.f("plugin_id", "Fatal error");

// Чтение
List<PluginLogger.LogEntry> logs = logger.getLogs(50);

// Очистка
logger.clearAll();
```

**Уровни:** `DEBUG`, `INFO`, `WARNING`, `ERROR`, `FATAL`

**Категории:** `PLUGIN_LOADER`, `PLUGIN_EXECUTION`, `PERMISSIONS`, `UI`, `NETWORK`, `SUDO`, `SECURITY`, `GENERAL`

---

## Lumi Store

Встроенный магазин плагинов:

```java
LumiStore store = new LumiStore();

// Получить список плагинов
store.fetchListings(listings -> {
    for (LumiStore.PluginListing p : listings) {
        System.out.println(p.name + " v" + p.version);
    }
});

// Скачать и установить
store.downloadPlugin(context, listing, success -> {
    if (success) System.out.println("Installed!");
});
```

- API URL: `https://plugins.lumigram.app/api/v1/list`
- Скачанные плагины автоматически устанавливаются через PluginManager

---

## Как дополнить это руководство

PLUGINS.md — живой документ. Чтобы его дополнить:

1. Откройте `PLUGINS.md` в корне репозитория
2. Добавьте новый раздел в соответствующую категорию или создайте новую
3. Отправьте PR или коммитьте напрямую в main

**Примеры того, что можно добавить:**

### Новые хуки (интеграция в Telegram)

Если вы добавили новый хук в `PluginManager.dispatch*()`, опишите здесь:
- Название метода в `BasePlugin`
- Где в Telegram клиенте вызывается
- Параметры и возвращаемое значение

### Примеры плагинов

Добавляйте примеры Python и C++ плагинов в папку `examples/` и ссылайтесь на них отсюда:
```markdown
### Пример: Telegram Echo Bot
См. `examples/echo_bot/` — плагин, который отвечает "echo" на любое сообщение.
```

### Android Runtime Permissions

Если добавляется новое Android-разрешение в `PermissionManager.checkSdkInt()`, обновите таблицу:
```markdown
| `android.new_permission` | Название | API 34+ |
```

### Secure Vault API

Если расширяется SecureVault (например, добавлены `getKeys()` или `encryptFile()`), опишите новые методы.

### Sudo Backend

Если будет реализован Shizuku backend, добавьте секцию:
```markdown
### Shizuku (требуется разрешение `sudo.shizuku_api`)
...
```

### Правила оформления

- Используйте markdown с подсветкой синтаксиса для кода
- Таблицы для сравнения и перечисления
- Секции с `###` для подразделов
- Код на Java/Kotlin/Python/C++ выделяйте соответствующим языком
