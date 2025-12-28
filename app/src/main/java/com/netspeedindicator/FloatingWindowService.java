package com.netspeedindicator;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.IBinder;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

public class FloatingWindowService extends Service {
    private WindowManager windowManager;
    private View floatingView;
    private TextView speedText;
    private LinearLayout container;
    private NetworkSpeedMonitor speedMonitor;
    
    private int screenWidth;
    private int screenHeight;
    
    // Settings
    private boolean isLocked = false;
    private boolean isLowSpeedHideEnabled = false;
    private int lowSpeedThreshold = 1024; // 1KB/s
    private int speedFormat = 0; // 0: Total, 1: Up/Down Horizontal, 2: Up/Down Vertical
    private int textAlignment = Gravity.LEFT;
    private int textColor = Color.WHITE;
    private int textSize = 14;
    private boolean showOverStatusBar = false;
    
    // Position
    private WindowManager.LayoutParams params;
    private int lastX, lastY;
    private float initialTouchX, initialTouchY;
    
    private static final String CHANNEL_ID = "floating_window_service";
    private static final int NOTIFICATION_ID = 1;
    
    private SharedPreferences preferences;
    
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
    
    @Override
    public void onCreate() {
        super.onCreate();
        
        preferences = getSharedPreferences("NetSpeedIndicator", MODE_PRIVATE);
        loadSettings();
        
        // Get screen size
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        screenWidth = metrics.widthPixels;
        screenHeight = metrics.heightPixels;
        
        // Create notification channel for foreground service
        createNotificationChannel();
        
        // Start foreground service
        startForeground(NOTIFICATION_ID, createNotification());
        
        // Create floating view
        createFloatingView();
        
        // Start network speed monitor
        speedMonitor = new NetworkSpeedMonitor(this);
        speedMonitor.startMonitoring(new NetworkSpeedMonitor.OnNetworkSpeedListener() {
            @Override
            public void onNetworkSpeedUpdate(long downloadSpeed, long uploadSpeed, long totalSpeed) {
                updateSpeedText(downloadSpeed, uploadSpeed, totalSpeed);
            }
        });
        
        // Register broadcast receiver for settings changes
        registerReceiver(settingsChangeReceiver, new IntentFilter("com.netspeedindicator.SETTINGS_CHANGED"));
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        
        // Stop network speed monitor
        if (speedMonitor != null) {
            speedMonitor.stopMonitoring();
        }
        
        // Remove floating view
        if (floatingView != null && windowManager != null) {
            windowManager.removeView(floatingView);
        }
        
        // Unregister receiver
        unregisterReceiver(settingsChangeReceiver);
    }
    

    
    private void createFloatingView() {
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        
        LayoutInflater inflater = (LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE);
        floatingView = inflater.inflate(R.layout.floating_window, null);
        
        speedText = floatingView.findViewById(R.id.speed_text);
        container = floatingView.findViewById(R.id.container);
        
        // Load saved position
        int x = preferences.getInt("position_x", 50);
        int y = preferences.getInt("position_y", 50);
        
        // Setup window parameters
        params = new WindowManager.LayoutParams();
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // For Android O and above, TYPE_APPLICATION_OVERLAY is the only valid type for overlays
            params.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        } else {
            params.type = showOverStatusBar ? WindowManager.LayoutParams.TYPE_SYSTEM_ERROR : WindowManager.LayoutParams.TYPE_PHONE;
        }
        
        params.format = PixelFormat.TRANSLUCENT;
        params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        params.gravity = Gravity.TOP | Gravity.LEFT;
        params.x = x;
        params.y = y;
        params.width = WindowManager.LayoutParams.WRAP_CONTENT;
        params.height = WindowManager.LayoutParams.WRAP_CONTENT;
        
        // Apply initial settings
        updateViewSettings();
        
        // Setup touch listeners for movement
        floatingView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (isLocked) {
                    return false;
                }
                
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        lastX = params.x;
                        lastY = params.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        return true;
                        
                    case MotionEvent.ACTION_MOVE:
                        params.x = lastX + (int) (event.getRawX() - initialTouchX);
                        params.y = lastY + (int) (event.getRawY() - initialTouchY);
                        
                        // Ensure the view stays within screen bounds
                        if (params.x < 0) params.x = 0;
                        if (params.y < 0) params.y = 0;
                        if (params.x > screenWidth - floatingView.getWidth()) {
                            params.x = screenWidth - floatingView.getWidth();
                        }
                        if (params.y > screenHeight - floatingView.getHeight()) {
                            params.y = screenHeight - floatingView.getHeight();
                        }
                        
                        windowManager.updateViewLayout(floatingView, params);
                        return true;
                        
                    case MotionEvent.ACTION_UP:
                        // Save position
                        SharedPreferences.Editor editor = preferences.edit();
                        editor.putInt("position_x", params.x);
                        editor.putInt("position_y", params.y);
                        editor.apply();
                        return true;
                }
                return false;
            }
        });
        
        // Add floating view to window manager
        windowManager.addView(floatingView, params);
    }
    
    private void updateSpeedText(long downloadSpeed, long uploadSpeed, long totalSpeed) {
        // Check low speed hide
        if (isLowSpeedHideEnabled && totalSpeed < lowSpeedThreshold) {
            if (floatingView.getVisibility() != View.GONE) {
                floatingView.setVisibility(View.GONE);
            }
            return;
        } else {
            if (floatingView.getVisibility() != View.VISIBLE) {
                floatingView.setVisibility(View.VISIBLE);
            }
        }
        
        String speedString = "";
        
        switch (speedFormat) {
            case 0: // Total speed
                speedString = NetworkSpeedMonitor.formatSpeed(totalSpeed);
                break;
                
            case 1: // Up/Down horizontal
                speedString = getString(R.string.down) + NetworkSpeedMonitor.formatSpeed(downloadSpeed) + " " +
                              getString(R.string.up) + NetworkSpeedMonitor.formatSpeed(uploadSpeed);
                break;
                
            case 2: // Up/Down vertical
                speedString = getString(R.string.down) + NetworkSpeedMonitor.formatSpeed(downloadSpeed) + "\n" +
                              getString(R.string.up) + NetworkSpeedMonitor.formatSpeed(uploadSpeed);
                break;
        }
        
        speedText.setText(speedString);
    }
    
    private void updateViewSettings() {
        // Update text settings
        speedText.setTextColor(textColor);
        speedText.setTextSize(textSize);
        speedText.setGravity(textAlignment | Gravity.CENTER_VERTICAL);
        
        // Update window type for status bar
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            params.type = showOverStatusBar ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        } else {
            params.type = showOverStatusBar ? WindowManager.LayoutParams.TYPE_SYSTEM_ERROR : WindowManager.LayoutParams.TYPE_PHONE;
        }
        
        if (windowManager != null && floatingView != null) {
            windowManager.updateViewLayout(floatingView, params);
        }
    }
    
    private void loadSettings() {
        isLocked = preferences.getBoolean("is_locked", false);
        isLowSpeedHideEnabled = preferences.getBoolean("low_speed_hide", false);
        speedFormat = preferences.getInt("speed_format", 0);
        textAlignment = preferences.getInt("text_alignment", Gravity.LEFT);
        textColor = preferences.getInt("text_color", Color.WHITE);
        textSize = preferences.getInt("text_size", 14);
        showOverStatusBar = preferences.getBoolean("show_over_status_bar", false);
        lowSpeedThreshold = preferences.getInt("low_speed_threshold", 1024);
    }
    
    private BroadcastReceiver settingsChangeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            loadSettings();
            updateViewSettings();
        }
    };
    
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Network Speed Indicator",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Shows network speed in floating window");
            
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(channel);
        }
    }
    
    private Notification createNotification() {
        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new Notification.Builder(this, CHANNEL_ID);
        } else {
            builder = new Notification.Builder(this);
        }
        
        builder.setSmallIcon(R.mipmap.ic_launcher)
               .setContentTitle("Network Speed Indicator")
               .setContentText("Running in background")
               .setPriority(Notification.PRIORITY_LOW);
        
        return builder.build();
    }
    
    // Public methods for position adjustment
    public void adjustPosition(int dx, int dy) {
        params.x += dx;
        params.y += dy;
        
        // Ensure within bounds
        if (params.x < 0) params.x = 0;
        if (params.y < 0) params.y = 0;
        if (params.x > screenWidth - floatingView.getWidth()) {
            params.x = screenWidth - floatingView.getWidth();
        }
        if (params.y > screenHeight - floatingView.getHeight()) {
            params.y = screenHeight - floatingView.getHeight();
        }
        
        windowManager.updateViewLayout(floatingView, params);
        
        // Save new position
        SharedPreferences.Editor editor = preferences.edit();
        editor.putInt("position_x", params.x);
        editor.putInt("position_y", params.y);
        editor.apply();
    }
    
    public void toggleLock() {
        isLocked = !isLocked;
        SharedPreferences.Editor editor = preferences.edit();
        editor.putBoolean("is_locked", isLocked);
        editor.apply();
    }
}