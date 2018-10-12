package com.esalman17.embodieddemo;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.util.Log;
import android.widget.ImageView;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * Created by esalman17 on 8.10.2018.
 */

public class GameHalfVirtual {
    private static String LOG_TAG = "Game";
    public ArrayList<Point> wantedPoints;
    public ArrayList<Point> virtualPoints;
    public Rect left, right;
    public Rect gesture_left, gesture_right;
    public long startTime, assestmentTime; // both in msec
    public GameState state;
    public int level;
    private Side correctSide;
    private boolean[] soundPlayedPoints;
    Drawable orange;

    public GameHalfVirtual(Context context, int level) {
        initialize(level);
        orange = context.getResources().getDrawable(R.drawable.orange);
    }

    public void initialize(int level){
        this.level = level;
        state =  GameState.OBJECT_PLACEMENT;

        wantedPoints = new ArrayList<>(10);
        virtualPoints = new ArrayList<>(10);

        switch (level){
            case 1:
                wantedPoints.add(new Point(280, 450));
                wantedPoints.add(new Point(400, 400));
                wantedPoints.add(new Point(500, 500));

                virtualPoints.add(new Point(770, 400));
                virtualPoints.add(new Point(1000, 400));
                virtualPoints.add(new Point(1000, 530));
                virtualPoints.add(new Point(770, 530));

                left = new Rect(200, 250, 580, 600);
                right = new Rect(700, 250, 1080, 600);
                correctSide = Side.RIGHT;
                soundPlayedPoints = new boolean[wantedPoints.size()];
                break;

            case 2:
                wantedPoints.add(new Point(280, 450));
                wantedPoints.add(new Point(400, 400));
                wantedPoints.add(new Point(500, 500));

                virtualPoints.add(new Point(850, 400));
                virtualPoints.add(new Point(950, 400));
                virtualPoints.add(new Point(950, 500));
                virtualPoints.add(new Point(850, 500));

                left = new Rect(200, 250, 580, 600);
                right = new Rect(700, 250, 1080, 600);
                correctSide = Side.RIGHT;
                soundPlayedPoints = new boolean[wantedPoints.size()];
                Arrays.fill(soundPlayedPoints, Boolean.TRUE);
                break;
        }

        // initialize canvas
        Canvas canvas = new Canvas(MainActivity.bmpOverlay);
        canvas.drawPaint(GamePaint.eraser);
        for(Point p : wantedPoints){
            canvas.drawCircle(p.x, p.y, 50, GamePaint.red);
        }

        gesture_left = new Rect(left);
        gesture_left.top = 0;
        gesture_right = new Rect(right);
        gesture_right.top = 0;

        Log.i(LOG_TAG, "New game object (level=" + level + ") is initialized");
    }

    public boolean processBlobDescriptors(int[] descriptors){
        // initialize canvas
        Canvas canvas = new Canvas(MainActivity.bmpOverlay);
        canvas.drawPaint(GamePaint.eraser);
        for(Point p : wantedPoints){
            canvas.drawCircle(p.x, p.y, 50, GamePaint.red);
        }

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
                    canvas.drawCircle(p2.x, p2.y, 55, GamePaint.eraser);
                    //canvas.drawCircle(p1.x, p1.y, 50, GamePaint.green);

                    orange.setBounds(p1.x-45, p1.y-45, p1.x+45, p1.y+45);
                    orange.draw(canvas);

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
                canvas.drawCircle(p1.x, p1.y, 50, GamePaint.blue);
            }
        }
        if(count == wantedPoints.size())
        {
            state = GameState.ALL_PLACED;
            Log.i(LOG_TAG, "Blobs: All objects are placed.");
            for(Point p1: virtualPoints){
                orange.setBounds(p1.x-45, p1.y-45, p1.x+45, p1.y+45);
                orange.draw(canvas);
            }
            MainActivity.soundPool.play(MainActivity.sOkay,1f,1f,1,0,1f);

            canvas.drawRect(left, GamePaint.red);
            canvas.drawRect(right, GamePaint.red);
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    state = GameState.ASSESMENT_RUNNING;
                    startTime = System.currentTimeMillis();
                    Log.i(LOG_TAG, "Assessment has started");
                }
            }).start();

            Log.i(LOG_TAG, "Assessment has started");

            return true;
        }
        else{
            return false;
        }
    }

    public int processGestureDescriptors(int[] descriptors){
        for (int i = 0; i <= descriptors.length - 3; i += 3)
        {
            if (descriptors[i + 2] == 1){
                if(getCorrectSide().contains(descriptors[i],descriptors[i + 1])){
                    state = GameState.ASSESMENT_FINISHED; // level finshed
                    assestmentTime = (System.currentTimeMillis() -startTime);
                    Log.i(LOG_TAG, "Gesture: Correct side is chosen");
                    return 1;
                }
                else if(getWrongSide().contains(descriptors[i],descriptors[i + 1])){
                    Log.i(LOG_TAG, "Gesture: Wrong side is chosen");
                    return -1;
                }
            }
        }
        return 0;
    }

    public void setBackground(ImageView view, int drawableId){
        view.setImageResource(drawableId);
    }

    private boolean areClose(Point p1, Point p2, int maxDist){
        return Math.pow(p1.x - p2.x, 2) + Math.pow(p1.y - p2.y, 2) <= maxDist*maxDist ;
    }

    private Rect getCorrectSide(){
        if(correctSide == Side.LEFT) return gesture_left;
        else if(correctSide == Side.RIGHT) return gesture_right;
        else return null;
    }

    private Rect getWrongSide(){
        if(correctSide == Side.LEFT) return gesture_right;
        else if(correctSide == Side.RIGHT) return gesture_left;
        else return null;
    }


}
