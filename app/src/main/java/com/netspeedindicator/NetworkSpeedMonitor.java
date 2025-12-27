package com.netspeedindicator;

import android.content.Context;
import android.net.TrafficStats;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;

import java.util.Formatter;
import java.util.Locale;

public class NetworkSpeedMonitor {
    private Context context;
    private long lastTotalRxBytes = 0;
    private long lastTotalTxBytes = 0;
    private long lastTimeStamp = 0;
    private Handler handler;
    private boolean isRunning = false;
    private OnNetworkSpeedListener listener;
    private static final int MSG_UPDATE_SPEED = 1;

    public interface OnNetworkSpeedListener {
        void onNetworkSpeedUpdate(long downloadSpeed, long uploadSpeed, long totalSpeed);
    }

    public NetworkSpeedMonitor(Context context) {
        this.context = context;
        init();
    }

    private void init() {
        lastTotalRxBytes = TrafficStats.getTotalRxBytes();
        lastTotalTxBytes = TrafficStats.getTotalTxBytes();
        lastTimeStamp = System.currentTimeMillis();

        handler = new Handler(Looper.getMainLooper()) {
            @Override
            public void handleMessage(Message msg) {
                if (msg.what == MSG_UPDATE_SPEED) {
                    updateNetworkSpeed();
                }
            }
        };
    }

    public void startMonitoring(OnNetworkSpeedListener listener) {
        if (isRunning) {
            return;
        }
        this.listener = listener;
        isRunning = true;
        handler.sendEmptyMessage(MSG_UPDATE_SPEED);
    }

    public void stopMonitoring() {
        isRunning = false;
        handler.removeMessages(MSG_UPDATE_SPEED);
        listener = null;
    }

    private void updateNetworkSpeed() {
        if (!isRunning) {
            return;
        }

        long currentRxBytes = TrafficStats.getTotalRxBytes();
        long currentTxBytes = TrafficStats.getTotalTxBytes();
        long currentTimeStamp = System.currentTimeMillis();

        long rxBytes = currentRxBytes - lastTotalRxBytes;
        long txBytes = currentTxBytes - lastTotalTxBytes;
        long timeInterval = currentTimeStamp - lastTimeStamp;

        if (timeInterval == 0) {
            timeInterval = 1;
        }

        long downloadSpeed = rxBytes * 1000 / timeInterval;
        long uploadSpeed = txBytes * 1000 / timeInterval;
        long totalSpeed = downloadSpeed + uploadSpeed;

        if (listener != null) {
            listener.onNetworkSpeedUpdate(downloadSpeed, uploadSpeed, totalSpeed);
        }

        lastTotalRxBytes = currentRxBytes;
        lastTotalTxBytes = currentTxBytes;
        lastTimeStamp = currentTimeStamp;

        handler.sendEmptyMessageDelayed(MSG_UPDATE_SPEED, 1000);
    }

    public static String formatSpeed(long speed) {
        if (speed <= 0) {
            return "0 B/s";
        }

        String[] units = {"B/s", "KB/s", "MB/s", "GB/s"};
        int unitIndex = 0;
        double formattedSpeed = speed;

        while (formattedSpeed >= 1024 && unitIndex < units.length - 1) {
            formattedSpeed /= 1024;
            unitIndex++;
        }

        return new Formatter(Locale.getDefault()).format("%.1f %s", formattedSpeed, units[unitIndex]).toString();
    }
}
