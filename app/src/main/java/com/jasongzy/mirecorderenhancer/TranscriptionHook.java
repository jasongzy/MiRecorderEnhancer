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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.github.libxposed.api.XposedModule;

@SuppressLint("DiscouragedApi")
final class TranscriptionHook {
    private static final String TAG = "MiRecorderEnhancer";
    private static final int TRANSCRIBE_ITEM_ID = 0x6d720001;
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "MiRecorderEnhancer-Transcription");
        thread.setDaemon(true);
        return thread;
    });

    private final XposedModule module;
    private final ClassLoader classLoader;
    private Field recordAdapterField;
    private Field callbackFragmentField;
    private Field contextMenuPositionField;
    private Method recordAtPositionMethod;
    private Method selectedRecordsMethod;
    private Method recognitionLanguagesMethod;
    private Method recognitionLanguageTypesMethod;
    private Constructor<?> recognitionDialogBuilderConstructor;
    private Method recognitionDialogCreateMethod;
    private Field recognitionDialogTitleField;
    private Field recognitionDialogCancelField;
    private Field recognitionDialogItemsField;
    private Field recognitionDialogItemListenerField;
    private Field recognitionDialogCancelListenerField;
    private Field recognitionDialogCancelableField;
    private Class<?> recognitionTaskClass;
    private Constructor<?> recognitionConfigConstructor;
    private Constructor<?> recognitionTaskConstructor;
    private Field recognitionLanguageField;
    private Field recognitionPathField;
    private Field recognitionSha1Field;
    private Field recognitionPackageField;

    TranscriptionHook(XposedModule module, ClassLoader classLoader) {
        this.module = module;
        this.classLoader = classLoader;
    }

    void install() throws ReflectiveOperationException {
        Class<?> recordsFragment = Class.forName(RecorderSymbols.RECORDS_FRAGMENT_CLASS, false, classLoader);
        resolveSharedSymbols(recordsFragment);
        installPart("single transcription action", () -> {
            Class<?> menuInfoClass =
                    Class.forName(RecorderSymbols.CONTEXT_MENU_INFO_CLASS, false, classLoader);
            contextMenuPositionField =
                    findField(menuInfoClass, RecorderSymbols.CONTEXT_MENU_POSITION_FIELD);
            hookContextMenu(recordsFragment);
        });
        installPart("batch transcription action", () -> {
            Class<?> actionModeCallback =
                    Class.forName(RecorderSymbols.ACTION_MODE_CALLBACK_CLASS, false, classLoader);
            callbackFragmentField =
                    findField(actionModeCallback, RecorderSymbols.CALLBACK_FRAGMENT_FIELD);
            hookActionMode(actionModeCallback);
        });
    }

    private void resolveSharedSymbols(Class<?> recordsFragment)
            throws ReflectiveOperationException {
        recordAdapterField = findField(recordsFragment, RecorderSymbols.RECORD_ADAPTER_FIELD);
        Class<?> adapterClass = recordAdapterField.getType();
        recordAtPositionMethod = adapterClass.getMethod(RecorderSymbols.RECORD_AT_POSITION_METHOD, int.class);
        selectedRecordsMethod = adapterClass.getMethod(RecorderSymbols.SELECTED_RECORDS_METHOD);

        Class<?> utils = Class.forName(RecorderSymbols.RECOGNITION_UTILS_CLASS, false, classLoader);
        recognitionLanguagesMethod = utils.getMethod(RecorderSymbols.RECOGNITION_LANGUAGES_METHOD);
        recognitionLanguageTypesMethod = utils.getMethod(RecorderSymbols.RECOGNITION_LANGUAGE_TYPES_METHOD);
        Class<?> recognitionDialogBuilderClass =
                Class.forName(RecorderSymbols.RECOGNITION_DIALOG_BUILDER_CLASS, false, classLoader);
        recognitionDialogBuilderConstructor = recognitionDialogBuilderClass.getConstructor(Context.class);
        recognitionDialogCreateMethod = recognitionDialogBuilderClass.getMethod(
                RecorderSymbols.RECOGNITION_DIALOG_CREATE_METHOD);
        recognitionDialogTitleField = findField(
                recognitionDialogBuilderClass, RecorderSymbols.RECOGNITION_DIALOG_TITLE_FIELD);
        recognitionDialogCancelField = findField(
                recognitionDialogBuilderClass, RecorderSymbols.RECOGNITION_DIALOG_CANCEL_FIELD);
        recognitionDialogItemsField = findField(
                recognitionDialogBuilderClass, RecorderSymbols.RECOGNITION_DIALOG_ITEMS_FIELD);
        recognitionDialogItemListenerField = findField(
                recognitionDialogBuilderClass, RecorderSymbols.RECOGNITION_DIALOG_ITEM_LISTENER_FIELD);
        recognitionDialogCancelListenerField = findField(
                recognitionDialogBuilderClass, RecorderSymbols.RECOGNITION_DIALOG_CANCEL_LISTENER_FIELD);
        recognitionDialogCancelableField = findField(
                recognitionDialogBuilderClass, RecorderSymbols.RECOGNITION_DIALOG_CANCELABLE_FIELD);

        Class<?> recognitionConfigClass =
                Class.forName(RecorderSymbols.RECOGNITION_CONFIG_CLASS, false, classLoader);
        recognitionTaskClass = Class.forName(RecorderSymbols.RECOGNITION_TASK_CLASS, false, classLoader);
        recognitionConfigConstructor = recognitionConfigClass.getConstructor();
        recognitionTaskConstructor = recognitionTaskClass.getConstructor(recognitionConfigClass);
        recognitionLanguageField = findField(
                recognitionConfigClass, RecorderSymbols.RECOGNITION_CONFIG_LANGUAGE_FIELD);
        recognitionPathField =
                findField(recognitionConfigClass, RecorderSymbols.RECOGNITION_CONFIG_PATH_FIELD);
        recognitionSha1Field =
                findField(recognitionConfigClass, RecorderSymbols.RECOGNITION_CONFIG_SHA1_FIELD);
        recognitionPackageField =
                findField(recognitionConfigClass, RecorderSymbols.RECOGNITION_CONFIG_PACKAGE_FIELD);
    }

    private void installPart(String name, HookInstaller installer) {
        try {
            installer.install();
        } catch (ReflectiveOperationException | RuntimeException exception) {
            module.log(Log.ERROR, TAG, "Failed to install " + name, exception);
        }
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
                showLanguageDialog(getActivity(fragment), List.of(record), false);
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
                Object fragment = getField(callbackFragmentField, chain.getThisObject());
                List<?> records = getSelectedRecords(fragment);
                if (!records.isEmpty()) {
                    showLanguageDialog(getActivity(fragment), records, true);
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

        menu.add(0, TRANSCRIBE_ITEM_ID, 2, targetString(context, "text_btn_recognize"));
    }

    private void addActionModeItem(Object callback, Menu menu) throws ReflectiveOperationException {
        if (menu.findItem(TRANSCRIBE_ITEM_ID) != null) {
            return;
        }
        Object fragment = getField(callbackFragmentField, callback);
        Context context = getActivity(fragment);
        int deleteId = resourceId(context, "menu_delete", "id");
        MenuItem delete = menu.findItem(deleteId);
        if (delete == null) {
            return;
        }

        MenuItem transcribe = menu.add(
                0, TRANSCRIBE_ITEM_ID, 0, targetString(context, "text_btn_recognize"));
        transcribe.setIcon(resourceId(context, actionIcon(context), "drawable"));
        transcribe.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
        try {
            moveBefore(menu, transcribe, delete);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            module.log(Log.WARN, TAG, "Unable to position transcription before delete", exception);
        }
    }

    private String actionIcon(Context context) {
        int nightMode = context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return nightMode == Configuration.UI_MODE_NIGHT_YES
                ? "miuix_action_icon_refresh_dark"
                : "miuix_action_icon_refresh_light";
    }

    @SuppressWarnings("unchecked")
    private void moveBefore(Menu menu, MenuItem item, MenuItem anchor)
            throws ReflectiveOperationException {
        Field itemsField = findField(menu.getClass(), RecorderSymbols.MIUIX_MENU_ITEMS_FIELD);
        List<Object> items = (List<Object>) itemsField.get(menu);
        if (!items.remove(item)) {
            throw new IllegalStateException("Transcription menu item is unavailable");
        }
        int anchorIndex = items.indexOf(anchor);
        if (anchorIndex < 0) {
            throw new IllegalStateException("Delete menu item is unavailable");
        }
        items.add(anchorIndex, item);
        menu.getClass().getMethod("onItemsChanged", boolean.class).invoke(menu, true);
    }

    private Object getContextRecord(Object fragment, ContextMenu.ContextMenuInfo menuInfo)
            throws ReflectiveOperationException {
        if (!contextMenuPositionField.getDeclaringClass().isInstance(menuInfo)) {
            throw new IllegalStateException("Unexpected recorder context menu info");
        }
        contextMenuPositionField.setAccessible(true);
        int position = contextMenuPositionField.getInt(menuInfo);
        Object adapter = getField(recordAdapterField, fragment);
        return recordAtPositionMethod.invoke(adapter, position);
    }

    private List<?> getSelectedRecords(Object fragment) throws ReflectiveOperationException {
        Object adapter = getField(recordAdapterField, fragment);
        return (List<?>) selectedRecordsMethod.invoke(adapter);
    }

    private Activity getActivity(Object fragment) throws ReflectiveOperationException {
        Object activity = fragment.getClass().getMethod("getActivity").invoke(fragment);
        if (!(activity instanceof Activity)) {
            throw new IllegalStateException("Recorder activity is unavailable");
        }
        return (Activity) activity;
    }

    @SuppressWarnings("unchecked")
    private void showLanguageDialog(Activity activity, List<?> records, boolean showQueuedToast)
            throws ReflectiveOperationException {
        Object service = activity.getClass()
                .getMethod(RecorderSymbols.RECORD_ACTIVITY_SERVICE_METHOD)
                .invoke(activity);
        if (service == null) {
            Toast.makeText(activity, targetString(activity, "recognition_error_engine"), Toast.LENGTH_SHORT).show();
            return;
        }

        ArrayList<String> labels = (ArrayList<String>) recognitionLanguagesMethod.invoke(null);
        ArrayList<Integer> languageTypes = (ArrayList<Integer>) recognitionLanguageTypesMethod.invoke(null);
        DialogInterface.OnClickListener listener = (dialog, position) -> enqueueAsync(
                activity, service, records, languageTypes.get(position), showQueuedToast);

        Object builder = recognitionDialogBuilderConstructor.newInstance(activity);
        setField(
                recognitionDialogTitleField,
                builder,
                targetString(activity, "recognition_language_dialog_title"));
        setField(
                recognitionDialogCancelField,
                builder,
                targetString(activity, "recognition_language_dialog_cancel"));
        setField(recognitionDialogItemsField, builder, labels.toArray(new CharSequence[0]));
        setField(recognitionDialogItemListenerField, builder, listener);
        setField(
                recognitionDialogCancelListenerField,
                builder,
                (DialogInterface.OnClickListener) (dialog, which) -> {});
        setField(recognitionDialogCancelableField, builder, false);
        Dialog dialog = (Dialog) recognitionDialogCreateMethod.invoke(builder);
        dialog.show();
    }

    private void enqueueAsync(
            Activity activity,
            Object service,
            List<?> records,
            int languageType,
            boolean showQueuedToast) {
        Context context = activity.getApplicationContext();
        EXECUTOR.execute(() -> {
            try {
                int submitted = enqueue(service, records, languageType);
                if (showQueuedToast && submitted > 0) {
                    activity.getMainExecutor().execute(() -> Toast.makeText(
                                    context,
                                    targetString(context, "recognition_button_add_to_queue"),
                                    Toast.LENGTH_SHORT)
                            .show());
                }
            } catch (Throwable throwable) {
                module.log(Log.ERROR, TAG, "Failed to enqueue transcription", unwrap(throwable));
                activity.getMainExecutor().execute(() -> Toast.makeText(
                                context,
                                targetString(context, "recognition_error_engine"),
                                Toast.LENGTH_SHORT)
                        .show());
            }
        });
    }

    private int enqueue(Object service, List<?> records, int languageType)
            throws ReflectiveOperationException {
        Method enqueue = service.getClass().getMethod(
                RecorderSymbols.RECOGNITION_ENQUEUE_METHOD, recognitionTaskClass);
        int submitted = 0;

        for (Object record : records) {
            String path = (String) record.getClass().getMethod("getFilePath").invoke(record);
            String sha1 = (String) record.getClass().getMethod("getSha1").invoke(record);
            if (TextUtils.isEmpty(path) || TextUtils.isEmpty(sha1)) {
                continue;
            }
            Object config = recognitionConfigConstructor.newInstance();
            setField(recognitionLanguageField, config, languageType);
            setField(recognitionPathField, config, path);
            setField(recognitionSha1Field, config, sha1);
            setField(recognitionPackageField, config, RecorderSymbols.PACKAGE);
            enqueue.invoke(service, recognitionTaskConstructor.newInstance(config));
            submitted++;
        }
        return submitted;
    }

    private String targetString(Context context, String name) {
        return context.getString(resourceId(context, name, "string"));
    }

    private int resourceId(Context context, String name, String type) {
        int id = context.getResources().getIdentifier(name, type, RecorderSymbols.PACKAGE);
        if (id == 0) {
            throw new IllegalStateException("Missing recorder resource: " + type + "/" + name);
        }
        return id;
    }

    private static Object getField(Field field, Object object) throws IllegalAccessException {
        return field.get(object);
    }

    private static void setField(Field field, Object object, Object value) throws IllegalAccessException {
        field.set(object, value);
    }

    private static Field findField(Class<?> type, String name) throws NoSuchFieldException {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try {
                Field field = current.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {}
        }
        throw new NoSuchFieldException(name);
    }

    private static Throwable unwrap(Throwable throwable) {
        return throwable instanceof InvocationTargetException && throwable.getCause() != null
                ? throwable.getCause()
                : throwable;
    }

    @FunctionalInterface
    private interface HookInstaller {
        void install() throws ReflectiveOperationException;
    }
}
