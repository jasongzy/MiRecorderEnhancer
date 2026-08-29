package com.jasongzy.mirecorderenhancer;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.Configuration;
import android.text.TextUtils;
import android.util.Log;
import android.view.ActionMode;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import io.github.libxposed.api.XposedModule;

@SuppressLint("DiscouragedApi")
final class TranscriptionHook {
    private static final String TAG = "MiRecorderEnhancer";
    private static final String TARGET_PACKAGE = "com.android.soundrecorder";
    private static final String RECORDS_FRAGMENT_CLASS = "o1.E";
    private static final String ACTION_MODE_CALLBACK_CLASS = "o1.B";
    private static final int TRANSCRIBE_ITEM_ID = 0x6d720001;

    private final XposedModule module;
    private final ClassLoader classLoader;

    TranscriptionHook(XposedModule module, ClassLoader classLoader) {
        this.module = module;
        this.classLoader = classLoader;
    }

    void install() throws ReflectiveOperationException {
        Class<?> recordsFragment = Class.forName(RECORDS_FRAGMENT_CLASS, false, classLoader);
        hookContextMenu(recordsFragment);

        Class<?> actionModeCallback = Class.forName(ACTION_MODE_CALLBACK_CLASS, false, classLoader);
        hookActionMode(actionModeCallback);
    }

    private void hookContextMenu(Class<?> recordsFragment) throws NoSuchMethodException {
        Method createMenu = recordsFragment.getDeclaredMethod(
                "onCreateContextMenu", ContextMenu.class, View.class, ContextMenu.ContextMenuInfo.class);
        module.hook(createMenu).intercept(chain -> {
            Object result = chain.proceed();
            try {
                addContextMenuItem((ContextMenu) chain.getArg(0), (View) chain.getArg(1));
            } catch (Throwable throwable) {
                module.log(Log.ERROR, TAG, "Failed to add single transcription action", throwable);
            }
            return result;
        });

        Method selectItem = recordsFragment.getDeclaredMethod("onContextItemSelected", MenuItem.class);
        module.hook(selectItem).intercept(chain -> {
            MenuItem item = (MenuItem) chain.getArg(0);
            if (item.getItemId() != TRANSCRIBE_ITEM_ID) {
                return chain.proceed();
            }
            try {
                Object fragment = chain.getThisObject();
                Object record = getContextRecord(fragment, item.getMenuInfo());
                showLanguageDialog(getActivity(fragment), List.of(record));
            } catch (Throwable throwable) {
                module.log(Log.ERROR, TAG, "Failed to start single transcription", unwrap(throwable));
            }
            return true;
        });
    }

    private void hookActionMode(Class<?> callbackClass) throws NoSuchMethodException {
        Method createMode = callbackClass.getDeclaredMethod("onCreateActionMode", ActionMode.class, Menu.class);
        module.hook(createMode).intercept(chain -> {
            Object result = chain.proceed();
            try {
                if (Boolean.TRUE.equals(result)) {
                    addActionModeItem(chain.getThisObject(), (Menu) chain.getArg(1));
                }
            } catch (Throwable throwable) {
                module.log(Log.ERROR, TAG, "Failed to add batch transcription action", throwable);
            }
            return result;
        });

        Method selectItem = callbackClass.getDeclaredMethod(
                "onActionItemClicked", ActionMode.class, MenuItem.class);
        module.hook(selectItem).intercept(chain -> {
            MenuItem item = (MenuItem) chain.getArg(1);
            if (item.getItemId() != TRANSCRIBE_ITEM_ID) {
                return chain.proceed();
            }
            try {
                Object fragment = getField(chain.getThisObject(), "f");
                List<?> records = getSelectedRecords(fragment);
                if (!records.isEmpty()) {
                    showLanguageDialog(getActivity(fragment), records);
                }
                ((ActionMode) chain.getArg(0)).finish();
            } catch (Throwable throwable) {
                module.log(Log.ERROR, TAG, "Failed to start batch transcription", unwrap(throwable));
            }
            return true;
        });
    }

    private void addContextMenuItem(ContextMenu menu, View view) {
        Context context = view.getContext();
        int deleteId = resourceId(context, "menu_delete", "id");
        MenuItem delete = menu.findItem(deleteId);
        if (delete == null || menu.findItem(TRANSCRIBE_ITEM_ID) != null) {
            return;
        }

        CharSequence deleteTitle = delete.getTitle();
        menu.removeItem(deleteId);
        menu.add(0, TRANSCRIBE_ITEM_ID, 3, targetString(context, "text_btn_recognize"));
        menu.add(0, deleteId, 4, deleteTitle);
    }

    private void addActionModeItem(Object callback, Menu menu) throws ReflectiveOperationException {
        if (menu.findItem(TRANSCRIBE_ITEM_ID) != null) {
            return;
        }
        Object fragment = getField(callback, "f");
        Context context = getActivity(fragment);
        int deleteId = resourceId(context, "menu_delete", "id");
        MenuItem delete = menu.findItem(deleteId);
        if (delete == null) {
            return;
        }

        CharSequence deleteTitle = delete.getTitle();
        menu.removeItem(deleteId);
        MenuItem transcribe = menu.add(
                0, TRANSCRIBE_ITEM_ID, 0, targetString(context, "text_btn_recognize"));
        transcribe.setIcon(resourceId(context, actionIcon(context), "drawable"));
        transcribe.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);

        MenuItem restoredDelete = menu.add(0, deleteId, 0, deleteTitle);
        restoredDelete.setIcon(resourceId(context, "action_delete", "drawable"));
        restoredDelete.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
        setField(callback, "b", restoredDelete);
    }

    private String actionIcon(Context context) {
        int nightMode = context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return nightMode == Configuration.UI_MODE_NIGHT_YES
                ? "miuix_action_icon_refresh_dark"
                : "miuix_action_icon_refresh_light";
    }

    private Object getContextRecord(Object fragment, ContextMenu.ContextMenuInfo menuInfo)
            throws ReflectiveOperationException {
        int position = firstIntField(menuInfo);
        Object adapter = getField(fragment, "Z");
        return adapter.getClass().getMethod("J", int.class).invoke(adapter, position);
    }

    private List<?> getSelectedRecords(Object fragment) throws ReflectiveOperationException {
        Object adapter = getField(fragment, "Z");
        return (List<?>) adapter.getClass().getMethod("I").invoke(adapter);
    }

    private Activity getActivity(Object fragment) throws ReflectiveOperationException {
        Object activity = fragment.getClass().getMethod("getActivity").invoke(fragment);
        if (!(activity instanceof Activity)) {
            throw new IllegalStateException("Recorder activity is unavailable");
        }
        return (Activity) activity;
    }

    @SuppressWarnings("unchecked")
    private void showLanguageDialog(Activity activity, List<?> records) throws ReflectiveOperationException {
        Object service = activity.getClass().getMethod("u").invoke(activity);
        if (service == null) {
            Toast.makeText(activity, targetString(activity, "recognition_error_engine"), Toast.LENGTH_SHORT).show();
            return;
        }

        Class<?> utils = Class.forName("H1.M", false, classLoader);
        ArrayList<String> labels = (ArrayList<String>) utils.getMethod("v").invoke(null);
        ArrayList<Integer> languageTypes = (ArrayList<Integer>) utils.getMethod("u").invoke(null);
        DialogInterface.OnClickListener listener = (dialog, position) -> {
            try {
                enqueue(service, records, languageTypes.get(position));
            } catch (Throwable throwable) {
                module.log(Log.ERROR, TAG, "Failed to enqueue transcription", unwrap(throwable));
                Toast.makeText(
                                activity,
                                targetString(activity, "recognition_error_engine"),
                                Toast.LENGTH_SHORT)
                        .show();
            }
        };

        Class<?> builderClass = Class.forName("O4.a", false, classLoader);
        Object builder = builderClass.getConstructor(Context.class).newInstance(activity);
        setField(builder, "c", targetString(activity, "recognition_language_dialog_title"));
        setField(builder, "d", targetString(activity, "recognition_language_dialog_cancel"));
        setField(builder, "e", labels.toArray(new CharSequence[0]));
        setField(builder, "f", listener);
        setField(builder, "g", (DialogInterface.OnClickListener) (dialog, which) -> {});
        setField(builder, "j", false);
        Dialog dialog = (Dialog) builderClass.getMethod("a").invoke(builder);
        dialog.show();
    }

    private void enqueue(Object service, List<?> records, int languageType)
            throws ReflectiveOperationException {
        Class<?> configClass = Class.forName("T0.a", false, classLoader);
        Class<?> taskClass = Class.forName(
                "com.android.soundrecorder.newrecognize.AsrTask", false, classLoader);
        Constructor<?> taskConstructor = taskClass.getConstructor(configClass);
        Method enqueue = service.getClass().getMethod("J", taskClass);

        for (Object record : records) {
            String path = (String) record.getClass().getMethod("getFilePath").invoke(record);
            String sha1 = (String) record.getClass().getMethod("getSha1").invoke(record);
            if (TextUtils.isEmpty(path) || TextUtils.isEmpty(sha1)) {
                continue;
            }
            Object config = configClass.getConstructor().newInstance();
            setField(config, "a", languageType);
            setField(config, "c", path);
            setField(config, "e", sha1);
            setField(config, "f", TARGET_PACKAGE);
            enqueue.invoke(service, taskConstructor.newInstance(config));
        }
    }

    private String targetString(Context context, String name) {
        return context.getString(resourceId(context, name, "string"));
    }

    private int resourceId(Context context, String name, String type) {
        int id = context.getResources().getIdentifier(name, type, TARGET_PACKAGE);
        if (id == 0) {
            throw new IllegalStateException("Missing recorder resource: " + type + "/" + name);
        }
        return id;
    }

    private static int firstIntField(Object object) throws IllegalAccessException {
        for (Field field : object.getClass().getDeclaredFields()) {
            if (field.getType() == int.class) {
                field.setAccessible(true);
                return field.getInt(object);
            }
        }
        throw new IllegalStateException("Context menu position is unavailable");
    }

    private static Object getField(Object object, String name) throws ReflectiveOperationException {
        Field field = findField(object.getClass(), name);
        field.setAccessible(true);
        return field.get(object);
    }

    private static void setField(Object object, String name, Object value) throws ReflectiveOperationException {
        Field field = findField(object.getClass(), name);
        field.setAccessible(true);
        field.set(object, value);
    }

    private static Field findField(Class<?> type, String name) throws NoSuchFieldException {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try {
                return current.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {}
        }
        throw new NoSuchFieldException(name);
    }

    private static Throwable unwrap(Throwable throwable) {
        return throwable instanceof InvocationTargetException && throwable.getCause() != null
                ? throwable.getCause()
                : throwable;
    }
}
