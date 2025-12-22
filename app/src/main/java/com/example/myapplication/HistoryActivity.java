package com.example.myapplication;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.util.List;
import java.util.Locale;

public class HistoryActivity extends AppCompatActivity {
    private ListView historyListView;
    private TextView statsText;
    private Button btnAll, btnGomoku, btnGo, btnXiangqi, btnClear;
    private GameHistoryManager historyManager;
    private String currentFilter = "全部";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);

        historyManager = new GameHistoryManager(this);

        historyListView = findViewById(R.id.historyListView);
        statsText = findViewById(R.id.statsText);
        btnAll = findViewById(R.id.btnAll);
        btnGomoku = findViewById(R.id.btnGomoku);
        btnGo = findViewById(R.id.btnGo);
        btnXiangqi = findViewById(R.id.btnXiangqi);
        btnClear = findViewById(R.id.btnClear);

        setupFilterButtons();
        loadHistory();
    }

    private void setupFilterButtons() {
        btnAll.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                currentFilter = "全部";
                loadHistory();
            }
        });

        btnGomoku.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                currentFilter = "五子棋";
                loadHistory();
            }
        });

        btnGo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                currentFilter = "围棋";
                loadHistory();
            }
        });

        btnXiangqi.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                currentFilter = "象棋";
                loadHistory();
            }
        });

        btnClear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showClearConfirmDialog();
            }
        });
    }

    private void loadHistory() {
        List<GameRecord> records;
        GameHistoryManager.GameStats stats;

        if (currentFilter.equals("全部")) {
            records = historyManager.getAllRecords();
            stats = historyManager.getStats();
        } else {
            records = historyManager.getRecordsByGameType(currentFilter);
            stats = historyManager.getStatsByGameType(currentFilter);
        }

        // 更新统计信息
        String statsInfo = String.format(Locale.getDefault(),
                "📊 统计信息 (%s)\n\n" +
                "总局数：%d 局\n" +
                "胜利：%d 局 (%.1f%%)\n" +
                "失败：%d 局\n" +
                "平局：%d 局",
                currentFilter,
                stats.totalGames,
                stats.wins,
                stats.getWinRate(),
                stats.losses,
                stats.draws);
        statsText.setText(statsInfo);

        // 更新列表
        HistoryAdapter adapter = new HistoryAdapter(records);
        historyListView.setAdapter(adapter);
    }

    private void showClearConfirmDialog() {
        new AlertDialog.Builder(this)
                .setTitle("清空历史记录")
                .setMessage("确定要清空所有历史记录吗？此操作不可恢复。")
                .setPositiveButton("确定", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface dialog, int which) {
                        historyManager.clearAllRecords();
                        loadHistory();
                        Toast.makeText(HistoryActivity.this, "历史记录已清空", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private class HistoryAdapter extends ArrayAdapter<GameRecord> {
        private List<GameRecord> records;

        public HistoryAdapter(List<GameRecord> records) {
            super(HistoryActivity.this, 0, records);
            this.records = records;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(getContext())
                        .inflate(R.layout.item_history, parent, false);
            }

            GameRecord record = records.get(position);

            TextView gameTypeText = convertView.findViewById(R.id.gameTypeText);
            TextView resultText = convertView.findViewById(R.id.resultText);
            TextView detailsText = convertView.findViewById(R.id.detailsText);

            // 游戏类型和模式
            String gameInfo = getGameEmoji(record.getGameType()) + " " + 
                             record.getGameType() + " · " + record.getGameMode();
            gameTypeText.setText(gameInfo);

            // 结果
            String resultEmoji;
            int resultColor;
            if (record.getResult().equals("胜")) {
                resultEmoji = "🏆";
                resultColor = 0xFF4CAF50; // Green
            } else if (record.getResult().equals("负")) {
                resultEmoji = "😢";
                resultColor = 0xFFF44336; // Red
            } else {
                resultEmoji = "🤝";
                resultColor = 0xFFFF9800; // Orange
            }
            resultText.setText(resultEmoji + " " + record.getResult());
            resultText.setTextColor(resultColor);

            // 详细信息
            String details = String.format(Locale.getDefault(),
                    "🕐 %s\n" +
                    "👤 对手：%s\n" +
                    "⏱️ 用时：%s\n" +
                    "📝 步数：玩家 %d · 对手 %d",
                    record.getFormattedDate(),
                    record.getOpponentId(),
                    record.getFormattedDuration(),
                    record.getPlayerMoves(),
                    record.getOpponentMoves());
            detailsText.setText(details);

            return convertView;
        }

        private String getGameEmoji(String gameType) {
            switch (gameType) {
                case "五子棋":
                    return "💕";
                case "围棋":
                    return "🌟";
                case "象棋":
                    return "✨";
                default:
                    return "🎮";
            }
        }
    }
}
