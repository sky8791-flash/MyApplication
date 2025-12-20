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

    private final BluetoothAdapter adapter;
    private final Context context;
    private final Listener listener;
    private BluetoothServerSocket serverSocket;
    private BluetoothSocket socket;
    private Thread readThread;
    private final Handler main = new Handler(Looper.getMainLooper());
    private BroadcastReceiver discoveryReceiver;

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
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    stopDiscovery();
                    BluetoothSocket s = device.createRfcommSocketToServiceRecord(APP_UUID);
                    s.connect();
                    handleConnected(s);
                } catch (Exception e) {
                    postError(e);
                }
            }
        }, "bt-connect").start();
    }

    private void handleConnected(BluetoothSocket s) throws IOException {
        if (serverSocket != null) { try { serverSocket.close(); } catch (Exception ignored) {} }
        this.socket = s;
        final BluetoothDevice device = s.getRemoteDevice();
        main.post(new Runnable() {
            @Override
            public void run() {
                listener.onConnected(device);
            }
        });
        startReadLoop(s);
    }

    private void startReadLoop(BluetoothSocket s) {
        readThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try (BufferedReader br = new BufferedReader(new InputStreamReader(s.getInputStream()))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        final String finalLine = line;
                        main.post(new Runnable() {
                            @Override
                            public void run() {
                                listener.onMessage(finalLine);
                            }
                        });
                    }
                    postDisconnect("remote closed");
                } catch (Exception e) {
                    postError(e);
                }
            }
        }, "bt-read");
        readThread.start();
    }

    public void sendLine(String text) {
        try {
            OutputStream os = socket.getOutputStream();
            PrintWriter pw = new PrintWriter(new OutputStreamWriter(os), true);
            pw.println(text);
        } catch (Exception e) {
            postError(e);
        }
    }

    public void close() {
        stopDiscovery();
        try { if (socket != null) socket.close(); } catch (Exception ignored) {}
        try { if (serverSocket != null) serverSocket.close(); } catch (Exception ignored) {}
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