package com.example.myapplication;

import android.content.Context;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.os.Handler;
import android.os.Looper;

import java.io.*;
import java.net.*;
import java.util.*;

public class LanHelper {
    public interface Listener {
        void onServiceFound(String serviceName, String hostAddress);
        void onConnected(String hostName);
        void onDisconnected(String reason);
        void onMessage(String line);
        void onError(Throwable t);
    }

    private static final String SERVICE_TYPE = "_gomoku._tcp.";
    private static final int DEFAULT_PORT = 8888;
    private static final int CONNECTION_TIMEOUT = 30000; // 30 seconds
    private static final int READ_TIMEOUT = 60000; // 60 seconds for reads
    private static final int KEEPALIVE_INTERVAL = 15000; // 15 seconds
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final int SOCKET_BUFFER_SIZE = 8192; // 8KB buffer
    private static final int WRITER_BUFFER_SIZE = 2048; // 2KB buffer for text output
    
    private final Context context;
    private final Listener listener;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    
    private NsdManager nsdManager;
    private NsdManager.DiscoveryListener discoveryListener;
    private NsdManager.RegistrationListener registrationListener;
    private NsdServiceInfo registeredService;
    
    private ServerSocket serverSocket;
    private Socket clientSocket;
    private Thread serverThread;
    private Thread readThread;
    private Thread keepAliveThread;
    private PrintWriter writer;
    
    private Map<String, InetAddress> discoveredServices = new HashMap<>();
    private volatile boolean isConnected = false;
    private volatile boolean shouldReconnect = false;
    private String lastServiceName;
    private long lastDiscoveryTime = 0;
    private String connectionStatus = "未连接";

    public LanHelper(Context ctx, Listener l) {
        this.context = ctx.getApplicationContext();
        this.listener = l;
        this.nsdManager = (NsdManager) context.getSystemService(Context.NSD_SERVICE);
    }

    /* ========== Service Discovery ========== */
    public void startDiscovery() {
        // Prevent rapid discovery restarts
        long now = System.currentTimeMillis();
        if (now - lastDiscoveryTime < 2000) {
            return;
        }
        lastDiscoveryTime = now;
        connectionStatus = "正在扫描局域网设备...";
        
        stopDiscovery();
        
        discoveryListener = new NsdManager.DiscoveryListener() {
            @Override
            public void onDiscoveryStarted(String serviceType) {
                // Discovery started
            }

            @Override
            public void onServiceFound(NsdServiceInfo serviceInfo) {
                if (!serviceInfo.getServiceType().equals(SERVICE_TYPE)) {
                    return;
                }
                
                // Resolve the service to get host information
                nsdManager.resolveService(serviceInfo, new NsdManager.ResolveListener() {
                    @Override
                    public void onResolveFailed(NsdServiceInfo serviceInfo, int errorCode) {
                        postError(new Exception("Resolve failed: " + errorCode));
                    }

                    @Override
                    public void onServiceResolved(NsdServiceInfo resolvedService) {
                        final String serviceName = resolvedService.getServiceName();
                        final InetAddress host = resolvedService.getHost();
                        discoveredServices.put(serviceName, host);
                        
                        mainHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                listener.onServiceFound(serviceName, host.getHostAddress());
                            }
                        });
                    }
                });
            }

            @Override
            public void onServiceLost(NsdServiceInfo serviceInfo) {
                discoveredServices.remove(serviceInfo.getServiceName());
            }

            @Override
            public void onDiscoveryStopped(String serviceType) {
                // Discovery stopped
            }

            @Override
            public void onStartDiscoveryFailed(String serviceType, int errorCode) {
                postError(new Exception("Discovery failed: " + errorCode));
            }

            @Override
            public void onStopDiscoveryFailed(String serviceType, int errorCode) {
                postError(new Exception("Stop discovery failed: " + errorCode));
            }
        };

        nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener);
    }

    public void stopDiscovery() {
        if (discoveryListener != null) {
            try {
                nsdManager.stopServiceDiscovery(discoveryListener);
            } catch (Exception ignored) {}
            discoveryListener = null;
        }
        discoveredServices.clear();
    }

    /* ========== Host Server (Create Room) ========== */
    public void startServer() {
        serverThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    // Create server socket
                    serverSocket = new ServerSocket(DEFAULT_PORT);
                    
                    // Register NSD service
                    registerService();
                    
                    // Wait for client connection
                    Socket socket = serverSocket.accept();
                    handleConnected(socket, "Client");
                    
                } catch (Exception e) {
                    if (!Thread.currentThread().isInterrupted()) {
                        postError(e);
                    }
                }
            }
        }, "lan-server");
        serverThread.start();
    }

    private void registerService() {
        NsdServiceInfo serviceInfo = new NsdServiceInfo();
        serviceInfo.setServiceName("Game_" + android.os.Build.MODEL);
        serviceInfo.setServiceType(SERVICE_TYPE);
        serviceInfo.setPort(DEFAULT_PORT);

        registrationListener = new NsdManager.RegistrationListener() {
            @Override
            public void onServiceRegistered(NsdServiceInfo nsdServiceInfo) {
                registeredService = nsdServiceInfo;
            }

            @Override
            public void onRegistrationFailed(NsdServiceInfo serviceInfo, int errorCode) {
                postError(new Exception("Registration failed: " + errorCode));
            }

            @Override
            public void onServiceUnregistered(NsdServiceInfo serviceInfo) {
                registeredService = null;
            }

            @Override
            public void onUnregistrationFailed(NsdServiceInfo serviceInfo, int errorCode) {
                postError(new Exception("Unregistration failed: " + errorCode));
            }
        };

        nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener);
    }

    /* ========== Client Connect (Join Room) ========== */
    public void connectTo(final String serviceName) {
        lastServiceName = serviceName;
        shouldReconnect = true;
        connectionStatus = "正在连接...";
        connectWithRetry(serviceName, 0);
    }
    
    private void connectWithRetry(final String serviceName, final int attempt) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    stopDiscovery();
                    connectionStatus = "连接尝试 " + (attempt + 1) + "/" + MAX_RETRY_ATTEMPTS;
                    
                    InetAddress host = discoveredServices.get(serviceName);
                    if (host == null) {
                        throw new Exception("服务未找到: " + serviceName);
                    }
                    
                    // Check network connectivity
                    if (!host.isReachable(5000)) {
                        throw new Exception("网络不可达，请检查WiFi连接");
                    }
                    
                    Socket socket = new Socket();
                    socket.setReuseAddress(true);
                    socket.setKeepAlive(true);
                    socket.setSendBufferSize(SOCKET_BUFFER_SIZE);
                    socket.setReceiveBufferSize(SOCKET_BUFFER_SIZE);
                    socket.setTcpNoDelay(true); // Disable Nagle's algorithm for low latency
                    
                    socket.connect(new InetSocketAddress(host, DEFAULT_PORT), CONNECTION_TIMEOUT);
                    socket.setSoTimeout(READ_TIMEOUT);
                    
                    handleConnected(socket, serviceName);
                    
                } catch (Exception e) {
                    if (shouldReconnect && attempt < MAX_RETRY_ATTEMPTS) {
                        // Progressive backoff: 2s, 4s, 8s
                        final long delay = 2000 * (1L << attempt);
                        connectionStatus = "重试中... (" + (delay/1000) + "秒后)";
                        mainHandler.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                connectWithRetry(serviceName, attempt + 1);
                            }
                        }, delay);
                    } else {
                        connectionStatus = "连接失败";
                        postError(e);
                    }
                }
            }
        }, "lan-connect-" + attempt).start();
    }

    private void handleConnected(Socket socket, final String hostName) throws IOException {
        this.clientSocket = socket;
        this.isConnected = true;
        this.connectionStatus = "已连接";
        
        // Initialize writer with buffering for efficient sending
        writer = new PrintWriter(new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()), WRITER_BUFFER_SIZE), true);
        
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                listener.onConnected(hostName);
            }
        });
        
        startReadLoop(socket);
        startKeepAlive();
    }

    private void startReadLoop(Socket socket) {
        readThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try (BufferedReader br = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
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
                        mainHandler.post(new Runnable() {
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
                    if (shouldReconnect && lastServiceName != null && !Thread.currentThread().isInterrupted()) {
                        mainHandler.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                connectWithRetry(lastServiceName, 0);
                            }
                        }, 3000);
                    }
                    if (!Thread.currentThread().isInterrupted()) {
                        postDisconnect("连接丢失");
                    }
                }
            }
        }, "lan-read");
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
        }, "lan-keepalive");
        keepAliveThread.start();
    }

    public void sendLine(String text) {
        try {
            if (writer != null && isConnected) {
                writer.println(text);
                if (writer.checkError()) {
                    throw new IOException("写入错误");
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
        
        // Unregister service
        if (registrationListener != null && registeredService != null) {
            try {
                nsdManager.unregisterService(registrationListener);
            } catch (Exception ignored) {}
        }
        
        // Interrupt threads
        if (keepAliveThread != null) {
            keepAliveThread.interrupt();
            keepAliveThread = null;
        }
        if (serverThread != null) {
            serverThread.interrupt();
            serverThread = null;
        }
        if (readThread != null) {
            readThread.interrupt();
            readThread = null;
        }
        
        // Close resources
        try { if (writer != null) writer.close(); } catch (Exception ignored) {}
        try { if (clientSocket != null) clientSocket.close(); } catch (Exception ignored) {}
        try { if (serverSocket != null) serverSocket.close(); } catch (Exception ignored) {}
        
        writer = null;
        clientSocket = null;
        serverSocket = null;
    }
    
    public boolean isConnected() {
        return isConnected && clientSocket != null && clientSocket.isConnected() && !clientSocket.isClosed();
    }
    
    public String getConnectionStatus() {
        return connectionStatus;
    }

    private void postDisconnect(final String reason) {
        connectionStatus = "已断开: " + reason;
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                listener.onDisconnected(reason);
            }
        });
    }

    private void postError(final Throwable t) {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                listener.onError(t);
            }
        });
    }
}
