package com.jasongzy.mirecorderenhancer;

import android.util.Log;

import io.github.libxposed.api.XposedModule;

public final class ModuleMain extends XposedModule {
    private static final String TAG = "MiRecorderEnhancer";
    private static final String TARGET_PACKAGE = "com.android.soundrecorder";

    @Override
    public void onPackageReady(PackageReadyParam param) {
        if (!TARGET_PACKAGE.equals(param.getPackageName())) {
            return;
        }
        try {
            new RecorderHook(this, param.getClassLoader()).install();
            log(Log.INFO, TAG, "Recorder hooks installed");
        } catch (ReflectiveOperationException exception) {
            log(Log.ERROR, TAG, "Failed to install recorder hooks", exception);
        }
    }
}
