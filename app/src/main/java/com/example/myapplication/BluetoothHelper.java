package com.example.myapplication;


import android.bluetooth.*;
import android.content.*;
import android.os.Handler;
import android.os.Looper;
import androidx.annotation.Nullable;

import java.io.*;
import java.util.*;

public class BluetoothHelper {
    public interface Listener {
        void onDeviceFound(BluetoothDevice device);
        void onConnected(BluetoothDevice device);
        void onDisconnected(String reason);
        void onMessage(String line);
        void onError(Throwable t);
    }

    private static final UUID APP_UUID = UUID.fromString("8a72b2fa-1c4e-4c4b-b8e2-6cfe2de2fa00");
    private static final int CONNECTION_TIMEOUT = 30000; // 30 seconds
    private static final int KEEPALIVE_INTERVAL = 15000; // 15 seconds
    private static final int MAX_RETRY_ATTEMPTS = 3;

    private final BluetoothAdapter adapter;
    private final Context context;
    private final Listener listener;
    private BluetoothServerSocket serverSocket;
    private BluetoothSocket socket;
    private Thread readThread;
    private Thread keepAliveThread;
    private final Handler main = new Handler(Looper.getMainLooper());
    private BroadcastReceiver discoveryReceiver;
    private volatile boolean isConnected = false;
    private volatile boolean shouldReconnect = false;
    private BluetoothDevice lastDevice;
    private PrintWriter writer;

    public BluetoothHelper(Context ctx, Listener l) {
        this.context = ctx.getApplicationContext();
        this.adapter = BluetoothAdapter.getDefaultAdapter();
        this.listener = l;
    }

    /* ========== 发现设备 ========== */
    public void startDiscovery() {
        stopDiscovery();
        discoveryReceiver = new BroadcastReceiver() {
            @Override public void onReceive(Context ctx, Intent intent) {
                if (BluetoothDevice.ACTION_FOUND.equals(intent.getAction())) {
                    final BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                    if (device != null) main.post(new Runnable() {
                        @Override
                        public void run() {
                            listener.onDeviceFound(device);
                        }
                    });
                }
            }
        };
        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        context.registerReceiver(discoveryReceiver, filter);
        adapter.startDiscovery();
    }

    public void stopDiscovery() {
        if (discoveryReceiver != null) {
            context.unregisterReceiver(discoveryReceiver);
            discoveryReceiver = null;
        }
        if (adapter.isDiscovering()) adapter.cancelDiscovery();
    }

    /* ========== 服务端监听 ========== */
    public void startServerAccept() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    serverSocket = adapter.listenUsingRfcommWithServiceRecord("Gomoku", APP_UUID);
                    BluetoothSocket s = serverSocket.accept();
                    handleConnected(s);
                } catch (Exception e) {
                    postError(e);
                }
            }
        }, "bt-accept").start();
    }

    /* ========== 客户端连接 ========== */
    public void connectTo(BluetoothDevice device) {
        lastDevice = device;
        shouldReconnect = true;
        connectWithRetry(device, 0);
    }
    
    private void connectWithRetry(final BluetoothDevice device, final int attempt) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    stopDiscovery();
                    
                    // Try standard connection first
                    BluetoothSocket s = null;
                    try {
                        s = device.createRfcommSocketToServiceRecord(APP_UUID);
                        s.connect();
                    } catch (IOException e) {
                        // Fallback: try insecure connection
                        if (s != null) {
                            try { s.close(); } catch (Exception ignored) {}
                        }
                        s = device.createInsecureRfcommSocketToServiceRecord(APP_UUID);
                        s.connect();
                    }
                    
                    handleConnected(s);
                } catch (Exception e) {
                    if (shouldReconnect && attempt < MAX_RETRY_ATTEMPTS) {
                        main.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                connectWithRetry(device, attempt + 1);
                            }
                        }, 2000); // Wait 2 seconds before retry
                    } else {
                        postError(e);
                    }
                }
            }
        }, "bt-connect-" + attempt).start();
    }

    private void handleConnected(BluetoothSocket s) throws IOException {
        if (serverSocket != null) { try { serverSocket.close(); } catch (Exception ignored) {} }
        this.socket = s;
        this.isConnected = true;
        final BluetoothDevice device = s.getRemoteDevice();
        
        // Initialize writer for efficient sending
        writer = new PrintWriter(new OutputStreamWriter(s.getOutputStream()), true);
        
        main.post(new Runnable() {
            @Override
            public void run() {
                listener.onConnected(device);
            }
        });
        
        startReadLoop(s);
        startKeepAlive();
    }

    private void startReadLoop(BluetoothSocket s) {
        readThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try (BufferedReader br = new BufferedReader(new InputStreamReader(s.getInputStream()))) {
                    String line;
                    while (isConnected && (line = br.readLine()) != null) {
                        // Skip keepalive pings
                        if ("PING".equals(line)) {
                            sendLine("PONG");
                            continue;
                        }
                        if ("PONG".equals(line)) {
                            continue;
                        }
                        
                        final String finalLine = line;
                        main.post(new Runnable() {
                            @Override
                            public void run() {
                                listener.onMessage(finalLine);
                            }
                        });
                    }
                    isConnected = false;
                    postDisconnect("连接断开");
                } catch (Exception e) {
                    isConnected = false;
                    if (shouldReconnect && lastDevice != null) {
                        main.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                connectWithRetry(lastDevice, 0);
                            }
                        }, 3000);
                    }
                    postDisconnect("连接丢失");
                }
            }
        }, "bt-read");
        readThread.start();
    }
    
    private void startKeepAlive() {
        keepAliveThread = new Thread(new Runnable() {
            @Override
            public void run() {
                while (isConnected && !Thread.currentThread().isInterrupted()) {
                    try {
                        Thread.sleep(KEEPALIVE_INTERVAL);
                        if (isConnected) {
                            sendLine("PING");
                        }
                    } catch (InterruptedException e) {
                        break;
                    } catch (Exception e) {
                        // Ignore keepalive errors
                    }
                }
            }
        }, "bt-keepalive");
        keepAliveThread.start();
    }

    public void sendLine(String text) {
        try {
            if (writer != null && isConnected) {
                writer.println(text);
                if (writer.checkError()) {
                    throw new IOException("Write error");
                }
            }
        } catch (Exception e) {
            isConnected = false;
            postError(e);
        }
    }

    public void close() {
        shouldReconnect = false;
        isConnected = false;
        stopDiscovery();
        
        if (keepAliveThread != null) {
            keepAliveThread.interrupt();
            keepAliveThread = null;
        }
        
        if (readThread != null) {
            readThread.interrupt();
            readThread = null;
        }
        
        try { if (writer != null) writer.close(); } catch (Exception ignored) {}
        try { if (socket != null) socket.close(); } catch (Exception ignored) {}
        try { if (serverSocket != null) serverSocket.close(); } catch (Exception ignored) {}
        
        writer = null;
        socket = null;
        serverSocket = null;
    }
    
    public boolean isConnected() {
        return isConnected && socket != null && socket.isConnected();
    }

    private void postDisconnect(String reason) {
        final String finalReason = reason;
        main.post(new Runnable() {
            @Override
            public void run() {
                listener.onDisconnected(finalReason);
            }
        });
    }
    private void postError(Throwable t) {
        final Throwable finalT = t;
        main.post(new Runnable() {
            @Override
            public void run() {
                listener.onError(finalT);
            }
        });
    }
}