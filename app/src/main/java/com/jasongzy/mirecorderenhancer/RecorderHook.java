package com.jasongzy.mirecorderenhancer;

import android.annotation.SuppressLint;
import android.net.Uri;
import android.util.Log;
import android.view.View;

import java.lang.reflect.Method;

import io.github.libxposed.api.XposedModule;

@SuppressLint("PrivateApi")
final class RecorderHook {
    private static final String TAG = "MiRecorderEnhancer";

    private final XposedModule module;
    private final ClassLoader classLoader;

    RecorderHook(XposedModule module, ClassLoader classLoader) {
        this.module = module;
        this.classLoader = classLoader;
    }

    void install() {
        installSafely("Record query hook", this::installQueryHook);
        installSafely("Filter UI hook", this::installUiHook);
        installSafely("Transcription hook", this::installTranscriptionHook);
    }

    private void installQueryHook() throws ReflectiveOperationException {
        Class<?> provider = Class.forName(RecorderSymbols.PROVIDER_CLASS, false, classLoader);
        Method query = provider.getDeclaredMethod(
                "query", Uri.class, String[].class, String.class, String[].class, String.class);
        module.hook(query).intercept(chain -> {
            Object[] args = null;
            String predicate = FilterController.get().predicate();
            if (!predicate.isEmpty()) {
                Uri uri = (Uri) chain.getArg(0);
                String[] projection = (String[]) chain.getArg(1);
                String sortOrder = (String) chain.getArg(4);
                String path = uri.getPathSegments().isEmpty() ? "" : uri.getPathSegments().get(0);
                if (QueryFilter.isRecordListQuery(uri.getAuthority(), path, projection == null, sortOrder)) {
                    args = new Object[] {
                        uri,
                        projection,
                        QueryFilter.appendPredicate((String) chain.getArg(2), predicate),
                        chain.getArg(3),
                        sortOrder
                    };
                }
            }
            return args == null ? chain.proceed() : chain.proceed(args);
        });
    }

    private void installUiHook() throws ReflectiveOperationException {
        Class<?> actionBarClass = Class.forName(RecorderSymbols.ACTION_BAR_CLASS, false, classLoader);
        Method setEndView = actionBarClass.getDeclaredMethod("setEndView", View.class);
        module.hook(setEndView).intercept(chain -> {
            View endView = (View) chain.getArg(0);
            try {
                View wrapped = FilterUi.wrapEndView(
                        (View) chain.getThisObject(), module.getModuleApplicationInfo(), endView);
                if (wrapped == endView) {
                    return chain.proceed();
                }
                module.log(Log.INFO, TAG, "Filter UI installed");
                return chain.proceed(new Object[] {wrapped});
            } catch (Throwable throwable) {
                module.log(Log.ERROR, TAG, "Failed to install filter UI", throwable);
                return chain.proceed();
            }
        });
    }

    private void installTranscriptionHook() throws ReflectiveOperationException {
        new TranscriptionHook(module, classLoader).install();
    }

    private void installSafely(String name, HookInstaller installer) {
        try {
            installer.install();
            module.log(Log.INFO, TAG, name + " installed");
        } catch (Throwable throwable) {
            module.log(Log.ERROR, TAG, "Failed to install " + name, throwable);
        }
    }

    @FunctionalInterface
    private interface HookInstaller {
        void install() throws ReflectiveOperationException;
    }
}
