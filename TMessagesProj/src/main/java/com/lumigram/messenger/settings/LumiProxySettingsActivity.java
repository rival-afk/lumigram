package com.lumigram.messenger.settings;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.ItemOptions;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.ProxyListActivity;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

public class LumiProxySettingsActivity extends BaseLumiSettingsActivity {

    private static final String PREFS_NAME = "lumigram_proxy";
    private static final String KEY_SUBSCRIPTIONS = "subscriptions";

    private final int proxyToggleRow = rowId++;
    private final int proxyListRow = rowId++;
    private final int subscriptionsHeaderRow = rowId++;
    private final int addSubscriptionRow = rowId++;
    private final int subscriptionsStartRow = rowId++;
    private final int clearSubscriptionsRow = rowId++;
    private final int creditRow = rowId++;

    private Set<String> subscriptions = new HashSet<>();
    private boolean proxyEnabled;

    private SharedPreferences getGlobalPrefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Context.MODE_PRIVATE);
    }

    private boolean isProxyEnabled() {
        return getGlobalPrefs().getBoolean("proxy_enabled", false) && SharedConfig.currentProxy != null;
    }

    @Override
    public boolean onFragmentCreate() {
        super.onFragmentCreate();
        loadSubscriptions();
        proxyEnabled = isProxyEnabled();
        return true;
    }

    private void loadSubscriptions() {
        SharedPreferences prefs = ApplicationLoader.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        subscriptions = new HashSet<>(prefs.getStringSet(KEY_SUBSCRIPTIONS, new HashSet<>()));
    }

    private void saveSubscriptions() {
        SharedPreferences prefs = ApplicationLoader.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putStringSet(KEY_SUBSCRIPTIONS, subscriptions).apply();
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asHeader(LocaleController.getString(R.string.ProxySettings)));

        SharedConfig.ProxyInfo currentProxy = SharedConfig.currentProxy;
        String proxyStatus;
        if (proxyEnabled && currentProxy != null) {
            proxyStatus = currentProxy.address + ":" + currentProxy.port;
        } else {
            proxyStatus = LocaleController.getString(R.string.AutoNightDisabled);
        }
        items.add(UItem.asButton(proxyToggleRow, proxyEnabled ? R.drawable.mini_checklist_done : R.drawable.mini_checklist_undone,
                LocaleController.getString(R.string.UseProxySettings), proxyStatus).slug("proxyToggle"));
        items.add(UItem.asButton(proxyListRow, R.drawable.settings_data,
                LocaleController.getString(R.string.ProxySettings) + " \u2192").slug("proxyList"));

        items.add(UItem.asShadow(null));
        items.add(UItem.asHeader("VLESS / Subscription Proxies"));
        items.add(TextSettingsCellFactory.of(addSubscriptionRow, "Add subscription"));

        if (!subscriptions.isEmpty()) {
            int i = 0;
            for (String sub : subscriptions) {
                items.add(TextDetailSettingsCellFactory.of(subscriptionsStartRow + i, sub,
                        "Tap to remove").slug("sub_" + i));
                i++;
            }
            items.add(TextSettingsCellFactory.of(clearSubscriptionsRow, "Clear all subscriptions").slug("clearSubs"));
        }

        items.add(UItem.asShadow(null));
        items.add(TextDetailSettingsCellFactory.of(creditRow,
                "Proxy + VLESS (exitFy)",
                "\u00A9 @exteraPluginsSup").slug("credit"));
    }

    @Override
    protected void onItemClick(UItem item, View view, int position, float x, float y) {
        int id = item.id;
        if (id == proxyToggleRow) {
            if (SharedConfig.currentProxy != null) {
                proxyEnabled = !proxyEnabled;
                SharedPreferences.Editor editor = getGlobalPrefs().edit();
                editor.putBoolean("proxy_enabled", proxyEnabled);
                editor.apply();
                ConnectionsManager.setProxySettings(proxyEnabled,
                        SharedConfig.currentProxy.address, SharedConfig.currentProxy.port,
                        SharedConfig.currentProxy.username, SharedConfig.currentProxy.password,
                        SharedConfig.currentProxy.secret);
                listView.adapter.update(true);
                BulletinFactory.of(this).createSimpleBulletin(
                        proxyEnabled ? R.raw.done : R.raw.done,
                        LocaleController.getString(proxyEnabled ? R.string.UseProxySettings : R.string.AutoNightDisabled)
                ).show();
            } else {
                BulletinFactory.of(this).createErrorBulletin(LocaleController.getString(R.string.NoProxy)).show();
            }
        } else if (id == proxyListRow) {
            presentFragment(new ProxyListActivity());
        } else if (id == addSubscriptionRow) {
            LinearLayout container = new LinearLayout(getParentActivity());
            container.setOrientation(LinearLayout.VERTICAL);
            container.setPadding(AndroidUtilities.dp(24), 0, AndroidUtilities.dp(24), 0);
            EditText input = new EditText(getParentActivity());
            input.setHint("https://example.com/sub");
            input.setTextSize(16);
            input.setBackground(Theme.createEditTextDrawable(getParentActivity(), false));
            container.addView(input, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity(), resourcesProvider);
            builder.setTitle("Add Subscription");
            builder.setView(container);
            builder.setPositiveButton("Add", (dialog, which) -> {
                String url = input.getText().toString().trim();
                if (!TextUtils.isEmpty(url) && (url.startsWith("http://") || url.startsWith("https://"))) {
                    if (subscriptions.add(url)) {
                        saveSubscriptions();
                        listView.adapter.update(true);
                        BulletinFactory.of(this).createCopyBulletin("Subscription added").show();
                    } else {
                        BulletinFactory.of(this).createErrorBulletin("Already added").show();
                    }
                }
            });
            builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
            showDialog(builder.create());
        } else if (id >= subscriptionsStartRow && id < clearSubscriptionsRow) {
            int index = id - subscriptionsStartRow;
            if (index < subscriptions.size()) {
                String sub = (String) subscriptions.toArray()[index];
                ItemOptions.makeOptions(this, view)
                        .setScrimViewBackground(listView.getClipBackground(view))
                        .add(R.drawable.msg_delete, "Remove", () -> {
                            subscriptions.remove(sub);
                            saveSubscriptions();
                            listView.adapter.update(true);
                        })
                        .setMinWidth(190)
                        .show();
            }
        } else if (id == clearSubscriptionsRow) {
            AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity(), resourcesProvider);
            builder.setTitle("Clear Subscriptions");
            builder.setMessage("Remove all proxy subscriptions?");
            builder.setPositiveButton(LocaleController.getString(R.string.Delete), (dialog, which) -> {
                subscriptions.clear();
                saveSubscriptions();
                listView.adapter.update(true);
            });
            builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
            showDialog(builder.create());
        }
    }

    @Override
    protected String getActionBarTitle() {
        return LocaleController.getString(R.string.ProxySettings);
    }

    @Override
    protected String getKey() {
        return "proxy";
    }
}
