package com.example.myapplication;

import android.Manifest;
import android.bluetooth.BluetoothDevice;
import android.content.pm.PackageManager;
import android.os.*;
import android.view.View;
import android.widget.*;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.card.MaterialCardView;

import org.json.JSONObject;
import java.util.*;

public class GameActivity extends AppCompatActivity implements BluetoothHelper.Listener, LanHelper.Listener {

    private BluetoothHelper bt;
    private LanHelper lan;
    private ArrayAdapter<String> deviceAdapter;
    private Map<String, BluetoothDevice> deviceMap = new HashMap<>();
    private Map<String, String> lanServiceMap = new HashMap<>();
    private boolean isUsingLan = false;
    private GomokuEngine gomokuEngine;
    private GoEngine goEngine;
    private XiangqiEngine xiangqiEngine;
    private BoardView.GameType currentGameType;
    private int myColor = 1;
    private int turn = 1;
    private BoardView boardView;
    private MaterialCardView cardDevices;
    private TextView tvGameTitle;
    private String gameTypeStr;
    
    // AI opponent
    private SimpleAI ai;
    private boolean playingAgainstAI = false;
    private final Handler aiHandler = new Handler(Looper.getMainLooper());
    private String aiDifficultyStr = "";
    
    // Game state
    private boolean gameStarted = false;
    private boolean gameEnded = false;
    
    // Game tracking
    private GameHistoryManager historyManager;
    private long gameStartTime = 0;
    private int playerMoveCount = 0;
    private int opponentMoveCount = 0;
    private String opponentName = "未知";

    private final String[] perms = new String[]{
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.ACCESS_FINE_LOCATION
    };
    private final ActivityResultContracts.RequestMultiplePermissions permsContract = new ActivityResultContracts.RequestMultiplePermissions();
    private final androidx.activity.result.ActivityResultLauncher<String[]> permLauncher =
            registerForActivityResult(permsContract, new androidx.activity.result.ActivityResultCallback<Map<String, Boolean>>() {
                @Override
                public void onActivityResult(Map<String, Boolean> r) {
                    startDiscovery();
                }
            });

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_game);
        
        bt = new BluetoothHelper(this, this);
        lan = new LanHelper(this, this);
        historyManager = new GameHistoryManager(this);

        // Get game type from intent
        gameTypeStr = getIntent().getStringExtra("GAME_TYPE");
        if (gameTypeStr == null) gameTypeStr = "GOMOKU";

        tvGameTitle = findViewById(R.id.tvGameTitle);
        boardView = findViewById(R.id.boardView);
        cardDevices = findViewById(R.id.cardDevices);

        // Initialize game based on type
        switch (gameTypeStr) {
            case "GOMOKU":
                tvGameTitle.setText("🎯 五子棋");
                currentGameType = BoardView.GameType.GOMOKU;
                gomokuEngine = new GomokuEngine(15);
                boardView.bindGomokuEngine(gomokuEngine);
                break;
            case "GO":
                tvGameTitle.setText("⚫ 围棋");
                currentGameType = BoardView.GameType.GO;
                goEngine = new GoEngine(19);
                boardView.bindGoEngine(goEngine);
                break;
            case "XIANGQI":
                tvGameTitle.setText("♟️ 象棋");
                currentGameType = BoardView.GameType.XIANGQI;
                xiangqiEngine = new XiangqiEngine();
                boardView.bindXiangqiEngine(xiangqiEngine);
                break;
        }

        boardView.setOnPlaceListener(new BoardView.OnPlaceListener() {
            @Override
            public void onPlace(int r, int c) {
                tryPlace(r, c);
            }
        });
        boardView.setOnXiangqiMoveListener(new BoardView.OnXiangqiMoveListener() {
            @Override
            public void onMove(int fromR, int fromC, int toR, int toC) {
                tryXiangqiMove(fromR, fromC, toR, toC);
            }
        });

        ListView list = findViewById(R.id.listDevices);
        deviceAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1);
        list.setAdapter(deviceAdapter);
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> p, View v, int pos, long id) {
                String key = deviceAdapter.getItem(pos);
                
                // Try Bluetooth first
                BluetoothDevice d = deviceMap.get(key);
                if (d != null) {
                    bt.connectTo(d);
                    return;
                }
                
                // Try LAN
                String serviceName = lanServiceMap.get(key);
                if (serviceName != null) {
                    lan.connectTo(serviceName);
                }
            }
        });

        findViewById(R.id.btnBack).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        findViewById(R.id.btnDiscover).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                checkPermAndDiscover();
                cardDevices.setVisibility(View.VISIBLE);
            }
        });
        findViewById(R.id.btnHost).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                bt.startServerAccept();
            }
        });
        findViewById(R.id.btnAI).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showAIDifficultyDialog();
            }
        });
        findViewById(R.id.btnUndo).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (playingAgainstAI) {
                    performUndo();
                } else {
                    sendUndoRequest();
                }
            }
        });
        
        // LAN buttons
        findViewById(R.id.btnLanDiscover).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startLanDiscovery();
                cardDevices.setVisibility(View.VISIBLE);
            }
        });
        findViewById(R.id.btnLanHost).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startLanServer();
            }
        });
    }
    
    private void showAIDifficultyDialog() {
        String[] difficulties = {"简单", "中等", "困难"};
        new AlertDialog.Builder(this)
                .setTitle("选择AI难度")
                .setItems(difficulties, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        SimpleAI.Difficulty difficulty;
                        switch (which) {
                            case 0: difficulty = SimpleAI.Difficulty.EASY; break;
                            case 1: difficulty = SimpleAI.Difficulty.MEDIUM; break;
                            case 2: difficulty = SimpleAI.Difficulty.HARD; break;
                            default: difficulty = SimpleAI.Difficulty.MEDIUM;
                        }
                        startAIGame(difficulty);
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }
    
    private void startAIGame(SimpleAI.Difficulty difficulty) {
        ai = new SimpleAI(difficulty);
        playingAgainstAI = true;
        myColor = 1;
        turn = 1;
        gameStarted = true;
        gameEnded = false;
        
        // Track game start
        gameStartTime = System.currentTimeMillis();
        playerMoveCount = 0;
        opponentMoveCount = 0;
        switch (difficulty) {
            case EASY: aiDifficultyStr = "AI-简单"; opponentName = "AI-简单"; break;
            case MEDIUM: aiDifficultyStr = "AI-中等"; opponentName = "AI-中等"; break;
            case HARD: aiDifficultyStr = "AI-困难"; opponentName = "AI-困难"; break;
        }
        
        // Reset game
        switch (currentGameType) {
            case GOMOKU:
                gomokuEngine = new GomokuEngine(15);
                boardView.bindGomokuEngine(gomokuEngine);
                break;
            case GO:
                goEngine = new GoEngine(19);
                boardView.bindGoEngine(goEngine);
                break;
            case XIANGQI:
                xiangqiEngine = new XiangqiEngine();
                boardView.bindXiangqiEngine(xiangqiEngine);
                break;
        }
        
        Toast.makeText(this, "AI对战开始！你是" + (myColor == 1 ? "黑方" : "红方"), Toast.LENGTH_SHORT).show();
    }
    
    private void performUndo() {
        if (currentGameType == BoardView.GameType.GOMOKU) {
            gomokuEngine.undo(2); // Undo both AI and player moves
        } else if (currentGameType == BoardView.GameType.GO) {
            goEngine.undo(2);
        } else if (currentGameType == BoardView.GameType.XIANGQI) {
            xiangqiEngine.undo(2);
        }
        boardView.invalidate();
        turn = myColor;
    }
    
    private void makeAIMove() {
        aiHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (gameEnded) return; // Don't make AI move if game ended
                
                int aiColor = other(myColor);
                
                if (currentGameType == BoardView.GameType.XIANGQI) {
                    int[] move = ai.findBestXiangqiMove(xiangqiEngine, aiColor);
                    if (move != null && move.length == 4) {
                        if (xiangqiEngine.place(move[0], move[1], move[2], move[3], aiColor)) {
                            opponentMoveCount++; // Track AI move
                            boardView.invalidate();
                            if (xiangqiEngine.checkWin(move[2], move[3])) {
                                gameEnded = true;
                                showGameEndDialog("AI获胜！");
                            } else {
                                turn = myColor;
                            }
                        } else {
                            // Move failed validation - this shouldn't happen with proper validation
                            Toast.makeText(GameActivity.this, "AI移动失败，回合返还给你", Toast.LENGTH_SHORT).show();
                        turn = myColor; // Give turn back to player
                        boardView.invalidate();
                    }
                } else {
                    // AI couldn't find a valid move
                    Toast.makeText(GameActivity.this, "AI无法找到有效移动，回合返还给你", Toast.LENGTH_SHORT).show();
                    turn = myColor; // Give turn back to player
                    boardView.invalidate();
                }
            } else {
                // For Gomoku and Go
                int[][] board = (currentGameType == BoardView.GameType.GOMOKU) ? 
                    gomokuEngine.getBoard() : goEngine.getBoard();
                int size = board.length;
                
                int[] move = ai.findBestMoveForBoard(board, aiColor, size);
                if (move != null) {
                    boolean success = false;
                    if (currentGameType == BoardView.GameType.GOMOKU) {
                        success = gomokuEngine.place(move[0], move[1], aiColor);
                        if (success) {
                            opponentMoveCount++; // Track AI move
                            if (gomokuEngine.checkWin(move[0], move[1])) {
                                gameEnded = true;
                                showGameEndDialog("AI获胜！");
                            }
                        }
                    } else if (currentGameType == BoardView.GameType.GO) {
                        success = goEngine.place(move[0], move[1], aiColor);
                        if (success) {
                            opponentMoveCount++; // Track AI move
                            if (goEngine.checkWin(move[0], move[1])) {
                                gameEnded = true;
                                showGameEndDialog("AI获胜！");
                            }
                        }
                    }
                    
                    if (success && !gameEnded) {
                        boardView.invalidate();
                        turn = myColor;
                    } else if (!success) {
                        // Move failed, return turn to player
                        Toast.makeText(GameActivity.this, "AI移动失败，回合返还给你", Toast.LENGTH_SHORT).show();
                        turn = myColor;
                        boardView.invalidate();
                    }
                } else {
                    // AI couldn't find a valid move
                    Toast.makeText(GameActivity.this, "AI无法找到有效移动，回合返还给你", Toast.LENGTH_SHORT).show();
                    turn = myColor;
                    boardView.invalidate();
                }
            }
            }
        }, 500); // 500ms delay for AI move
    }

    private void checkPermAndDiscover() {
        List<String> need = new ArrayList<>();
        for (String p : perms) if (checkSelfPermission(p) != PackageManager.PERMISSION_GRANTED) need.add(p);
        if (!need.isEmpty()) permLauncher.launch(need.toArray(new String[0]));
        else startDiscovery();
    }

    private void startDiscovery() {
        deviceAdapter.clear();
        deviceMap.clear();
        bt.startDiscovery();
        Toast.makeText(this, "开始扫描...", Toast.LENGTH_SHORT).show();
    }

    private void tryPlace(int r, int c) {
        if (!gameStarted) { 
            Toast.makeText(this, "请先开始游戏（点击AI对战或连接蓝牙）", Toast.LENGTH_SHORT).show(); 
            return; 
        }
        if (gameEnded) {
            Toast.makeText(this, "游戏已结束，请重新开始", Toast.LENGTH_SHORT).show();
            return;
        }
        if (turn != myColor) { Toast.makeText(this, "等待对手", Toast.LENGTH_SHORT).show(); return; }
        
        boolean success = false;
        if (currentGameType == BoardView.GameType.GOMOKU) {
            success = gomokuEngine.place(r, c, myColor);
        } else if (currentGameType == BoardView.GameType.GO) {
            success = goEngine.place(r, c, myColor);
        }
        
        if (!success) return;
        playerMoveCount++; // Track player move
        boardView.invalidate();
        
        if (!playingAgainstAI) {
            sendMove(r, c, myColor, -1, -1);
        }
        
        boolean won = false;
        if (currentGameType == BoardView.GameType.GOMOKU) {
            won = gomokuEngine.checkWin(r, c);
        } else if (currentGameType == BoardView.GameType.GO) {
            won = goEngine.checkWin(r, c);
        }
        
        if (won) {
            gameEnded = true;
            showGameEndDialog("你赢了!");
        } else {
            turn = other(myColor);
            if (playingAgainstAI) {
                makeAIMove();
            }
        }
    }

    private void tryXiangqiMove(int fromR, int fromC, int toR, int toC) {
        if (!gameStarted) { 
            Toast.makeText(this, "请先开始游戏（点击AI对战或连接蓝牙）", Toast.LENGTH_SHORT).show(); 
            return; 
        }
        if (gameEnded) {
            Toast.makeText(this, "游戏已结束，请重新开始", Toast.LENGTH_SHORT).show();
            return;
        }
        if (turn != myColor) { Toast.makeText(this, "等待对手", Toast.LENGTH_SHORT).show(); return; }
        if (!xiangqiEngine.place(fromR, fromC, toR, toC, myColor)) return;
        
        playerMoveCount++; // Track player move
        boardView.invalidate();
        
        if (!playingAgainstAI) {
            sendMove(toR, toC, myColor, fromR, fromC);
        }
        
        if (xiangqiEngine.checkWin(toR, toC)) {
            gameEnded = true;
            showGameEndDialog("你赢了!");
        } else {
            turn = other(myColor);
            if (playingAgainstAI) {
                makeAIMove();
            }
        }
    }

    private void onRemoteMove(int r, int c, int color, int fromR, int fromC) {
        opponentMoveCount++; // Track opponent move
        if (currentGameType == BoardView.GameType.XIANGQI) {
            xiangqiEngine.place(fromR, fromC, r, c, color);
            if (xiangqiEngine.checkWin(r, c)) {
                gameEnded = true;
                showGameEndDialog("你输了");
            }
        } else if (currentGameType == BoardView.GameType.GOMOKU) {
            gomokuEngine.place(r, c, color);
            if (gomokuEngine.checkWin(r, c)) {
                gameEnded = true;
                showGameEndDialog("你输了");
            }
        } else if (currentGameType == BoardView.GameType.GO) {
            goEngine.place(r, c, color);
            if (goEngine.checkWin(r, c)) {
                gameEnded = true;
                showGameEndDialog("你输了");
            }
        }
        boardView.invalidate();
        if (!gameEnded) {
            turn = myColor;
        }
    }

    private void sendMove(int r, int c, int color, int fromR, int fromC) {
        try {
            JSONObject o = new JSONObject();
            o.put("type", "move"); 
            o.put("r", r); 
            o.put("c", c); 
            o.put("color", color);
            o.put("gameType", currentGameType.name());
            if (currentGameType == BoardView.GameType.XIANGQI) {
                o.put("fromR", fromR);
                o.put("fromC", fromC);
            }
            sendMessage(o.toString());
        } catch (Exception e) { 
            e.printStackTrace();
            Toast.makeText(this, "发送移动失败", Toast.LENGTH_SHORT).show();
        }
    }

    private int other(int c) { return c == 1 ? 2 : 1; }
    
    private void sendMessage(String message) {
        if (isUsingLan) {
            lan.sendLine(message);
        } else {
            bt.sendLine(message);
        }
    }
    
    private void showGameEndDialog(String message) {
        // Save game record
        saveGameRecord(message);
        
        new AlertDialog.Builder(this)
                .setTitle("游戏结束")
                .setMessage(message)
                .setPositiveButton("重新开始", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        resetGame();
                    }
                })
                .setNegativeButton("返回菜单", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        finish();
                    }
                })
                .setCancelable(false)
                .show();
    }
    
    private void saveGameRecord(String resultMessage) {
        if (gameStartTime == 0) return; // Game never properly started
        
        long duration = System.currentTimeMillis() - gameStartTime;
        String gameTypeName = "";
        switch (currentGameType) {
            case GOMOKU: gameTypeName = "五子棋"; break;
            case GO: gameTypeName = "围棋"; break;
            case XIANGQI: gameTypeName = "象棋"; break;
        }
        
        String gameMode = playingAgainstAI ? "AI" : (isUsingLan ? "局域网" : "蓝牙");
        String result = "平";
        if (resultMessage.contains("赢了") || resultMessage.contains("获胜")) {
            result = "胜";
        } else if (resultMessage.contains("输了")) {
            result = "负";
        }
        
        GameRecord record = new GameRecord(
            gameTypeName,
            gameMode,
            opponentName,
            result,
            playerMoveCount,
            opponentMoveCount,
            duration
        );
        
        historyManager.addRecord(record);
    }
    
    private void resetGame() {
        gameStarted = false;
        gameEnded = false;
        playingAgainstAI = false;
        myColor = 1;
        turn = 1;
        
        // Reset the game board
        switch (currentGameType) {
            case GOMOKU:
                gomokuEngine = new GomokuEngine(15);
                boardView.bindGomokuEngine(gomokuEngine);
                break;
            case GO:
                goEngine = new GoEngine(19);
                boardView.bindGoEngine(goEngine);
                break;
            case XIANGQI:
                xiangqiEngine = new XiangqiEngine();
                boardView.bindXiangqiEngine(xiangqiEngine);
                break;
        }
        
        Toast.makeText(this, "棋盘已重置，请开始新游戏", Toast.LENGTH_SHORT).show();
    }

    private void sendChallenge() {
        try {
            JSONObject o = new JSONObject(); 
            o.put("type", "challenge"); 
            o.put("from", "Player");
            o.put("gameType", gameTypeStr);
            sendMessage(o.toString());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void sendUndoRequest() {
        try {
            JSONObject o = new JSONObject(); 
            o.put("type", "undo_request");
            sendMessage(o.toString());
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "发送悔棋请求失败", Toast.LENGTH_SHORT).show();
        }
    }

    @Override public void onDeviceFound(BluetoothDevice device) {
        String key = device.getName() + " (" + device.getAddress() + ")";
        if (!deviceMap.containsKey(key)) {
            deviceMap.put(key, device);
            deviceAdapter.add(key);
            deviceAdapter.notifyDataSetChanged();
        }
    }

    @Override public void onConnected(BluetoothDevice device) {
        opponentName = device.getName(); // Track opponent name
        gameStartTime = System.currentTimeMillis(); // Track game start for multiplayer
        playerMoveCount = 0;
        opponentMoveCount = 0;
        Toast.makeText(this, "已连接 " + device.getName(), Toast.LENGTH_SHORT).show();
        sendChallenge();
    }

    @Override public void onDisconnected(String reason) {
        Toast.makeText(this, "断开: " + reason, Toast.LENGTH_SHORT).show();
    }

    @Override public void onMessage(String line) {
        try {
            JSONObject o = new JSONObject(line);
            String type = o.getString("type");
            switch (type) {
                case "challenge":
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            new AlertDialog.Builder(GameActivity.this)
                                    .setTitle("对战请求")
                                    .setMessage("是否接受对战？")
                                    .setPositiveButton("接受", new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface d, int w) {
                                            try {
                                                JSONObject ok = new JSONObject(); 
                                                ok.put("type", "accept");
                                                sendMessage(ok.toString());
                                                myColor = 2;
                                                turn = 1;
                                                gameStarted = true;
                                                gameEnded = false;
                                                playingAgainstAI = false;
                                            } catch (Exception e) {
                                                e.printStackTrace();
                                            }
                                        }
                                    })
                                    .setNegativeButton("拒绝", new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface d, int w) {
                                            try { 
                                                JSONObject r = new JSONObject(); 
                                                r.put("type", "reject"); 
                                                sendMessage(r.toString()); 
                                            } catch (Exception e) {
                                                e.printStackTrace();
                                            }
                                        }
                                    }).show();
                        }
                    });
                    break;
                case "accept":
                    Toast.makeText(this, "对方接受，开始游戏", Toast.LENGTH_SHORT).show();
                    myColor = 1; 
                    turn = 1;
                    gameStarted = true;
                    gameEnded = false;
                    playingAgainstAI = false;
                    break;
                case "reject":
                    Toast.makeText(this, "对方拒绝", Toast.LENGTH_SHORT).show();
                    break;
                case "move":
                    int r = o.getInt("r"), c = o.getInt("c"), color = o.getInt("color");
                    int fromR = o.optInt("fromR", -1);
                    int fromC = o.optInt("fromC", -1);
                    final int finalR = r;
                    final int finalC = c;
                    final int finalColor = color;
                    final int finalFromR = fromR;
                    final int finalFromC = fromC;
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            onRemoteMove(finalR, finalC, finalColor, finalFromR, finalFromC);
                        }
                    });
                    break;
                case "undo_request":
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            new AlertDialog.Builder(GameActivity.this)
                                    .setTitle("悔棋请求")
                                    .setMessage("同意撤销上一步吗？")
                                    .setPositiveButton("同意", new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface d, int w) {
                                            if (currentGameType == BoardView.GameType.GOMOKU) {
                                                gomokuEngine.undo(1);
                                            } else if (currentGameType == BoardView.GameType.GO) {
                                                goEngine.undo(1);
                                            } else if (currentGameType == BoardView.GameType.XIANGQI) {
                                                xiangqiEngine.undo(1);
                                            }
                                            boardView.invalidate();
                                            try { 
                                                JSONObject ok = new JSONObject(); 
                                                ok.put("type","undo_ok"); 
                                                sendMessage(ok.toString()); 
                                            } catch (Exception e) {
                                                e.printStackTrace();
                                            }
                                            turn = other(turn);
                                        }
                                    })
                                    .setNegativeButton("拒绝", null)
                                    .show();
                        }
                    });
                    break;
                case "undo_ok":
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (currentGameType == BoardView.GameType.GOMOKU) {
                                gomokuEngine.undo(1);
                            } else if (currentGameType == BoardView.GameType.GO) {
                                goEngine.undo(1);
                            } else if (currentGameType == BoardView.GameType.XIANGQI) {
                                xiangqiEngine.undo(1);
                            }
                            boardView.invalidate();
                            turn = other(turn);
                        }
                    });
                    break;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override public void onError(Throwable t) {
        Toast.makeText(this, "错误: " + t.getMessage(), Toast.LENGTH_SHORT).show();
    }
    
    /* ========== LAN Helper Methods ========== */
    
    private void startLanDiscovery() {
        deviceAdapter.clear();
        deviceMap.clear();
        lanServiceMap.clear();
        isUsingLan = true;
        lan.startDiscovery();
        Toast.makeText(this, "正在搜索局域网设备...", Toast.LENGTH_SHORT).show();
    }
    
    private void startLanServer() {
        lan.startServer();
        isUsingLan = true;
        Toast.makeText(this, "等待局域网连接...", Toast.LENGTH_SHORT).show();
    }
    
    /* ========== LanHelper.Listener Implementation ========== */
    
    @Override public void onServiceFound(String serviceName, String hostAddress) {
        String key = serviceName + " (" + hostAddress + ")";
        if (!lanServiceMap.containsKey(key)) {
            lanServiceMap.put(key, serviceName);
            deviceAdapter.add(key);
            deviceAdapter.notifyDataSetChanged();
        }
    }
    
    @Override public void onConnected(String hostName) {
        isUsingLan = true;
        opponentName = hostName; // Track LAN opponent name
        gameStartTime = System.currentTimeMillis(); // Track game start for LAN
        playerMoveCount = 0;
        opponentMoveCount = 0;
        Toast.makeText(this, "已连接 " + hostName, Toast.LENGTH_SHORT).show();
        if (isUsingLan) {
            sendChallengeLan();
        }
    }
    
    private void sendChallengeLan() {
        try {
            JSONObject o = new JSONObject();
            o.put("type", "challenge");
            lan.sendLine(o.toString());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        bt.close();
        lan.close();
    }
}
