package personal.nfl.protect.demo;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

public class SecondActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_second);

        Button button = findViewById(R.id.btn_button);
        button.setOnClickListener(v -> {
            startActivity(new Intent(this, SingleInstanceActivity.class));
            // Toast.makeText(SecondActivity.this, "点击了按钮", Toast.LENGTH_SHORT).show();
        });
    }
}