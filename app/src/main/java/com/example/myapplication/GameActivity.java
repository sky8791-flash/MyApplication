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

public class GameActivity extends AppCompatActivity implements BluetoothHelper.Listener {

    private BluetoothHelper bt;
    private ArrayAdapter<String> deviceAdapter;
    private Map<String, BluetoothDevice> deviceMap = new HashMap<>();
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

    private final String[] perms = new String[]{
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.ACCESS_FINE_LOCATION
    };
    private final ActivityResultContracts.RequestMultiplePermissions permsContract = new ActivityResultContracts.RequestMultiplePermissions();
    private final androidx.activity.result.ActivityResultLauncher<String[]> permLauncher =
            registerForActivityResult(permsContract, r -> startDiscovery());

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_game);
        
        bt = new BluetoothHelper(this, this);

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

        boardView.setOnPlaceListener((r, c) -> tryPlace(r, c));
        boardView.setOnXiangqiMoveListener((fromR, fromC, toR, toC) -> tryXiangqiMove(fromR, fromC, toR, toC));

        ListView list = findViewById(R.id.listDevices);
        deviceAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1);
        list.setAdapter(deviceAdapter);
        list.setOnItemClickListener((p, v, pos, id) -> {
            String key = deviceAdapter.getItem(pos);
            BluetoothDevice d = deviceMap.get(key);
            if (d != null) {
                bt.connectTo(d);
            }
        });

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnDiscover).setOnClickListener(v -> {
            checkPermAndDiscover();
            cardDevices.setVisibility(View.VISIBLE);
        });
        findViewById(R.id.btnHost).setOnClickListener(v -> bt.startServerAccept());
        findViewById(R.id.btnAI).setOnClickListener(v -> showAIDifficultyDialog());
        findViewById(R.id.btnUndo).setOnClickListener(v -> {
            if (playingAgainstAI) {
                performUndo();
            } else {
                sendUndoRequest();
            }
        });
    }
    
    private void showAIDifficultyDialog() {
        String[] difficulties = {"简单", "中等", "困难"};
        new AlertDialog.Builder(this)
                .setTitle("选择AI难度")
                .setItems(difficulties, (dialog, which) -> {
                    SimpleAI.Difficulty difficulty;
                    switch (which) {
                        case 0: difficulty = SimpleAI.Difficulty.EASY; break;
                        case 1: difficulty = SimpleAI.Difficulty.MEDIUM; break;
                        case 2: difficulty = SimpleAI.Difficulty.HARD; break;
                        default: difficulty = SimpleAI.Difficulty.MEDIUM;
                    }
                    startAIGame(difficulty);
                })
                .setNegativeButton("取消", null)
                .show();
    }
    
    private void startAIGame(SimpleAI.Difficulty difficulty) {
        ai = new SimpleAI(difficulty);
        playingAgainstAI = true;
        myColor = 1;
        turn = 1;
        
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
        aiHandler.postDelayed(() -> {
            int aiColor = other(myColor);
            
            if (currentGameType == BoardView.GameType.XIANGQI) {
                int[] move = ai.findBestXiangqiMove(xiangqiEngine, aiColor);
                if (move != null && move.length == 4) {
                    if (xiangqiEngine.place(move[0], move[1], move[2], move[3], aiColor)) {
                        boardView.invalidate();
                        if (xiangqiEngine.checkWin(move[2], move[3])) {
                            Toast.makeText(this, "AI获胜！", Toast.LENGTH_LONG).show();
                        } else {
                            turn = myColor;
                        }
                    }
                }
            } else {
                int[][] board = (currentGameType == BoardView.GameType.GOMOKU) ? 
                    gomokuEngine.getBoard() : goEngine.getBoard();
                int size = board.length;
                
                int[] move = ai.findBestMoveForBoard(board, aiColor, size);
                if (move != null) {
                    boolean success = false;
                    if (currentGameType == BoardView.GameType.GOMOKU) {
                        success = gomokuEngine.place(move[0], move[1], aiColor);
                        if (success && gomokuEngine.checkWin(move[0], move[1])) {
                            Toast.makeText(this, "AI获胜！", Toast.LENGTH_LONG).show();
                        }
                    } else if (currentGameType == BoardView.GameType.GO) {
                        success = goEngine.place(move[0], move[1], aiColor);
                        if (success && goEngine.checkWin(move[0], move[1])) {
                            Toast.makeText(this, "AI获胜！", Toast.LENGTH_LONG).show();
                        }
                    }
                    
                    if (success) {
                        boardView.invalidate();
                        turn = myColor;
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
        if (turn != myColor) { Toast.makeText(this, "等待对手", Toast.LENGTH_SHORT).show(); return; }
        
        boolean success = false;
        if (currentGameType == BoardView.GameType.GOMOKU) {
            success = gomokuEngine.place(r, c, myColor);
        } else if (currentGameType == BoardView.GameType.GO) {
            success = goEngine.place(r, c, myColor);
        }
        
        if (!success) return;
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
            Toast.makeText(this, "你赢了!", Toast.LENGTH_LONG).show();
        } else {
            turn = other(myColor);
            if (playingAgainstAI) {
                makeAIMove();
            }
        }
    }

    private void tryXiangqiMove(int fromR, int fromC, int toR, int toC) {
        if (turn != myColor) { Toast.makeText(this, "等待对手", Toast.LENGTH_SHORT).show(); return; }
        if (!xiangqiEngine.place(fromR, fromC, toR, toC, myColor)) return;
        
        boardView.invalidate();
        
        if (!playingAgainstAI) {
            sendMove(toR, toC, myColor, fromR, fromC);
        }
        
        if (xiangqiEngine.checkWin(toR, toC)) {
            Toast.makeText(this, "你赢了!", Toast.LENGTH_LONG).show();
        } else {
            turn = other(myColor);
            if (playingAgainstAI) {
                makeAIMove();
            }
        }
    }

    private void onRemoteMove(int r, int c, int color, int fromR, int fromC) {
        if (currentGameType == BoardView.GameType.XIANGQI) {
            xiangqiEngine.place(fromR, fromC, r, c, color);
            if (xiangqiEngine.checkWin(r, c)) {
                Toast.makeText(this, "你输了", Toast.LENGTH_LONG).show();
            }
        } else if (currentGameType == BoardView.GameType.GOMOKU) {
            gomokuEngine.place(r, c, color);
            if (gomokuEngine.checkWin(r, c)) {
                Toast.makeText(this, "你输了", Toast.LENGTH_LONG).show();
            }
        } else if (currentGameType == BoardView.GameType.GO) {
            goEngine.place(r, c, color);
            if (goEngine.checkWin(r, c)) {
                Toast.makeText(this, "你输了", Toast.LENGTH_LONG).show();
            }
        }
        boardView.invalidate();
        turn = myColor;
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
            bt.sendLine(o.toString());
        } catch (Exception e) { 
            e.printStackTrace();
            Toast.makeText(this, "发送移动失败", Toast.LENGTH_SHORT).show();
        }
    }

    private int other(int c) { return c == 1 ? 2 : 1; }

    private void sendChallenge() {
        try {
            JSONObject o = new JSONObject(); 
            o.put("type", "challenge"); 
            o.put("from", "Player");
            o.put("gameType", gameTypeStr);
            bt.sendLine(o.toString());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void sendUndoRequest() {
        try {
            JSONObject o = new JSONObject(); 
            o.put("type", "undo_request");
            bt.sendLine(o.toString());
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
                    runOnUiThread(() -> new AlertDialog.Builder(this)
                            .setTitle("对战请求")
                            .setMessage("是否接受对战？")
                            .setPositiveButton("接受", (d, w) -> {
                                try {
                                    JSONObject ok = new JSONObject(); 
                                    ok.put("type", "accept");
                                    bt.sendLine(ok.toString());
                                    myColor = 2;
                                    turn = 1;
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            })
                            .setNegativeButton("拒绝", (d, w) -> {
                                try { 
                                    JSONObject r = new JSONObject(); 
                                    r.put("type", "reject"); 
                                    bt.sendLine(r.toString()); 
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            }).show());
                    break;
                case "accept":
                    Toast.makeText(this, "对方接受，开始游戏", Toast.LENGTH_SHORT).show();
                    myColor = 1; turn = 1;
                    break;
                case "reject":
                    Toast.makeText(this, "对方拒绝", Toast.LENGTH_SHORT).show();
                    break;
                case "move":
                    int r = o.getInt("r"), c = o.getInt("c"), color = o.getInt("color");
                    int fromR = o.optInt("fromR", -1);
                    int fromC = o.optInt("fromC", -1);
                    runOnUiThread(() -> onRemoteMove(r, c, color, fromR, fromC));
                    break;
                case "undo_request":
                    runOnUiThread(() -> new AlertDialog.Builder(this)
                            .setTitle("悔棋请求")
                            .setMessage("同意撤销上一步吗？")
                            .setPositiveButton("同意", (d,w)-> {
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
                                    bt.sendLine(ok.toString()); 
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                                turn = other(turn);
                            })
                            .setNegativeButton("拒绝", null)
                            .show());
                    break;
                case "undo_ok":
                    runOnUiThread(() -> {
                        if (currentGameType == BoardView.GameType.GOMOKU) {
                            gomokuEngine.undo(1);
                        } else if (currentGameType == BoardView.GameType.GO) {
                            goEngine.undo(1);
                        } else if (currentGameType == BoardView.GameType.XIANGQI) {
                            xiangqiEngine.undo(1);
                        }
                        boardView.invalidate();
                        turn = other(turn);
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

    @Override protected void onDestroy() {
        super.onDestroy();
        bt.close();
    }
}
