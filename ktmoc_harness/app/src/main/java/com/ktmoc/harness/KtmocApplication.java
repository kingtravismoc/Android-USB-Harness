// Copyright 2024 KTMOC Project
// SPDX-License-Identifier: GPL-3.0-or-later
package com.ktmoc.harness;

import android.app.Application;
import android.util.Log;

/**
 * KTMOC Application class for F-Droid compatible initialization
 */
public class KtmocApplication extends Application {
    private static final String TAG = "KtmocApp";
    
    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "KTMOC Harness v4.0+ initialized");
        
        // Initialize application-wide components
        initializeComponents();
    }
    
    private void initializeComponents() {
        // Set up global exception handler for debugging
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            Log.e(TAG, "Uncaught exception in thread " + thread.getName(), throwable);
            // In production, log to file for later analysis
        });
        
        Log.d(TAG, "Components initialized successfully");
    }
}
