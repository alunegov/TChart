package com.github.alunegov.tchart;

import android.app.Application;

import com.codemonkeylabs.fpslibrary.TinyDancer;

public class DebugApplication extends Application {
    @Override
    public void onCreate() {
        TinyDancer.create()
                .show(this);
    }
}
