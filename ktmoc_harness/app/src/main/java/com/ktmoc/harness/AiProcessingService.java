// Copyright 2024 KTMOC Project
// SPDX-License-Identifier: GPL-3.0-or-later
package com.ktmoc.harness;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

/**
 * Background service for AI processing tasks
 * Supports long-running AI inference without blocking the UI
 */
public class AiProcessingService extends Service {
    private static final String TAG = "AiProcessingService";
    private static final String CHANNEL_ID = "ktmoc_ai_channel";
    private static final int NOTIFICATION_ID = 1001;
    
    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        Log.d(TAG, "AI Processing Service created");
    }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            String action = intent.getAction();
            
            if ("START_PROCESSING".equals(action)) {
                startForeground(NOTIFICATION_ID, createNotification("Processing AI request..."));
                processAiRequest(intent.getStringExtra("prompt"), intent.getStringExtra("image_data"));
            } else if ("STOP_PROCESSING".equals(action)) {
                stopForeground(STOP_FOREGROUND_REMOVE);
                stopSelf();
            }
        }
        
        return START_NOT_STICKY;
    }
    
    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
    
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "KTMOC AI Processing",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Background AI processing notifications");
            
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }
    
    private Notification createNotification(String message) {
        Intent notificationIntent = new Intent(this, KtmocHarnessActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, notificationIntent,
                PendingIntent.FLAG_IMMUTABLE
        );
        
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("KTMOC AI Processing")
                .setContentText(message)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build();
    }
    
    private void processAiRequest(String prompt, String imageData) {
        // Run AI processing in background thread
        new Thread(() -> {
            try {
                // Simulate AI processing - actual implementation would call the AI interface
                Log.d(TAG, "Processing AI request: " + (prompt != null ? prompt.substring(0, 50) : "null"));
                
                // Update notification with progress
                updateNotification("AI processing complete");
                
                // Broadcast result back to activity
                Intent resultIntent = new Intent("com.ktmoc.harness.AI_PROCESSING_COMPLETE");
                resultIntent.putExtra("result", "Processing completed successfully");
                sendBroadcast(resultIntent);
                
            } catch (Exception e) {
                Log.e(TAG, "AI processing error", e);
                updateNotification("AI processing failed: " + e.getMessage());
            } finally {
                stopForeground(STOP_FOREGROUND_REMOVE);
                stopSelf();
            }
        }).start();
    }
    
    private void updateNotification(String message) {
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.notify(NOTIFICATION_ID, createNotification(message));
        }
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "AI Processing Service destroyed");
    }
}
