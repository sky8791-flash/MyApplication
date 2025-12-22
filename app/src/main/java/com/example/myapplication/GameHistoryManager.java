package com.example.myapplication;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class GameHistoryManager {
    private static final String PREFS_NAME = "GameHistory";
    private static final String KEY_RECORDS = "records";
    private static final int MAX_RECORDS = 100; // 最多保存100条记录

    private Context context;
    private SharedPreferences prefs;

    public GameHistoryManager(Context context) {
        this.context = context;
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    // 添加游戏记录
    public void addRecord(GameRecord record) {
        List<GameRecord> records = getAllRecords();
        records.add(0, record); // 添加到列表开头（最新的在前面）

        // 限制记录数量
        if (records.size() > MAX_RECORDS) {
            records = records.subList(0, MAX_RECORDS);
        }

        saveRecords(records);
    }

    // 获取所有记录
    public List<GameRecord> getAllRecords() {
        Set<String> recordStrings = prefs.getStringSet(KEY_RECORDS, new HashSet<String>());
        List<GameRecord> records = new ArrayList<>();

        for (String recordString : recordStrings) {
            GameRecord record = GameRecord.deserialize(recordString);
            if (record != null) {
                records.add(record);
            }
        }

        // 按时间排序（最新的在前面）
        Collections.sort(records, new Comparator<GameRecord>() {
            @Override
            public int compare(GameRecord r1, GameRecord r2) {
                return Long.compare(r2.getTimestamp(), r1.getTimestamp());
            }
        });

        return records;
    }

    // 按游戏类型筛选
    public List<GameRecord> getRecordsByGameType(String gameType) {
        List<GameRecord> allRecords = getAllRecords();
        List<GameRecord> filtered = new ArrayList<>();

        for (GameRecord record : allRecords) {
            if (record.getGameType().equals(gameType)) {
                filtered.add(record);
            }
        }

        return filtered;
    }

    // 获取统计数据
    public GameStats getStats() {
        List<GameRecord> records = getAllRecords();
        int totalGames = records.size();
        int wins = 0;
        int losses = 0;
        int draws = 0;

        for (GameRecord record : records) {
            if (record.getResult().equals("胜")) {
                wins++;
            } else if (record.getResult().equals("负")) {
                losses++;
            } else if (record.getResult().equals("平")) {
                draws++;
            }
        }

        return new GameStats(totalGames, wins, losses, draws);
    }

    // 获取特定游戏类型的统计
    public GameStats getStatsByGameType(String gameType) {
        List<GameRecord> records = getRecordsByGameType(gameType);
        int totalGames = records.size();
        int wins = 0;
        int losses = 0;
        int draws = 0;

        for (GameRecord record : records) {
            if (record.getResult().equals("胜")) {
                wins++;
            } else if (record.getResult().equals("负")) {
                losses++;
            } else if (record.getResult().equals("平")) {
                draws++;
            }
        }

        return new GameStats(totalGames, wins, losses, draws);
    }

    // 清空所有记录
    public void clearAllRecords() {
        prefs.edit().remove(KEY_RECORDS).apply();
    }

    // 保存记录到SharedPreferences
    private void saveRecords(List<GameRecord> records) {
        Set<String> recordStrings = new HashSet<>();
        for (GameRecord record : records) {
            recordStrings.add(record.serialize());
        }
        prefs.edit().putStringSet(KEY_RECORDS, recordStrings).apply();
    }

    // 统计数据类
    public static class GameStats {
        public int totalGames;
        public int wins;
        public int losses;
        public int draws;

        public GameStats(int totalGames, int wins, int losses, int draws) {
            this.totalGames = totalGames;
            this.wins = wins;
            this.losses = losses;
            this.draws = draws;
        }

        public double getWinRate() {
            if (totalGames == 0) return 0;
            return (double) wins / totalGames * 100;
        }
    }
}
