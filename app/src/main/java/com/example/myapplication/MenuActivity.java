package com.example.myapplication;

import android.content.Intent;
import android.os.Bundle;
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

        cardGomoku.setOnClickListener(v -> {
            Intent intent = new Intent(MenuActivity.this, GameActivity.class);
            intent.putExtra("GAME_TYPE", "GOMOKU");
            startActivity(intent);
        });

        cardGo.setOnClickListener(v -> {
            Intent intent = new Intent(MenuActivity.this, GameActivity.class);
            intent.putExtra("GAME_TYPE", "GO");
            startActivity(intent);
        });

        cardXiangqi.setOnClickListener(v -> {
            Intent intent = new Intent(MenuActivity.this, GameActivity.class);
            intent.putExtra("GAME_TYPE", "XIANGQI");
            startActivity(intent);
        });
    }
}
