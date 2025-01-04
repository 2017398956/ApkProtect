package personal.nfl.protect.demo

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class SingleInstanceActivity : AppCompatActivity() {

    private lateinit var tvNumber: TextView
    private lateinit var btnAdd: Button
    private var number = 0
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("SingleInstanceActivity", "onCreate")
        enableEdgeToEdge()
        setContentView(R.layout.activity_single_instance)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        tvNumber = findViewById(R.id.tv_number)
        btnAdd = findViewById(R.id.btn_add)
        btnAdd.setOnClickListener {
            number++
            tvNumber.text = "$number"
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        Log.d("SingleInstanceActivity", "onNewIntent")
    }
}