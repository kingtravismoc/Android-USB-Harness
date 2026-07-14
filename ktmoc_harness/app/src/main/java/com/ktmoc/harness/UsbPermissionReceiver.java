// Copyright 2024 KTMOC Project
// SPDX-License-Identifier: GPL-3.0-or-later
package com.ktmoc.harness;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.util.Log;

/**
 * USB Permission Receiver for handling USB device attachment/detachment
 */
public class UsbPermissionReceiver extends BroadcastReceiver {
    private static final String TAG = "UsbPermissionReceiver";
    
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        
        if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)) {
            UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
            if (device != null) {
                Log.d(TAG, "USB device attached: " + device.getDeviceName());
                // Notify the main activity
                notifyDeviceChange(context, device, true);
            }
        } else if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
            UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
            if (device != null) {
                Log.d(TAG, "USB device detached: " + device.getDeviceName());
                // Notify the main activity
                notifyDeviceChange(context, device, false);
            }
        }
    }
    
    private void notifyDeviceChange(Context context, UsbDevice device, boolean attached) {
        Intent broadcastIntent = new Intent("com.ktmoc.harness.USB_DEVICE_CHANGE");
        broadcastIntent.putExtra("device_name", device.getDeviceName());
        broadcastIntent.putExtra("vendor_id", device.getVendorId());
        broadcastIntent.putExtra("product_id", device.getProductId());
        broadcastIntent.putExtra("attached", attached);
        context.sendBroadcast(broadcastIntent);
    }
}
