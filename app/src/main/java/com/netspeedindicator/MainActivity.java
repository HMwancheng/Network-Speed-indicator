package com.netspeedindicator;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {
    private static final int SYSTEM_ALERT_WINDOW_PERMISSION = 100;
    
    private Switch floatingToggle;
    private Switch lockToggle;
    private Switch lowSpeedToggle;
    private Switch statusBarToggle;
    
    private RadioGroup speedFormatGroup;
    private RadioGroup alignGroup;
    
    private Button btnUp, btnDown, btnLeft, btnRight, btnCenter;
    private Button colorWhite, colorBlack, colorGreen, colorRed, colorBlue;
    
    private SeekBar textSizeSeek;
    private TextView textSizeValue;
    
    private SharedPreferences preferences;
    private SharedPreferences.Editor editor;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        preferences = getSharedPreferences("NetSpeedIndicator", MODE_PRIVATE);
        editor = preferences.edit();
        
        initViews();
        loadSettings();
        setupListeners();
        
        // Check for system alert window permission
        if (!Settings.canDrawOverlays(this)) {
            requestPermission();
        }
    }
    
    private void initViews() {
        floatingToggle = findViewById(R.id.floating_toggle);
        lockToggle = findViewById(R.id.lock_toggle);
        lowSpeedToggle = findViewById(R.id.low_speed_toggle);
        statusBarToggle = findViewById(R.id.status_bar_toggle);
        
        speedFormatGroup = findViewById(R.id.speed_format_group);
        alignGroup = findViewById(R.id.align_group);
        
        btnUp = findViewById(R.id.btn_up);
        btnDown = findViewById(R.id.btn_down);
        btnLeft = findViewById(R.id.btn_left);
        btnRight = findViewById(R.id.btn_right);
        btnCenter = findViewById(R.id.btn_center);
        
        colorWhite = findViewById(R.id.color_white);
        colorBlack = findViewById(R.id.color_black);
        colorGreen = findViewById(R.id.color_green);
        colorRed = findViewById(R.id.color_red);
        colorBlue = findViewById(R.id.color_blue);
        
        textSizeSeek = findViewById(R.id.text_size_seek);
        textSizeValue = findViewById(R.id.text_size_value);
    }
    
    private void loadSettings() {
        // Load floating window state
        boolean isFloatingEnabled = preferences.getBoolean("floating_enabled", false);
        floatingToggle.setChecked(isFloatingEnabled);
        
        // Load position lock
        boolean isLocked = preferences.getBoolean("is_locked", false);
        lockToggle.setChecked(isLocked);
        
        // Load low speed hide
        boolean lowSpeedHide = preferences.getBoolean("low_speed_hide", false);
        lowSpeedToggle.setChecked(lowSpeedHide);
        
        // Load speed format
        int speedFormat = preferences.getInt("speed_format", 0);
        switch (speedFormat) {
            case 0:
                speedFormatGroup.check(R.id.format_total);
                break;
            case 1:
                speedFormatGroup.check(R.id.format_hv);
                break;
            case 2:
                speedFormatGroup.check(R.id.format_vv);
                break;
        }
        
        // Load text alignment
        int textAlignment = preferences.getInt("text_alignment", Gravity.LEFT);
        switch (textAlignment) {
            case Gravity.LEFT:
                alignGroup.check(R.id.align_left);
                break;
            case Gravity.CENTER:
                alignGroup.check(R.id.align_center);
                break;
            case Gravity.RIGHT:
                alignGroup.check(R.id.align_right);
                break;
        }
        
        // Load text color
        int textColor = preferences.getInt("text_color", Color.WHITE);
        // Update UI to reflect selected color
        updateColorButtons(textColor);
        
        // Load text size
        int textSize = preferences.getInt("text_size", 14);
        textSizeSeek.setProgress(textSize);
        textSizeValue.setText(textSize + "sp");
        
        // Load status bar setting
        boolean showOverStatusBar = preferences.getBoolean("show_over_status_bar", false);
        statusBarToggle.setChecked(showOverStatusBar);
    }
    
    private void setupListeners() {
        // Floating window toggle
        floatingToggle.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                if (Settings.canDrawOverlays(this)) {
                    startFloatingService();
                    editor.putBoolean("floating_enabled", true);
                } else {
                    requestPermission();
                    floatingToggle.setChecked(false);
                }
            } else {
                stopFloatingService();
                editor.putBoolean("floating_enabled", false);
            }
            editor.apply();
        });
        
        // Position lock toggle
        lockToggle.setOnCheckedChangeListener((buttonView, isChecked) -> {
            editor.putBoolean("is_locked", isChecked);
            editor.apply();
            notifySettingsChanged();
        });
        
        // Low speed hide toggle
        lowSpeedToggle.setOnCheckedChangeListener((buttonView, isChecked) -> {
            editor.putBoolean("low_speed_hide", isChecked);
            editor.apply();
            notifySettingsChanged();
        });
        
        // Status bar toggle
        statusBarToggle.setOnCheckedChangeListener((buttonView, isChecked) -> {
            editor.putBoolean("show_over_status_bar", isChecked);
            editor.apply();
            notifySettingsChanged();
        });
        
        // Speed format radio group
        speedFormatGroup.setOnCheckedChangeListener((group, checkedId) -> {
            int format = 0;
            if (checkedId == R.id.format_hv) {
                format = 1;
            } else if (checkedId == R.id.format_vv) {
                format = 2;
            }
            editor.putInt("speed_format", format);
            editor.apply();
            notifySettingsChanged();
        });
        
        // Text alignment radio group
        alignGroup.setOnCheckedChangeListener((group, checkedId) -> {
            int alignment = Gravity.LEFT;
            if (checkedId == R.id.align_center) {
                alignment = Gravity.CENTER;
            } else if (checkedId == R.id.align_right) {
                alignment = Gravity.RIGHT;
            }
            editor.putInt("text_alignment", alignment);
            editor.apply();
            notifySettingsChanged();
        });
        
        // Position fine tune buttons
        btnUp.setOnClickListener(v -> adjustPosition(0, -10));
        btnDown.setOnClickListener(v -> adjustPosition(0, 10));
        btnLeft.setOnClickListener(v -> adjustPosition(-10, 0));
        btnRight.setOnClickListener(v -> adjustPosition(10, 0));
        btnCenter.setOnClickListener(v -> centerPosition());
        
        // Text color buttons
        colorWhite.setOnClickListener(v -> setTextColor(Color.WHITE));
        colorBlack.setOnClickListener(v -> setTextColor(Color.BLACK));
        colorGreen.setOnClickListener(v -> setTextColor(Color.GREEN));
        colorRed.setOnClickListener(v -> setTextColor(Color.RED));
        colorBlue.setOnClickListener(v -> setTextColor(Color.BLUE));
        
        // Text size seek bar
        textSizeSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                textSizeValue.setText(progress + "sp");
                editor.putInt("text_size", progress);
                editor.apply();
                notifySettingsChanged();
            }
            
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}
            
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
    }
    
    private void adjustPosition(int dx, int dy) {
        int currentX = preferences.getInt("position_x", 50);
        int currentY = preferences.getInt("position_y", 50);
        
        editor.putInt("position_x", currentX + dx);
        editor.putInt("position_y", currentY + dy);
        editor.apply();
        notifySettingsChanged();
    }
    
    private void centerPosition() {
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int screenHeight = getResources().getDisplayMetrics().heightPixels;
        
        editor.putInt("position_x", screenWidth / 2 - 50);
        editor.putInt("position_y", screenHeight / 2 - 50);
        editor.apply();
        notifySettingsChanged();
    }
    
    private void setTextColor(int color) {
        editor.putInt("text_color", color);
        editor.apply();
        notifySettingsChanged();
    }
    
    private void startFloatingService() {
        Intent serviceIntent = new Intent(this, FloatingWindowService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
    }
    
    private void stopFloatingService() {
        Intent serviceIntent = new Intent(this, FloatingWindowService.class);
        stopService(serviceIntent);
    }
    
    private void requestPermission() {
        Toast.makeText(this, "需要悬浮窗权限", Toast.LENGTH_SHORT).show();
        Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, 
                Uri.parse("package:" + getPackageName()));
        startActivityForResult(intent, SYSTEM_ALERT_WINDOW_PERMISSION);
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == SYSTEM_ALERT_WINDOW_PERMISSION) {
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "悬浮窗权限被拒绝", Toast.LENGTH_SHORT).show();
            }
        }
    }
    
    private void updateColorButtons(int selectedColor) {
        // Update button appearances based on selected color
        colorWhite.setSelected(selectedColor == Color.WHITE);
        colorBlack.setSelected(selectedColor == Color.BLACK);
        colorGreen.setSelected(selectedColor == Color.GREEN);
        colorRed.setSelected(selectedColor == Color.RED);
        colorBlue.setSelected(selectedColor == Color.BLUE);
    }
    
    private void notifySettingsChanged() {
        Intent intent = new Intent("com.netspeedindicator.SETTINGS_CHANGED");
        sendBroadcast(intent);
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Save settings when activity is destroyed
        editor.apply();
    }
}