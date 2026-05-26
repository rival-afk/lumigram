# 🐾 Lumigram
Lumigram is a third-party Telegram client with not many but useful modifications.

- Website: https://github.com/rival-afk/lumigram
- Telegram channel: https://t.me/lumigram_dev
- Downloads: https://github.com/rival-afk/lumigram
- Feedback: https://github.com/rival-afk/lumigram/issues

## API, Protocol documentation

Telegram API manuals: https://core.telegram.org/api

MTProto protocol manuals: https://core.telegram.org/mtproto

## Compilation Guide

1. Download the Lumigram source code ( `git clone https://github.com/rival-afk/lumigram.git` )
1. Fill out storeFile, storePassword, keyAlias, keyPassword in local.properties to access your release.keystore
1. Go to https://console.firebase.google.com/, create two android apps with application IDs com.lumigram.messenger and com.lumigram.messenger.beta, turn on firebase messaging and download `google-services.json`, which should be copied into `TMessagesProj` folder.
1. Open the project in the Studio (note that it should be opened, NOT imported).
1. Fill out values in `TMessagesProj/src/main/java/com/lumigram/messenger/Extra.java` – there’s a link for each of the variables showing where and which data to obtain.
1. You are ready to compile Lumigram.

## Localization

Lumigram is forked from Telegram, thus most locales follows the translations of Telegram for Android, checkout https://translations.telegram.org/en/android/.

As for the Lumigram specialized strings, we use Crowdin to translate Lumigram. Help us bring Lumigram to the world!
