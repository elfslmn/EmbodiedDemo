package com.esalman17.embodieddemo;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.util.Log;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * Created by esalman17 on 17.12.2018.
 */

public class GameStacking extends Game {
    private static final String LOG_TAG = "GameStacking";
    private HashMap<Point, Integer> stackPoints;
    int wantedStackHeight = 0;

    private ArrayList<Rect> longStonePoints;
    private boolean[] soundPlayedPoints;
    Drawable drawable, cross;

    public GameStacking(Context context, int level){
        this.level = level;
        drawable = context.getResources().getDrawable(R.drawable.red);
        cross = context.getResources().getDrawable(R.drawable.cross);

        initialize(level);
    }

    @Override
    void initialize(int level) {
        state = GameState.OBJECT_PLACEMENT;

        stackPoints = new HashMap<>(2);
        longStonePoints = new ArrayList<>(5);

        switch (level){
            case 3:
                // left
                stackPoints.put(new Point(300, 450), -5);
                wantedStackHeight = 3;

                // right // TODO adjust for long stones
                longStonePoints.add(new Rect(680, 480, 800, 560));
                longStonePoints.add(new Rect(810, 480, 930, 560));
                longStonePoints.add(new Rect(940, 480, 1060, 560));
                longStonePoints.add(new Rect(1070, 480, 1190, 560));

                correctSide = Side.RIGHT;
                question = Question.LESS;

                left = new Rect(130, 320, 570, 600);
                right = new Rect(750, 320, 1080, 600);

        }

        soundPlayedPoints = new boolean[longStonePoints.size()];
        initializeCanvas();

        gesture_left = new Rect(left);
        gesture_left.top = 0;
        gesture_right = new Rect(right);
        gesture_right.top = 0;

        Log.i(LOG_TAG, "New game object (level=" + level + ") is initialized");
    }

    @Override
    Canvas initializeCanvas() {
        Canvas canvas = new Canvas(MainActivity.bmpOverlay);
        canvas.drawPaint(GamePaint.eraser);

        for(Point p : stackPoints.keySet()){
            cross.setBounds(p.x-50, p.y-50, p.x+50, p.y+50);
            cross.draw(canvas);
        }
        if(state == GameState.LEFT_PLACED){
            for(Rect r : longStonePoints){
                canvas.drawRect(r, GamePaint.red);
            }
        }
        return canvas;
    }

    @Override
    boolean processBlobDescriptors(int[] descriptors) {
        Canvas canvas = initializeCanvas();

        // start processing
        int longCount = 0;
        for (int i = 0; i <= descriptors.length - 3; i += 3)
        {
            if (descriptors[i + 2] == -1) continue; // -1 is an edge-connected(gesture) blob

            Point p1 = new Point(descriptors[i], descriptors[i + 1]);
            boolean match = false;

            if(descriptors[i + 2] >= 0) // Circle stones
            {
                int height = descriptors[i + 2];
                Log.d(LOG_TAG, "retro height : "+height);

                for (Point p2 : stackPoints.keySet()) {
                    if (areClose(p1, p2, 70)) {
                        canvas.drawCircle(p2.x, p2.y, 51, GamePaint.eraser);
                        if(height > 11*wantedStackHeight){
                            drawable.setBounds(p1.x - 50, p1.y - 50, p1.x + 60, p1.y + 60);
                            drawable.draw(canvas);
                        }
                        else{
                            cross.setBounds(p1.x - 45, p1.y - 45, p1.x + 55, p1.y + 55);
                            cross.draw(canvas);
                        }
                        match = true;
                        if (height - stackPoints.get(p2) > 12) {
                            MainActivity.soundPool.play(MainActivity.sOkay, 1f, 1f, 1, 0, 1f);
                        }
                        stackPoints.put(p2, height);
                        break;
                    }
                }
            }
            else{ // Long Stones
                int angle = descriptors[i + 2];
                for(Rect r : longStonePoints){
                    if(r.contains(p1.x,p1.y) && angle > -20){
                        canvas.drawRect(r, GamePaint.green);
                        match = true;
                        longCount++;
                        break;
                    }
                }
            }
            if(!match)
            {
                canvas.drawCircle(p1.x, p1.y, 10, GamePaint.blue);
            }
        }
        if(state == GameState.OBJECT_PLACEMENT){
            int count = 0;
            for (Point p2: stackPoints.keySet())
            {
                if(stackPoints.get(p2)> 11*wantedStackHeight) count++;
            }
            if(count == stackPoints.size())
            {
                state = GameState.LEFT_PLACED;
                Log.d(LOG_TAG, "Stacking done: state = LEFT_PLACED");
                return true;
            }
        }
        else if(state == GameState.LEFT_PLACED){
            if(longCount == longStonePoints.size()){
                state = GameState.ALL_PLACED;
                return true;
            }
        }
        return false;
    }

    @Override
    int processGestureDescriptors(int[] descriptors) {
        return 0;
    }
}
