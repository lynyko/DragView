package com.demo.myapplication;

import androidx.appcompat.app.AppCompatActivity;

import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.TextView;

import com.yko.dragview.DragViewContainer;

public class MainActivity extends AppCompatActivity {
    String[] colors = new String[]{"#FF5252","#FF9800","#3F51B5","#7C4DFF","#009688","#795548"};
    DragViewContainer container;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        container = findViewById(R.id.container);
    }

    public void onClick(View v){
        Log.d("测试", "onclick:" + ((TextView)v).getText().toString());
        container.removeView(v);
    }

    int text = 8;
    public void onAdd(View v){
        TextView tv = new TextView(this);
        tv.setGravity(Gravity.CENTER);
        text++;
        tv.setText(text + "");
        tv.setBackgroundColor(Color.parseColor(colors[text % 6]));
        tv.setTextColor(Color.WHITE);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        tv.setOnClickListener(onClickListener);
        container.addView(tv);
    }

    View.OnClickListener onClickListener = v -> {
        onClick(v);
    };
}