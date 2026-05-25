package com.lumigram.messenger.settings;

import android.content.Context;
import android.os.Process;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.LaunchActivity;

import java.util.ArrayList;
import java.util.List;

import com.lumigram.messenger.plugins.PluginController;
import com.lumigram.messenger.plugins.PluginLifecycle;
import com.lumigram.messenger.plugins.PluginManager;
import com.lumigram.messenger.plugins.PluginManifest;

public class LumiPluginsActivity extends BaseLumiSettingsActivity {

    private final int storeRow = rowId++;
    private final int exitRow = rowId++;

    private final int pluginsStartRow = 100;
    private final int safeModeRow = 200;

    private PluginManager pluginManager;
    private List<PluginManifest> plugins;

    private PluginManager getPluginManager() {
        if (pluginManager == null) {
            pluginManager = PluginManager.getInstance();
        }
        return pluginManager;
    }

    private List<PluginManifest> getPlugins() {
        if (plugins == null) {
            plugins = getPluginManager().getInstalledPlugins();
        }
        return plugins;
    }

    @Override
    public boolean onFragmentCreate() {
        getPluginManager();
        plugins = getPluginManager().getInstalledPlugins();
        return super.onFragmentCreate();
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        var pm = getPluginManager();
        if (pm.isSafeMode()) {
            items.add(TextSettingsCellFactory.of(safeModeRow, LocaleController.getString(R.string.PluginsSafeMode)).red());
            items.add(UItem.asShadow(LocaleController.getString(R.string.PluginsSafeModeAbout)));
        }

        items.add(UItem.asHeader(LocaleController.getString(R.string.PluginsInstalled)));
        if (getPlugins().isEmpty()) {
            items.add(UItem.asShadow(LocaleController.getString(R.string.PluginsNone)));
        } else {
            for (int i = 0; i < getPlugins().size(); i++) {
                var manifest = getPlugins().get(i);
                var state = pm.getPluginState(manifest.id);
                var stateText = switch (state) {
                    case RUNNING -> "✓ " + LocaleController.getString(R.string.PluginsRunning);
                    case LOADING -> "⟳ " + LocaleController.getString(R.string.PluginsLoading);
                    case INSTALLED -> LocaleController.getString(R.string.PluginsInstalledIdle);
                    case DISABLED -> LocaleController.getString(R.string.PluginsDisabled);
                    case ERROR -> "✗ " + LocaleController.getString(R.string.PluginsError);
                    default -> state.name();
                };
                items.add(TextDetailSettingsCellFactory.of(pluginsStartRow + i,
                        manifest.name + " v" + manifest.version,
                        stateText
                ).slug(manifest.id));
            }
            items.add(UItem.asShadow(null));
        }

        items.add(UItem.asHeader(LocaleController.getString(R.string.PluginsStore)));
        items.add(TextDetailSettingsCellFactory.of(storeRow,
                LocaleController.getString(R.string.PluginsStore),
                LocaleController.getString(R.string.PluginsStoreAbout)
        ).slug("store"));
        items.add(UItem.asShadow(null));

        items.add(TextSettingsCellFactory.of(exitRow,
                LocaleController.getString(R.string.PluginsExitTitle)
        ).red().slug("exit"));
        items.add(UItem.asShadow(null));
    }

    @Override
    protected void onItemClick(UItem item, View view, int position, float x, float y) {
        int id = item.id;

        if (id == safeModeRow) {
            getPluginManager().exitSafeMode();
            listView.adapter.update(true);
            BulletinFactory.of(this).createSimpleBulletin(R.raw.info,
                    LocaleController.getString(R.string.PluginsSafeModeExited), null).show();
            return;
        }

        if (id == storeRow) {
            showStoreDialog();
            return;
        }

        if (id == exitRow) {
            showExitConfirm();
            return;
        }

        if (id >= pluginsStartRow) {
            int idx = id - pluginsStartRow;
            if (idx >= 0 && idx < getPlugins().size()) {
                var manifest = getPlugins().get(idx);
                showPluginActions(manifest);
            }
            return;
        }
    }

    @Override
    protected boolean onItemLongClick(UItem item, View view, int position, float x, float y) {
        return super.onItemLongClick(item, view, position, x, y);
    }

    @Override
    protected String getActionBarTitle() {
        return LocaleController.getString(R.string.PluginsTitle);
    }

    @Override
    protected String getKey() {
        return "plugins";
    }

    private void showPluginActions(PluginManifest manifest) {
        var pm = getPluginManager();
        var state = pm.getPluginState(manifest.id);
        var builder = new AlertDialog.Builder(getParentActivity(), resourcesProvider);
        builder.setTitle(manifest.name + " v" + manifest.version);

        var items = new ArrayList<CharSequence>();
        var actions = new ArrayList<Runnable>();

        if (manifest.description != null) {
            items.add(manifest.description);
            actions.add(null);
        }

        if (manifest.author != null) {
            items.add(LocaleController.getString(R.string.PluginsAuthor) + " " + manifest.author);
            actions.add(null);
        }

        switch (state) {
            case INSTALLED:
                items.add(LocaleController.getString(R.string.PluginsLoad));
                actions.add(() -> {
                    pm.loadPlugin(manifest.id);
                    listView.adapter.update(true);
                });
                break;
            case RUNNING:
                items.add(LocaleController.getString(R.string.PluginsUnload));
                actions.add(() -> {
                    pm.unloadPlugin(manifest.id);
                    listView.adapter.update(true);
                });
                items.add(LocaleController.getString(R.string.PluginsDisable));
                actions.add(() -> {
                    pm.disablePlugin(manifest.id);
                    listView.adapter.update(true);
                });
                break;
            case DISABLED:
                items.add(LocaleController.getString(R.string.PluginsEnable));
                actions.add(() -> {
                    pm.enablePlugin(manifest.id);
                    pm.loadPlugin(manifest.id);
                    listView.adapter.update(true);
                });
                break;
            case ERROR:
                items.add(LocaleController.getString(R.string.PluginsLoad));
                actions.add(() -> {
                    pm.loadPlugin(manifest.id);
                    listView.adapter.update(true);
                });
                break;
        }

        items.add(LocaleController.getString(R.string.PluginsUninstall));
        actions.add(() -> {
            AlertDialog confirm = new AlertDialog.Builder(getParentActivity(), resourcesProvider)
                    .setTitle(LocaleController.getString(R.string.PluginsUninstall))
                    .setMessage(LocaleController.formatString(R.string.PluginsUninstallConfirm, manifest.name))
                    .setPositiveButton(LocaleController.getString(R.string.Delete), (dialog, which) -> {
                        pm.uninstallPlugin(manifest.id);
                        plugins = pm.getInstalledPlugins();
                        listView.adapter.update(true);
                    })
                    .setNegativeButton(LocaleController.getString(R.string.Cancel), null)
                    .create();
            confirm.redPositive();
            showDialog(confirm);
        });

        items.add(LocaleController.getString(R.string.Cancel));
        actions.add(null);

        builder.setItems(items.toArray(new CharSequence[0]), (dialog, which) -> {
            var action = actions.get(which);
            if (action != null) {
                action.run();
            }
        });
        showDialog(builder.create());
    }

    private void showStoreDialog() {
        var builder = new AlertDialog.Builder(getParentActivity(), resourcesProvider);
        builder.setTitle(LocaleController.getString(R.string.PluginsStore));
        builder.setMessage(LocaleController.getString(R.string.PluginsStoreComingSoon));
        builder.setPositiveButton(LocaleController.getString(R.string.OK), null);
        showDialog(builder.create());
    }

    private void showExitConfirm() {
        var builder = new AlertDialog.Builder(getParentActivity(), resourcesProvider);
        builder.setTitle(LocaleController.getString(R.string.PluginsExitTitle));
        builder.setMessage(LocaleController.getString(R.string.PluginsExitConfirm));
        builder.setPositiveButton(LocaleController.getString(R.string.PluginsExit), (dialog, which) -> {
            try {
                getPluginManager().shutdown();
            } catch (Exception e) {
                FileLog.e(e);
            }
            AndroidUtilities.runOnUIThread(() -> {
                if (getParentActivity() instanceof LaunchActivity launch) {
                    launch.finishAffinity();
                }
                Process.killProcess(Process.myPid());
                System.exit(0);
            }, 200);
        });
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        var dialog = builder.create();
        dialog.redPositive();
        showDialog(dialog);
    }

    @Override
    public void onResume() {
        super.onResume();
        plugins = getPluginManager().getInstalledPlugins();
        listView.adapter.update(true);
    }
}
