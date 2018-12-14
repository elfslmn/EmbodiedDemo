package com.esalman17.embodieddemo;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.util.Log;
import android.widget.ImageView;

import java.util.ArrayList;

/**
 * Created by esalman17 on 8.10.2018.
 */

public class GameBothReal extends Game{
    private static final String LOG_TAG = "GameBothReal";
    private ArrayList<Point> wantedPoints;
    private ArrayList<Point> otherPoints;
    private boolean[] soundPlayedPoints;
    Drawable drawable, finger, cross;
    Rect middle, gesture_middle;

    public GameBothReal(Context context, int level) {
        this.level = level;
        if(level == 0){
            finger = context.getResources().getDrawable(R.drawable.finger);
        }
        drawable = context.getResources().getDrawable(R.drawable.red);
        cross = context.getResources().getDrawable(R.drawable.cross);

        initialize(level);
    }

    @Override
    public void initialize(int level){
        state = GameState.OBJECT_PLACEMENT;

        wantedPoints = new ArrayList<>(10);
        otherPoints = new ArrayList<>(10);

        switch (level){
            case 0:
                //left
                wantedPoints.add(new Point(280, 450));
                wantedPoints.add(new Point(400, 400));
                wantedPoints.add(new Point(500, 500));

                //right
                wantedPoints.add(new Point(800, 450));
                wantedPoints.add(new Point(950, 450));

                left = new Rect(200, 250, 580, 600);
                right = new Rect(700, 250, 1080, 600);
                correctSide = Side.LEFT;
                break;
            case 1:
                // left
                wantedPoints.add(new Point(190, 500));
                wantedPoints.add(new Point(300, 495));
                wantedPoints.add(new Point(410, 490));
                wantedPoints.add(new Point(510, 500));

                // right
                otherPoints.add(new Point(810, 500));
                otherPoints.add(new Point(870, 400));
                otherPoints.add(new Point(980, 390));
                otherPoints.add(new Point(1020, 500));

                correctSide = Side.MIDDLE; // TODO değiştir eşit, wizard of oz
                question = Question.MORE;

                left = new Rect(130, 320, 570, 600);
                right = new Rect(750, 320, 1080, 600);
                middle = new Rect(left.right, left.top, right.left, left.bottom );
                break;
        }

        soundPlayedPoints = new boolean[wantedPoints.size() + otherPoints.size()];
        initializeCanvas();

        gesture_left = new Rect(left);
        gesture_left.top = 0;
        gesture_right = new Rect(right);
        gesture_right.top = 0;
        gesture_middle = new Rect(middle);
        gesture_middle.top = 0;

        Log.i(LOG_TAG, "New game object (level=" + level + ") is initialized");
    }

    @Override
    Canvas initializeCanvas() {
        Canvas canvas = new Canvas(MainActivity.bmpOverlay);
        canvas.drawPaint(GamePaint.eraser);

        for(Point p : wantedPoints){
            cross.setBounds(p.x-50, p.y-50, p.x+50, p.y+50);
            cross.draw(canvas);
        }
        return canvas;
    }

    @Override
    public boolean processBlobDescriptors(int[] descriptors){
        Canvas canvas = initializeCanvas();

        // start processing
        int count = 0;
        for (int i = 0; i <= descriptors.length - 3; i += 3)
        {
            if (descriptors[i + 2] == 1) continue; // it is edge-connected blob

            Point p1 = new Point(descriptors[i], descriptors[i + 1]);
            boolean match = false;
            for (int j=0; j<wantedPoints.size(); j++)
            {
                Point p2 = wantedPoints.get(j);
                if (areClose(p1, p2, 50))
                {
                    canvas.drawCircle(p2.x, p2.y, 51, GamePaint.eraser);

                    drawable.setBounds(p1.x-50, p1.y-50, p1.x+50, p1.y+50);
                    drawable.draw(canvas);

                    match = true;
                    if( !soundPlayedPoints[j]){
                        soundPlayedPoints[j] = true;
                        MainActivity.soundPool.play(MainActivity.sOkay,1f,1f,1,0,1f);
                    }
                    break;
                }
            }

            if(match) count++;
            else{
                canvas.drawCircle(p1.x, p1.y, 10, GamePaint.blue);
            }
        }
        if(count == wantedPoints.size())
        {
            if(state == GameState.OBJECT_PLACEMENT){
                state = GameState.LEFT_PLACED;
                for(Point p : otherPoints){
                    cross.setBounds(p.x-50, p.y-50, p.x+50, p.y+50);
                    cross.draw(canvas);
                }
                wantedPoints.addAll(otherPoints);
            }
            else{
                state = GameState.ALL_PLACED;
            }
            return true;
        }
        else{
            return false;
        }
    }

    @Override
    public int processGestureDescriptors(int[] descriptors){
        for (int i = 0; i <= descriptors.length - 3; i += 3)
        {
            if (descriptors[i + 2] == 1 ){
                if(gesture_left.contains(descriptors[i],descriptors[i + 1])){
                    if(correctSide == Side.LEFT){
                        state = GameState.ASSESMENT_FINISHED; // level finished
                        assestmentTime = (System.currentTimeMillis() -startTime);
                        Log.i(LOG_TAG, "Gesture: Correct side is chosen");
                        return 1;
                    }
                    else{
                        Log.i(LOG_TAG, "Gesture: Wrong side is chosen");
                        return -1;
                    }
                }
                else if(gesture_right.contains(descriptors[i],descriptors[i + 1])){
                    if(correctSide == Side.RIGHT){
                        state = GameState.ASSESMENT_FINISHED; // level finished
                        assestmentTime = (System.currentTimeMillis() -startTime);
                        Log.i(LOG_TAG, "Gesture: Correct side is chosen");
                        return 1;
                    }
                    else{
                        Log.i(LOG_TAG, "Gesture: Wrong side is chosen");
                        return -1;
                    }
                }
                else if(gesture_middle.contains(descriptors[i],descriptors[i + 1])){
                    if(correctSide == Side.MIDDLE){
                        state = GameState.ASSESMENT_FINISHED; // level finished
                        assestmentTime = (System.currentTimeMillis() -startTime);
                        Log.i(LOG_TAG, "Gesture: Correct side is chosen");
                        return 1;
                    }
                    else{
                        Log.i(LOG_TAG, "Gesture: Wrong side is chosen");
                        return -1;
                    }
                }

            }
        }
        return 0;
    }

}
