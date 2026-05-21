package com.lumigram.messenger.helpers;

import android.net.Uri;
import android.text.TextUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.browser.Browser;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.LaunchActivity;

import java.util.Locale;
import java.util.function.Consumer;

import com.lumigram.messenger.settings.BaseLumiSettingsActivity;
import com.lumigram.messenger.settings.LumiAppearanceSettingsActivity;
import com.lumigram.messenger.settings.LumiChatSettingsActivity;
import com.lumigram.messenger.settings.LumiDonateActivity;
import com.lumigram.messenger.settings.LumiEmojiSettingsActivity;
import com.lumigram.messenger.settings.LumiExperimentalSettingsActivity;
import com.lumigram.messenger.settings.LumiGeneralSettingsActivity;
import com.lumigram.messenger.settings.LumiPasscodeSettingsActivity;

public class SettingsHelper {

    public static void processDeepLink(Uri uri, Consumer<BaseFragment> callback, Runnable unknown, Browser.Progress progress) {
        if (uri == null) {
            unknown.run();
            return;
        }
        var segments = uri.getPathSegments();
        if (segments.isEmpty() || segments.size() > 2) {
            unknown.run();
            return;
        }
        BaseLumiSettingsActivity fragment;
        var segment = segments.get(1);
        if (PasscodeHelper.getSettingsKey().equals(segment)) {
            fragment = new LumiPasscodeSettingsActivity();
        } else {
            switch (segment.toLowerCase(Locale.US)) {
                case "appearance":
                case "a":
                    fragment = new LumiAppearanceSettingsActivity();
                    break;
                case "chat":
                case "chats":
                case "c":
                    fragment = new LumiChatSettingsActivity();
                    break;
                case "donate":
                case "d":
                    fragment = new LumiDonateActivity();
                    break;
                case "experimental":
                case "e":
                    fragment = new LumiExperimentalSettingsActivity();
                    break;
                case "emoji":
                    fragment = new LumiEmojiSettingsActivity();
                    break;
                case "general":
                case "g":
                    fragment = new LumiGeneralSettingsActivity();
                    break;
                case "reportid":
                    SettingsHelper.copyReportId();
                    return;
                case "update":
                    LaunchActivity.instance.checkAppUpdate(true, progress);
                    return;
                default:
                    unknown.run();
                    return;
            }
        }
        callback.accept(fragment);
        var row = uri.getQueryParameter("r");
        if (TextUtils.isEmpty(row)) {
            row = uri.getQueryParameter("row");
        }
        if (!TextUtils.isEmpty(row)) {
            var rowFinal = row;
            AndroidUtilities.runOnUIThread(() -> fragment.scrollToRow(rowFinal, unknown));
        }
    }

    public static void copyReportId() {
        AndroidUtilities.addToClipboard(AnalyticsHelper.userId);
        BulletinFactory.global().createSimpleBulletin(R.raw.copy, LocaleController.getString(R.string.TextCopied), LocaleController.getString(R.string.CopyReportIdDescription)).show();
    }
}
