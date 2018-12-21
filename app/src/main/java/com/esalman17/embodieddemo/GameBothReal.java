package com.esalman17.embodieddemo;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.util.Log;

import java.util.ArrayList;

/**
 * Created by esalman17 on 8.10.2018.
 */

public class GameBothReal extends Game{
    private static final String LOG_TAG = "GameBothReal";
    private ArrayList<Point> allPoints;
    private ArrayList<Point> rightPoints;
    private boolean[] soundPlayedPoints;
    Drawable drawable, finger, cross;

    public GameBothReal(Context context, int level) {
        this.level = level;
        if(level == 0){
            finger = context.getResources().getDrawable(R.drawable.finger);
        }
        if(level == 1) drawable = context.getResources().getDrawable(R.drawable.red);
        else if(level == 2) drawable = context.getResources().getDrawable(R.drawable.purple);
        cross = context.getResources().getDrawable(R.drawable.cross);

        initialize(level);
    }

    @Override
    public void initialize(int level){
        state = GameState.OBJECT_PLACEMENT;

        allPoints = new ArrayList<>(10);
        rightPoints = new ArrayList<>(10);

        switch (level){
            case 1:
                allPoints.add(new Point(230, 560));
                allPoints.add(new Point(330, 560));
                allPoints.add(new Point(230, 660));
                allPoints.add(new Point(330, 660));

                rightPoints.add(new Point(680, 560));
                rightPoints.add(new Point(780, 560));
                rightPoints.add(new Point(680, 660));
                rightPoints.add(new Point(780, 660));

                correctSide = Side.MIDDLE;
                question = Question.MORE; // TODO or equal

                left = new Rect(130, 350, 400, 715);
                right = new Rect(600, 350, 1050, 715);
                middle = new Rect(left.right, left.top, right.left, left.bottom );

                break;
            case 2:
                allPoints.add(new Point(190, 500));
                allPoints.add(new Point(300, 495));
                allPoints.add(new Point(410, 490));
                allPoints.add(new Point(510, 500));

                rightPoints.add(new Point(810, 500));
                rightPoints.add(new Point(870, 400));
                rightPoints.add(new Point(930, 300));
                rightPoints.add(new Point(990, 200));

                correctSide = Side.MIDDLE;
                question = Question.MORE; // TODO or equal

                left = new Rect(130, 320, 570, 600);
                right = new Rect(750, 320, 1080, 600);
                middle = new Rect(left.right, left.top, right.left, left.bottom );
                break;
        }

        soundPlayedPoints = new boolean[allPoints.size() + rightPoints.size()];
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

        for(Point p : allPoints){
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
            if (descriptors[i + 2] < 0) continue; // -1: gesture blob, -2,-90: long stones

            Point p1 = new Point(descriptors[i], descriptors[i + 1]);
            boolean match = false;
            for (int j = 0; j< allPoints.size(); j++)
            {
                Point p2 = allPoints.get(j);
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
        if(count == allPoints.size())
        {
            if(state == GameState.OBJECT_PLACEMENT){
                state = GameState.LEFT_PLACED;
                allPoints.addAll(rightPoints);
            }
            else if(state == GameState.LEFT_PLACED){
                state = GameState.RIGHT_PLACED;
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
            if (descriptors[i + 2] == -1 ){ //  -1 is an edge-connected(gesture) blob
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

    public void changePoints(){
        int n = allPoints.size();
        switch (level){
            case 1:
                allPoints.set(n-1, new Point(880, 560) );
                allPoints.set(n-2, new Point(980, 560) );
                soundPlayedPoints[n-1] = false;
                soundPlayedPoints[n-2] = false;
                break;
            case 2:
                allPoints.set(n-1, new Point(1020, 500) );
                allPoints.set(n-2, new Point(980, 390) );
                soundPlayedPoints[n-1] = false;
                soundPlayedPoints[n-2] = false;
                break;
        }
    }

}
