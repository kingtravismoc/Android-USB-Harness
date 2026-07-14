// Copyright 2024 KTMOC Project
// SPDX-License-Identifier: GPL-3.0-or-later
package com.ktmoc.harness;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

/**
 * Settings dialog for AI service configuration
 */
public class AiConfigDialog extends DialogFragment {
    
    private EditText etServiceName;
    private EditText etModel;
    private EditText etApiKey;
    private Switch swNoKeyMode;
    
    @NonNull
    @Override
    public AlertDialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        Context context = requireContext();
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_ai_config, null);
        
        etServiceName = view.findViewById(R.id.et_service_name);
        etModel = view.findViewById(R.id.et_model);
        etApiKey = view.findViewById(R.id.et_api_key);
        swNoKeyMode = view.findViewById(R.id.sw_nokey_mode);
        
        // Load saved settings
        etServiceName.setText(prefs.getString("ai_service", "huggingface"));
        etModel.setText(prefs.getString("ai_model", "microsoft/Phi-3-mini-4k-instruct"));
        etApiKey.setText(prefs.getString("ai_api_key", ""));
        swNoKeyMode.setChecked(prefs.getBoolean("hf_nokey", true));
        
        return new AlertDialog.Builder(context, R.style.KtmocDialogTheme)
                .setTitle(R.string.ai_config_title)
                .setView(view)
                .setPositiveButton(R.string.connect, (dialog, which) -> saveSettings())
                .setNegativeButton(android.R.string.cancel, null)
                .create();
    }
    
    private void saveSettings() {
        Context context = requireContext();
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        
        String serviceName = etServiceName.getText().toString().trim();
        String model = etModel.getText().toString().trim();
        String apiKey = etApiKey.getText().toString().trim();
        boolean noKeyMode = swNoKeyMode.isChecked();
        
        prefs.edit()
                .putString("ai_service", serviceName)
                .putString("ai_model", model)
                .putString("ai_api_key", apiKey)
                .putBoolean("hf_nokey", noKeyMode)
                .apply();
        
        Toast.makeText(context, "AI configuration saved", Toast.LENGTH_SHORT).show();
        
        // Notify activity of configuration change
        if (getTargetFragment() instanceof AiConfigListener) {
            ((AiConfigListener) getTargetFragment()).onAiConfigChanged(serviceName, model, apiKey, noKeyMode);
        } else if (getActivity() instanceof AiConfigListener) {
            ((AiConfigListener) getActivity()).onAiConfigChanged(serviceName, model, apiKey, noKeyMode);
        }
    }
    
    public interface AiConfigListener {
        void onAiConfigChanged(String serviceName, String model, String apiKey, boolean noKeyMode);
    }
}
