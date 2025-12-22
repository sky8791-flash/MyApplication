package com.example.myapplication;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.card.MaterialCardView;

public class MenuActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_menu);

        MaterialCardView cardGomoku = findViewById(R.id.cardGomoku);
        MaterialCardView cardGo = findViewById(R.id.cardGo);
        MaterialCardView cardXiangqi = findViewById(R.id.cardXiangqi);
        Button btnHistory = findViewById(R.id.btnHistory);

        cardGomoku.setOnClickListener(new android.view.View.OnClickListener() {
            @Override
            public void onClick(android.view.View v) {
                Intent intent = new Intent(MenuActivity.this, GameActivity.class);
                intent.putExtra("GAME_TYPE", "GOMOKU");
                startActivity(intent);
            }
        });

        cardGo.setOnClickListener(new android.view.View.OnClickListener() {
            @Override
            public void onClick(android.view.View v) {
                Intent intent = new Intent(MenuActivity.this, GameActivity.class);
                intent.putExtra("GAME_TYPE", "GO");
                startActivity(intent);
            }
        });

        cardXiangqi.setOnClickListener(new android.view.View.OnClickListener() {
            @Override
            public void onClick(android.view.View v) {
                Intent intent = new Intent(MenuActivity.this, GameActivity.class);
                intent.putExtra("GAME_TYPE", "XIANGQI");
                startActivity(intent);
            }
        });
        
        btnHistory.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MenuActivity.this, HistoryActivity.class);
                startActivity(intent);
            }
        });
    }
}
