package com.yko.dragview;

import android.app.Service;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.Vibrator;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;

import java.util.LinkedList;
import java.util.List;

public class DragViewContainer extends ViewGroup {
    private List<DragView> dragViewList = new LinkedList<>();
    private int spanCount = 0;
    private int verticalPadding = 0;
    private int horizontalPadding = 0;
    private int itemWidth = 0;
    private int itemHeight = 0;
    private float lastX, lastY;

    // 拖动的控件
    private DragView dragView = null;
    private int dragViewIndex = -1;

    // 插入控件的位置
    private int insertIndex = -1;

    // 控件当前状态
    private final int STATE_IDLE = 0;
    private final int STATE_READY = 1;
    private final int STATE_DRAGING = 2;
    private final int STATE_RELEASE = 3;
    private int state = STATE_IDLE;

    // 其他view开发移动时间
    private long startMovingTime = 0;
    // 拖动的view释放的时间
    private long startReleaseTime = 0;
    private final long DURATION = 200;

    // 移动到新的位置，在新旧位置上的控件都要移动
    private final int FUNC_MOVE = 1;
    private boolean moving = false;
    // 长按控件，可以拖动
    private final int FUNC_DRAG = 2;
    // 放开手指，dragview回到目标位置
    private final int FUNC_RELEASE = 3;
    // 删除view
    private final int FUNC_REMOVE = 4;

    // 需要重新布局
    private boolean dirty = true;
    private Handler H = new Handler(Looper.getMainLooper()){
        @Override
        public void handleMessage(@NonNull Message msg) {
            switch (msg.what){
                case FUNC_MOVE:
                    moving();
                    break;
                case FUNC_DRAG:
                    readyToDrag();
                    break;
                case FUNC_RELEASE:
                    releaseDrag();
                    break;
                case FUNC_REMOVE:
                    removing();
                    break;
            }
        }
    };

    Vibrator vibrator;
    public DragViewContainer(Context context) {
        super(context);
        init(context, null);
    }

    public DragViewContainer(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }

    private void init(Context context, AttributeSet attrs){
        vibrator = (Vibrator)context.getApplicationContext().getSystemService(Service.VIBRATOR_SERVICE);

        TypedArray a = getContext().obtainStyledAttributes(
                attrs, R.styleable.DragContainer, 0, 0);
        spanCount = a.getInteger(R.styleable.DragContainer_DragContainerCount, 5);
        verticalPadding = (int)a.getDimension(R.styleable.DragContainer_VerticalPadding, 0f);
        horizontalPadding = (int)a.getDimension(R.styleable.DragContainer_HorizontalPadding, 0f);
        a.recycle();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        itemWidth = (width - horizontalPadding) / spanCount;

        // 为得到每个view的高和宽都一样，取child中最高的准
        View child;
        int h, x = 0, y = 0;
        int hMax = 0;
        int wSpec, hSpec;
        DragView dv, dvPre;
        // 先找出最大高度
        for(int i = 0; i < dragViewList.size(); i++){
            dv = dragViewList.get(i);
            if(dv.oriX != -1){
                h = dv.view.getMeasuredHeight();
                if(hMax < h){
                    hMax = h;
                }
                continue;
            }
            child = dv.view;
            LayoutParams lp = child.getLayoutParams();
            wSpec = MeasureSpec.makeMeasureSpec(itemWidth - horizontalPadding, MeasureSpec.EXACTLY);
            hSpec = lp.height > 0 ? MeasureSpec.makeMeasureSpec(lp.height, MeasureSpec.EXACTLY) : lp.height;
            child.measure(wSpec, hSpec);
            h = child.getMeasuredHeight();
            if(hMax < h){
                hMax = h;
            }
        }

        // 第二次测量，把child的宽 高统一
        for(int i = 0; i < dragViewList.size(); i++){
            dv = dragViewList.get(i);
            child = dv.view;
            if(child.getHeight() != hMax) {
                child.measure(MeasureSpec.makeMeasureSpec(itemWidth - horizontalPadding, MeasureSpec.EXACTLY),
                        MeasureSpec.makeMeasureSpec(hMax, MeasureSpec.EXACTLY));
            }
        }
        itemHeight =  hMax + verticalPadding;
        // view中心坐标
        x = (itemWidth + horizontalPadding) / 2;
        y = (itemHeight + verticalPadding) / 2;
        if(dragViewList.size() > 0 && dragViewList.get(0).oriX == -1){
            dv = dragViewList.get(0);
            dv.oriX = x;
            dv.oriY = y;
            dv.newX = -1;
            dv.newY = -1;
            dv.x = -1;
            dv.y = -1;
        }
        for(int i = 1; i < dragViewList.size(); i++){
            if(dragViewList.get(i).oriX != -1){
                continue;
            }
            dv = dragViewList.get(i);
            dvPre = dragViewList.get(i - 1);
            x = dvPre.oriX;
            y = dvPre.oriY;
            // 换行
            if(x + itemWidth > itemWidth * spanCount){
                x = (itemWidth + horizontalPadding) / 2;
                y += itemHeight;
            } else {
                x += itemWidth;
            }
            dv.oriX = x;
            dv.oriY = y;
            dv.newX = -1;
            dv.newY = -1;
            dv.x = -1;
            dv.y = -1;
        }
        // 计算container的高度
        int height = (dragViewList.size() + spanCount - 1) / spanCount * itemHeight + verticalPadding;
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        View child;
        int lx, ly;
        for(int i = 0; i < dragViewList.size(); i++) {
            DragView dv = dragViewList.get(i);
            child = dv.view;
            lx = dv.x == -1 ? dv.oriX : dv.x;
            ly = dv.x == -1 ? dv.oriY : dv.y;
            Log.d("测试", "onLayout==============================");
            Log.d("测试", "onLayout " + i + ":" +
                    (lx - child.getMeasuredWidth() / 2) + "," +
                    (ly - child.getMeasuredHeight() / 2) + "," +
                    (lx + child.getMeasuredWidth() / 2) + "," +
                    (ly + child.getMeasuredHeight() / 2));
            child.layout(lx - child.getMeasuredWidth() / 2,
                    ly - child.getMeasuredHeight() / 2,
                    lx + child.getMeasuredWidth() / 2,
                    ly + child.getMeasuredHeight() / 2);
        }
    }

    @Override
    public void addView(View child, int index, LayoutParams params) {
        super.addView(child, -1, params);
        DragView dv = new DragView(child);
        dragViewList.add(dv);
    }

    @Override
    public void removeViewAt(int index) {
        if(index >= 0 && index < dragViewList.size()){
            removeView(dragViewList.get(index).view);
        }
    }

    @Override
    public void removeView(View view) {
        // 非空闲，移动状态下，不能删除
        if(state != STATE_IDLE || startMovingTime != 0 || startReleaseTime != 0){
            return;
        }
        super.removeView(view);
        DragView dv, dvNext;
        int removeIndex = 0;
        for(; removeIndex < dragViewList.size(); removeIndex++){
            dv = dragViewList.get(removeIndex);
            if(view == dv.view){
                break;
            }
        }

        if(removeIndex < dragViewList.size() - 1){
            for(int i = dragViewList.size() - 1; i > removeIndex; i--){
                dv = dragViewList.get(i - 1);
                dvNext = dragViewList.get(i);
                dvNext.newX = dv.oriX;
                dvNext.newY = dv.oriY;
                dvNext.rect = dv.rect;
            }
            insertIndex = dragViewList.size() - 1;
            dragViewIndex = removeIndex;
            H.sendEmptyMessage(FUNC_REMOVE);
        }
        dragViewList.remove(removeIndex);
    }

    private void removing(){
        if(startMovingTime == 0){
            startMovingTime = System.currentTimeMillis();
            H.sendEmptyMessage(FUNC_REMOVE);
        } else {
            long now = System.currentTimeMillis();
            DragView dv;
            if(now - startMovingTime >= DURATION){
                startMovingTime = 0;
                for (int i = dragViewIndex; i < insertIndex; i++) {
                    dv = dragViewList.get(i);
                    dv.oriX = dv.newX;
                    dv.oriY = dv.newY;
                    dv.x = -1;
                    dv.y = -1;
                }
            } else {
                double percent = (now - (double) startMovingTime) / DURATION;
                for (int i = dragViewIndex; i < insertIndex; i++) {
                    dv = dragViewList.get(i);
                    dv.x = dv.oriX + (int) ((dv.newX - dv.oriX) * percent);
                    dv.y = dv.oriY + (int) ((dv.newY - dv.oriY) * percent);
                }
                H.sendEmptyMessage(FUNC_REMOVE);
            }
        }
        requestLayout();
    }

    private int getDragViewIndex(float x, float y){
        for(int i = 0; i < dragViewList.size(); i++){
            DragView item = dragViewList.get(i);
            if(item.rect == null) {
                item.rect = new Rect();
                item.view.getHitRect(item.rect);
            }
            if(item.rect.contains((int)x, (int)y)){
                return i;
            }
        }
        return -1;
    }

    private int getInsertIndex(){
        for(int i = 0; i < dragViewList.size(); i++){
            DragView item = dragViewList.get(i);
            if(item != dragView && item.rect.contains(dragView.x, dragView.y)){
                return i;
            }
        }
        return -1;
    }

    private void drag(float x, float y){
        dragView.update(x, y);
        // 当moving==true时，dragview可移动，但其他view按原移动的路径移动，移动结束后恢复draging状态
        // 当draging状态时，dragview可移动，同时计算其他view是否要move
        if(!moving) {
            int ii = getInsertIndex();
            if (ii != -1 && insertIndex == -1) {
                // 手指移动到新的View的位置，500ms后开发移动View
                H.sendEmptyMessageDelayed(FUNC_MOVE, 500);
            } else if (ii == -1 && insertIndex != -1) {
                // 500ms内，手指移出目标view，停止自动move view
                if (H.hasMessages(FUNC_MOVE)) {
                    H.removeMessages(FUNC_MOVE);
                }
            } else if (ii != -1 && ii == insertIndex) {
                // 500ms内，手指地目标view内，什么都不用做
            } else if (ii != -1 && ii != insertIndex) {
                // 500ms内，手指移到另外的view里，移除原来的message，插入新的message
                if (H.hasMessages(FUNC_MOVE)) {
                    H.removeMessages(FUNC_MOVE);
                }
                H.sendEmptyMessageDelayed(FUNC_MOVE, 500);
            }
            insertIndex = ii;
        }
        requestLayout();
    }

    private void moving(){
        if(insertIndex != -1){
            DragView vl, vlReplace;
            if(startMovingTime == 0){
                // startMovingTime == 0其他view开始移动
                // 如果在处理move消息前，手指松开了，进入STATE_RELEASE状态，其他view就不再移动
                // drawview也回到原来位置
                if(state != STATE_DRAGING){
                    return;
                }
                startMovingTime = System.currentTimeMillis();
                moving = true;
                int oriX = dragViewList.get(insertIndex).oriX;
                int oriY = dragViewList.get(insertIndex).oriY;
                Rect rect = dragViewList.get(insertIndex).rect;
                // 其他view向后移动1位
                if(insertIndex < dragViewIndex){
                    for(int i = insertIndex; i < dragViewIndex; i++){
                        vl = dragViewList.get(i);
                        vlReplace = dragViewList.get(i + 1);
                        vl.newX = vlReplace.oriX;
                        vl.newY = vlReplace.oriY;
                        vl.rect = vlReplace.rect;
                    }
                } else if(insertIndex > dragViewIndex){
                    // 其他view向前移动1位
                    for(int i = insertIndex; i > dragViewIndex; i--){
                        vl = dragViewList.get(i);
                        vlReplace = dragViewList.get(i - 1);
                        vl.newX = vlReplace.oriX;
                        vl.newY = vlReplace.oriY;
                        vl.rect = vlReplace.rect;
                    }
                }
                dragView.newX = oriX;
                dragView.newY = oriY;
                dragView.rect = rect;
                H.sendEmptyMessage(FUNC_MOVE);
            } else {
                long now = System.currentTimeMillis();
                // 超过移动时间，也就是移动完成
                if(now - startMovingTime >= DURATION){
                    if (insertIndex < dragViewIndex) {
                        for (int i = insertIndex; i < dragViewIndex; i++) {
                            vl = dragViewList.get(i);
                            vl.oriX = vl.newX;
                            vl.oriY = vl.newY;
                            vl.newX = -1;
                            vl.newY = -1;
                            vl.x = -1;
                            vl.y = -1;
                        }
                    } else if (insertIndex > dragViewIndex) {
                        for (int i = insertIndex; i > dragViewIndex; i--) {
                            vl = dragViewList.get(i);
                            vl.oriX = vl.newX;
                            vl.oriY = vl.newY;
                            vl.newX = -1;
                            vl.newY = -1;
                            vl.x = -1;
                            vl.y = -1;
                        }
                    }
                    moving = false;
                    startMovingTime = 0;
                    dragView.oriX = dragView.newX;
                    dragView.oriY = dragView.newY;
                    dragView.newX = -1;
                    dragView.newY = -1;
                    dragViewList.remove(dragViewIndex);
                    dragViewList.add(insertIndex, dragView);
                    dragViewIndex = insertIndex;
                } else {
                    // 移动过程中
                    double percent = (now - (double) startMovingTime) / DURATION;
                    if (insertIndex < dragViewIndex) {
                        for (int i = insertIndex; i < dragViewIndex; i++) {
                            vl = dragViewList.get(i);
                            vl.x = vl.oriX + (int)((vl.newX - vl.oriX) * percent);
                            vl.y = vl.oriY + (int)((vl.newY - vl.oriY) * percent);
                        }
                    } else if (insertIndex > dragViewIndex) {
                        for (int i = insertIndex; i > dragViewIndex; i--) {
                            vl = dragViewList.get(i);
                            vl.x = vl.oriX + (int)((vl.newX - vl.oriX) * percent);
                            vl.y = vl.oriY + (int)((vl.newY - vl.oriY) * percent);
                        }
                    }
                    H.sendEmptyMessage(FUNC_MOVE);
                }
            }
            requestLayout();
        }
    }

    private void readyToDrag(){
        dragView.view.bringToFront();
        state = STATE_DRAGING;
        vibrator.vibrate(50);
        for(int i = 0; i < dragViewList.size(); i++){
            DragView item = dragViewList.get(i);
            item.rect = new Rect();
            item.view.getHitRect(item.rect);
        }
        Toast.makeText(getContext(), "正在拖动", Toast.LENGTH_SHORT).show();
    }

    private void releaseDrag(){
        if(startReleaseTime == 0){
            state = STATE_RELEASE;
            startReleaseTime = System.currentTimeMillis();
            if(dragView.newX == -1){
                dragView.newX = dragView.oriX;
                dragView.newY = dragView.oriY;
            }
            dragView.oriX = dragView.x;
            dragView.oriY = dragView.y;
            H.sendEmptyMessage(FUNC_RELEASE);
        } else {
            long now = System.currentTimeMillis();
            if(now - startReleaseTime >= DURATION){
                dragView.oriX = dragView.newX;
                dragView.oriY = dragView.newY;
                dragView.newX = -1;
                dragView.newY = -1;
                dragView.x = -1;
                dragView.y = -1;
                dragView = null;
                dragViewIndex = -1;
                state = STATE_IDLE;
                startReleaseTime = 0;
            } else {
                double percent = (now - startReleaseTime) / (double)DURATION;
                dragView.x = (int)(dragView.oriX + percent * (dragView.newX - dragView.oriX));
                dragView.y = (int)(dragView.oriY + percent * (dragView.newY - dragView.oriY));
                H.sendEmptyMessage(FUNC_RELEASE);
            }
        }
        requestLayout();
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        switch(ev.getAction()){
            case MotionEvent.ACTION_DOWN:
                // 非空闲，移动状态下，不响应触摸
                if(state != STATE_IDLE || startMovingTime != 0 || startReleaseTime != 0){
                    break;
                }
                lastX = ev.getX();
                lastY = ev.getY();
                dragViewIndex = getDragViewIndex(lastX, lastY);
                if(dragViewIndex == -1){
                    break;
                }
                dragView = dragViewList.get(dragViewIndex);
                H.sendEmptyMessageDelayed(FUNC_DRAG, 1000);
                state = STATE_READY;
                break;
            case MotionEvent.ACTION_MOVE:
                if(state == STATE_READY){
                    float x = ev.getX();
                    float y = ev.getY();
                    if(Math.pow(x - lastX, 2) + Math.pow(y - lastY, 2) > 100){
                        H.removeMessages(FUNC_DRAG);
                        state = STATE_IDLE;
                    }
                } else if(state == STATE_DRAGING){
                    return true;
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if(H.hasMessages(FUNC_DRAG)){
                    H.removeMessages(FUNC_DRAG);
                }
                if(state == STATE_DRAGING){
                    state = STATE_IDLE;
                    return true;
                }
                state = STATE_IDLE;
                break;
            default:
                break;
        }
        return false;
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        switch (ev.getAction()){
            case MotionEvent.ACTION_MOVE:
                if(state == STATE_READY){
                    float x = ev.getX();
                    float y = ev.getY();
                    if(Math.pow(x - lastX, 2) + Math.pow(y - lastY, 2) > 100){
                        H.removeMessages(FUNC_DRAG);
                        state = STATE_IDLE;
                    }
                } else if(state == STATE_DRAGING) {
                    drag(ev.getX(), ev.getY());
                }
                break;
            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP:
                if(state == STATE_DRAGING) {
                    drag(ev.getX(), ev.getY());
                    H.sendEmptyMessage(FUNC_RELEASE);
                }
                break;
        }
        return true;
    }
}