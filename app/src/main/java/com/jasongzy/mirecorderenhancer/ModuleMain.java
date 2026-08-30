package com.jasongzy.mirecorderenhancer;

import android.util.Log;

import io.github.libxposed.api.XposedModule;

public final class ModuleMain extends XposedModule {
    private static final String TAG = "MiRecorderEnhancer";

    @Override
    public void onPackageReady(PackageReadyParam param) {
        if (!RecorderSymbols.PACKAGE.equals(param.getPackageName())) {
            return;
        }
        new RecorderHook(this, param.getClassLoader()).install();
        log(Log.INFO, TAG, "Recorder hook installation completed");
    }
}
