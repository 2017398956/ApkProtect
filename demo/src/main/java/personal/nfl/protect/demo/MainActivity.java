package personal.nfl.protect.demo;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        int[] appCompatTheme = R.styleable.AppCompatTheme;
        int appCompatThemeWindowActionBar = R.styleable.AppCompatTheme_windowActionBar;
        setContentView(R.layout.activity_main);
        findViewById(R.id.btn_jump).setOnClickListener(v -> {
            Intent intent = new Intent(this, SecondActivity.class);
            startActivity(intent);
        });


    }
}