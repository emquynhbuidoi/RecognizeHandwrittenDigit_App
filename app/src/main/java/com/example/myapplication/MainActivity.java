package com.example.myapplication;

import androidx.appcompat.app.AppCompatActivity;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.divyanshu.draw.widget.DrawView;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";

    private DrawView drawView;
    private Button clearButton;
    private TextView predictedTextView;
    private DigitClassifier digitClassifier = new DigitClassifier(MainActivity.this);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        predictedTextView = findViewById(R.id.predicted_text);

        drawView = findViewById(R.id.draw_view);
        drawView.setStrokeWidth(70.0f); // Độ dày nét vẽ
        drawView.setColor(Color.WHITE);
        drawView.setBackgroundColor(Color.BLACK);
        drawView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                drawView.onTouchEvent(event); // xử lý sự kiện khi vẽ

                // nếu người dùng kết thúc sự kiện chạm -> thực hiện phân loại
                if(event.getAction() == MotionEvent.ACTION_UP){
                    classifyDrawing();
                }
                return true;
            }
        });

        clearButton = findViewById(R.id.clear_button);
        clearButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                drawView.clearCanvas();
                predictedTextView.setText(getString(R.string.prediction_text_placeholder));
            }
        });

        //set-up digit classifier
        digitClassifier.initialize();
    }

    @Override
    protected void onDestroy() {
        digitClassifier.close();
        super.onDestroy();
    }

    private void classifyDrawing() {
        Bitmap bitmap = drawView.getBitmap();
        if(bitmap != null && digitClassifier.isInitialized){
            digitClassifier.classifyAsync(bitmap)
                    .handle((result, exception)->{
                     if(exception != null){
                         Throwable cause = exception.getCause();
                         if(cause != null){
                             Log.e(TAG, "classifyDrawing: Lỗi rồi!!!", cause);
                         }
                     }
                     else{
                         if(result != null){
                             // Xử lý thành công
//                             Log.d(TAG, "classifyDrawing: " + result);
                             predictedTextView.setText(result);
                         }
                     }
                     return null;
                    });
        }
    }
}