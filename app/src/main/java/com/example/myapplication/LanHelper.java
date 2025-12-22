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
    
    private Map<String, InetAddress> discoveredServices = new HashMap<>();

    public LanHelper(Context ctx, Listener l) {
        this.context = ctx.getApplicationContext();
        this.listener = l;
        this.nsdManager = (NsdManager) context.getSystemService(Context.NSD_SERVICE);
    }

    /* ========== Service Discovery ========== */
    public void startDiscovery() {
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
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    stopDiscovery();
                    
                    InetAddress host = discoveredServices.get(serviceName);
                    if (host == null) {
                        postError(new Exception("Service not found: " + serviceName));
                        return;
                    }
                    
                    Socket socket = new Socket(host, DEFAULT_PORT);
                    handleConnected(socket, serviceName);
                    
                } catch (Exception e) {
                    postError(e);
                }
            }
        }, "lan-connect").start();
    }

    private void handleConnected(Socket socket, final String hostName) throws IOException {
        this.clientSocket = socket;
        
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                listener.onConnected(hostName);
            }
        });
        
        startReadLoop(socket);
    }

    private void startReadLoop(Socket socket) {
        readThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try (BufferedReader br = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        final String finalLine = line;
                        mainHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                listener.onMessage(finalLine);
                            }
                        });
                    }
                    postDisconnect("Connection closed");
                } catch (Exception e) {
                    if (!Thread.currentThread().isInterrupted()) {
                        postDisconnect("Connection lost");
                    }
                }
            }
        }, "lan-read");
        readThread.start();
    }

    public void sendLine(String text) {
        try {
            if (clientSocket != null && !clientSocket.isClosed()) {
                OutputStream os = clientSocket.getOutputStream();
                PrintWriter pw = new PrintWriter(new OutputStreamWriter(os), true);
                pw.println(text);
            }
        } catch (Exception e) {
            postError(e);
        }
    }

    public void close() {
        stopDiscovery();
        
        // Unregister service
        if (registrationListener != null && registeredService != null) {
            try {
                nsdManager.unregisterService(registrationListener);
            } catch (Exception ignored) {}
        }
        
        // Close sockets
        try { if (clientSocket != null) clientSocket.close(); } catch (Exception ignored) {}
        try { if (serverSocket != null) serverSocket.close(); } catch (Exception ignored) {}
        
        // Interrupt threads
        if (serverThread != null) serverThread.interrupt();
        if (readThread != null) readThread.interrupt();
    }

    private void postDisconnect(final String reason) {
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
