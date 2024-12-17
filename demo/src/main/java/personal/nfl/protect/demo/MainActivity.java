package personal.nfl.protect.demo;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

public class MainActivity extends AppCompatActivity {

    public MainActivity() {
        Log.d("MainActivity", "constructor");
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        assert (getApplication() instanceof DemoApplication);
        int[] appCompatTheme = R.styleable.AppCompatTheme;
        int appCompatThemeWindowActionBar = R.styleable.AppCompatTheme_windowActionBar;
        setContentView(R.layout.activity_main);
        findViewById(R.id.btn_jump).setOnClickListener(v -> {
            try (
                    InputStream fileInputStream = v.getContext().getAssets().open("test.txt");
                    InputStreamReader inputStreamReader = new InputStreamReader(fileInputStream);
                    BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
            ) {
                Log.d("test_assets4", "read line:" + bufferedReader.readLine());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            Intent intent = new Intent(this, SecondActivity.class);
            startActivity(intent);
        });

        try (
                InputStream fileInputStream = getAssets().open("test.txt");
                InputStreamReader inputStreamReader = new InputStreamReader(fileInputStream);
                BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
        ) {
            Log.d("test_assets3", "read line:" + bufferedReader.readLine());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}