package com.example.myapplication;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class GameRecord {
    private String gameType;        // 游戏类型：五子棋/围棋/象棋
    private String gameMode;        // 游戏模式：AI/蓝牙/局域网
    private String opponentId;      // 对手ID（AI难度或设备名称）
    private String result;          // 结果：胜/负/平
    private long timestamp;         // 时间戳
    private int playerMoves;        // 玩家移动次数
    private int opponentMoves;      // 对手移动次数
    private long gameDuration;      // 游戏时长（毫秒）

    public GameRecord(String gameType, String gameMode, String opponentId, String result, 
                      int playerMoves, int opponentMoves, long gameDuration) {
        this.gameType = gameType;
        this.gameMode = gameMode;
        this.opponentId = opponentId;
        this.result = result;
        this.timestamp = System.currentTimeMillis();
        this.playerMoves = playerMoves;
        this.opponentMoves = opponentMoves;
        this.gameDuration = gameDuration;
    }

    // Constructor for loading from storage
    public GameRecord(String gameType, String gameMode, String opponentId, String result,
                      long timestamp, int playerMoves, int opponentMoves, long gameDuration) {
        this.gameType = gameType;
        this.gameMode = gameMode;
        this.opponentId = opponentId;
        this.result = result;
        this.timestamp = timestamp;
        this.playerMoves = playerMoves;
        this.opponentMoves = opponentMoves;
        this.gameDuration = gameDuration;
    }

    public String getGameType() {
        return gameType;
    }

    public String getGameMode() {
        return gameMode;
    }

    public String getOpponentId() {
        return opponentId;
    }

    public String getResult() {
        return result;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public int getPlayerMoves() {
        return playerMoves;
    }

    public int getOpponentMoves() {
        return opponentMoves;
    }

    public long getGameDuration() {
        return gameDuration;
    }

    public String getFormattedDate() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
        return sdf.format(new Date(timestamp));
    }

    public String getFormattedDuration() {
        long seconds = gameDuration / 1000;
        long minutes = seconds / 60;
        long secs = seconds % 60;
        return String.format(Locale.getDefault(), "%d分%d秒", minutes, secs);
    }

    // Serialize to string for storage
    public String serialize() {
        return gameType + "|" + gameMode + "|" + opponentId + "|" + result + "|" +
               timestamp + "|" + playerMoves + "|" + opponentMoves + "|" + gameDuration;
    }

    // Deserialize from string
    public static GameRecord deserialize(String data) {
        try {
            String[] parts = data.split("\\|");
            if (parts.length >= 8) {
                return new GameRecord(
                    parts[0],  // gameType
                    parts[1],  // gameMode
                    parts[2],  // opponentId
                    parts[3],  // result
                    Long.parseLong(parts[4]),  // timestamp
                    Integer.parseInt(parts[5]), // playerMoves
                    Integer.parseInt(parts[6]), // opponentMoves
                    Long.parseLong(parts[7])    // gameDuration
                );
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
}
