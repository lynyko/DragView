package com.yko.dragview;

import android.graphics.Rect;
import android.view.View;

public class DragView {
    // 静止时的坐标
    public int oriX = -1, oriY = -1;
    // 平移时下个位置的坐标
    public int newX = -1, newY = -1;
    // 当前位置坐标
    public int x = -1, y = -1;
    // view所占的空间
    public Rect rect = null;
    public View view = null;

    public DragView(View view){
        this.view = view;
    }

    public void update(float x, float y){
        this.x = (int)x;
        this.y = (int)y;
    }
}
