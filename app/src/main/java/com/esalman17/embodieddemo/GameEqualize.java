package com.esalman17.embodieddemo;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Point;
import android.graphics.Rect;
import android.util.Log;

import java.util.ArrayList;

/**
 * Created by esalman17 on 22.10.2018.
 */

public class GameEqualize extends Game {
    private static final String LOG_TAG = "GameEqualize";
    private static final int CORRECT_THRESHOLD = 5;
    private int correctCounter;
    private ArrayList<Point> virtualPoints;

    public GameEqualize(Context context, int level) {
        this.level = level;
        initialize(level);
        drawable = context.getResources().getDrawable(R.drawable.red);
    }

    @Override
    void initialize(int level) {
        virtualPoints = new ArrayList<>();
        switch (level){
            case 1:
                left = new Rect(200, 250, 580, 600);
                right = new Rect(700, 250, 1080, 600);
                break;
        }

        initializeCanvas();

        gesture_left = new Rect(left);
        gesture_left.top = 0;
        gesture_right = new Rect(right);
        gesture_right.top = 0;

        correctCounter = 0;
        state = GameState.ASSESMENT_RUNNING; // TODO It assumes there is already objects in the field

        Log.i(LOG_TAG, "New game object (level=" + level + ") is initialized");
    }

    @Override
    Canvas initializeCanvas() {
        Canvas canvas = new Canvas(MainActivity.bmpOverlay);
        canvas.drawPaint(GamePaint.eraser);
        for(Point p1: virtualPoints){
            drawable.setBounds(p1.x-45, p1.y-45, p1.x+45, p1.y+45);
            drawable.draw(canvas);
        }
        canvas.drawRect(left, GamePaint.red);
        canvas.drawRect(right, GamePaint.red);

        return canvas;
    }

    @Override
    public boolean processBlobDescriptors(int[] descriptors){
        Canvas canvas = initializeCanvas();

        // start processing
        int countLeft = 0, countRight = 0;
        for (int i = 0; i <= descriptors.length - 3; i += 3)
        {
            if (descriptors[i + 2] == -1) continue; // -1 is an edge-connected(gesture) blob

            Point p1 = new Point(descriptors[i], descriptors[i + 1]);
            if(left.contains(p1.x, p1.y)){
                drawable.setBounds(p1.x-45, p1.y-45, p1.x+45, p1.y+45);
                drawable.draw(canvas);
                countLeft++;
            }
            else if(right.contains(p1.x, p1.y)){
                drawable.setBounds(p1.x-45, p1.y-45, p1.x+45, p1.y+45);
                drawable.draw(canvas);
                countRight++;
            }

        }
        if(countLeft == countRight && countLeft !=0){
            correctCounter++;
        }
        else{
            correctCounter = 0;
        }
        //Log.d(LOG_TAG, "left ="+countLeft+" right="+countRight+" correct counter="+correctCounter);

        if(correctCounter >= CORRECT_THRESHOLD){
            state = GameState.ASSESMENT_FINISHED; // level finished
            assestmentTime = (System.currentTimeMillis() -startTime);
            Log.i(LOG_TAG, "ASSESMENT_FINISHED: Left and right has equal number of objects.");
            return true;
        }

        return false;
    }

    @Override
    int processGestureDescriptors(int[] descriptors) {
        return 0;
    }
}
