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
    private static final int SOCKET_BUFFER_SIZE = 8192; // 8KB buffer for better performance
    private static final int WRITER_BUFFER_SIZE = 2048; // 2KB buffer for text output

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
    private String connectionStatus = "未连接";

    public BluetoothHelper(Context ctx, Listener l) {
        this.context = ctx.getApplicationContext();
        this.adapter = BluetoothAdapter.getDefaultAdapter();
        this.listener = l;
    }

    /* ========== 发现设备 ========== */
    public void startDiscovery() {
        stopDiscovery();
        connectionStatus = "正在扫描设备...";
        
        // First, report all paired devices
        Set<BluetoothDevice> pairedDevices = adapter.getBondedDevices();
        if (pairedDevices != null) {
            for (final BluetoothDevice device : pairedDevices) {
                main.post(new Runnable() {
                    @Override
                    public void run() {
                        listener.onDeviceFound(device);
                    }
                });
            }
        }
        
        // Then start discovering new devices
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
        connectionStatus = "正在连接...";
        connectWithRetry(device, 0);
    }
    
    private void connectWithRetry(final BluetoothDevice device, final int attempt) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    stopDiscovery();
                    connectionStatus = "连接尝试 " + (attempt + 1) + "/" + MAX_RETRY_ATTEMPTS;
                    
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
                    
                    // Validate socket streams
                    try {
                        s.getInputStream();
                        s.getOutputStream();
                    } catch (IOException e) {
                        // Socket validation failed
                        throw e;
                    }
                    
                    handleConnected(s);
                } catch (Exception e) {
                    if (shouldReconnect && attempt < MAX_RETRY_ATTEMPTS) {
                        // Progressive backoff: 2s, 4s, 8s
                        final long delay = 2000 * (1L << attempt);
                        connectionStatus = "重试中... (" + (delay/1000) + "秒后)";
                        main.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                connectWithRetry(device, attempt + 1);
                            }
                        }, delay);
                    } else {
                        connectionStatus = "连接失败";
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
        this.connectionStatus = "已连接";
        final BluetoothDevice device = s.getRemoteDevice();
        
        // Initialize writer with buffering for efficient sending
        writer = new PrintWriter(new BufferedWriter(new OutputStreamWriter(s.getOutputStream()), WRITER_BUFFER_SIZE), true);
        
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
    
    public String getConnectionStatus() {
        return connectionStatus;
    }

    private void postDisconnect(String reason) {
        connectionStatus = "已断开: " + reason;
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