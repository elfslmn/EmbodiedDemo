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
import java.util.List;

/**
 * Created by esalman17 on 8.10.2018.
 */

public class GameHalfVirtual extends Game{
    private static String LOG_TAG = "GameHalfVirtual";
    public ArrayList<Point> wantedPoints;
    public ArrayList<Point> virtualPoints;
    private boolean[] soundPlayedPoints;
    Drawable drawable, finger, cross;

    public GameHalfVirtual(Context context, int level) {
        this.level = level;
        if(level == 0){
            finger = context.getResources().getDrawable(R.drawable.finger);
        }
        cross = context.getResources().getDrawable(R.drawable.cross);
        initialize(level);

        switch (level) {
            case 0:
                drawable = context.getResources().getDrawable(R.drawable.pilot);
                break;
            case 1:
                drawable = context.getResources().getDrawable(R.drawable.pink);
                break;
            case 2:
                drawable = context.getResources().getDrawable(R.drawable.orange);
                break;
            case 3:
                drawable = context.getResources().getDrawable(R.drawable.green);
                break;
            case 4:
                drawable = context.getResources().getDrawable(R.drawable.red);
                break;
            case 5:
                drawable = context.getResources().getDrawable(R.drawable.purple);
                break;
            case 6:
                drawable = context.getResources().getDrawable(R.drawable.blue);
                break;
        }

    }

    Point center_right = new Point(915, 425);
    Point center_left = new Point(350, 425);

    public void initialize(int level){
        this.level = level;
        state =  GameState.OBJECT_PLACEMENT;

        wantedPoints = new ArrayList<>(10);
        virtualPoints = new ArrayList<>(10);
        List<Integer> indicesWanted;
        List<Integer> indicesVirtual;

        switch (level){
            case 0: // demo level
                wantedPoints.add(new Point(400, 400));
                wantedPoints.add(new Point(280, 450));

                virtualPoints.add(new Point(870, 390));
                virtualPoints.add(new Point(990, 410));
                virtualPoints.add(new Point(960, 490));
                virtualPoints.add(new Point(840, 510));

                left = new Rect(200, 250, 580, 600);
                right = new Rect(700, 250, 1080, 600);
                correctSide = Side.RIGHT;

                question = Question.MORE;
                soundPlayedPoints = new boolean[wantedPoints.size()];

                break;
            case 1:
                indicesWanted = Arrays.asList(5,7,8,9,11);
                initializePoints(indicesWanted, center_right, true);
                indicesVirtual = Arrays.asList(2,3,4,5,6,7,8,9,10,11);
                initializePoints(indicesVirtual, center_left, false);

                left = new Rect(150, 150, 580, 700);
                right = new Rect(720, 150, 1130, 700);
                correctSide = Side.RIGHT;

                question = Question.LESS;
                soundPlayedPoints = new boolean[wantedPoints.size()];
                break;

            case 2:
                indicesWanted = Arrays.asList(4,5,6,7,8,9);
                initializePoints(indicesWanted, center_right, true);
                indicesVirtual = Arrays.asList(2,4,5,6,7,8,9,10,11);
                initializePoints(indicesVirtual, center_left, false);

                left = new Rect(150, 150, 580, 700);
                right = new Rect(720, 150, 1130, 700);
                correctSide = Side.LEFT;

                question = Question.MORE;
                soundPlayedPoints = new boolean[wantedPoints.size()];
                break;
            case 3:
                indicesWanted = Arrays.asList(2,4,5,6,7,8,9,10,11);
                initializePoints(indicesWanted, center_left, true);
                indicesVirtual = Arrays.asList(1,2,3,10,11,12);
                initializePoints(indicesVirtual, center_right, false);

                left = new Rect(150, 150, 580, 700);
                right = new Rect(720, 150, 1130, 700);
                correctSide = Side.RIGHT;

                question = Question.LESS;
                soundPlayedPoints = new boolean[wantedPoints.size()];
                break;
            case 4:
                indicesWanted = Arrays.asList(2,4,5,6,7,8,9,10,11);
                initializePoints(indicesWanted, center_right, true);
                indicesVirtual = Arrays.asList(1,2,4,5,6,7,8,9,10,11,12);
                initializePoints(indicesVirtual, center_left, false);

                left = new Rect(150, 150, 580, 700);
                right = new Rect(720, 150, 1130, 700);
                correctSide = Side.LEFT;

                question = Question.MORE;
                soundPlayedPoints = new boolean[wantedPoints.size()];
                break;
            case 5:
                indicesWanted = Arrays.asList(4,5,6,7,8,9);
                initializePoints(indicesWanted, center_left, true);
                indicesVirtual = Arrays.asList(2,4,5,6,7,8,9,11);
                initializePoints(indicesVirtual, center_right, false);

                left = new Rect(150, 150, 580, 700);
                right = new Rect(720, 150, 1130, 700);
                correctSide = Side.LEFT;

                question = Question.LESS;
                soundPlayedPoints = new boolean[wantedPoints.size()];
                break;
            case 6:
                indicesWanted = Arrays.asList(2,4,5,6,7,8,9,11);
                initializePoints(indicesWanted, center_left, true);
                indicesVirtual = Arrays.asList(2,3,4,5,6,7,8,9,10,11);
                initializePoints(indicesVirtual, center_right, false);

                left = new Rect(150, 150, 580, 700);
                right = new Rect(720, 150, 1130, 700);
                correctSide = Side.RIGHT;

                question = Question.MORE;
                soundPlayedPoints = new boolean[wantedPoints.size()];
                break;
        }

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

        for(Point p : wantedPoints){
            cross.setBounds(p.x-50, p.y-50, p.x+50, p.y+50);
            cross.draw(canvas);
            if(level == 0 && state == GameState.OBJECT_PLACEMENT){
                finger.setBounds(p.x-60, p.y+60, p.x+60, p.y+200);
                finger.draw(canvas);
            }
        }
        return canvas;
    }

    public boolean processBlobDescriptors(int[] descriptors){
        Canvas canvas = initializeCanvas();

        // start processing
        int count = 0;
        for (int i = 0; i <= descriptors.length - 3; i += 3)
        {
            if (descriptors[i + 2] == -1) continue; //  -1 is an edge-connected(gesture) blob
            if(descriptors[i] < 10) continue; // to overcome ghost stones

            Point p1 = new Point(descriptors[i], descriptors[i + 1]);
            boolean match = false;
            for (int j=0; j<wantedPoints.size(); j++)
            {
                Point p2 = wantedPoints.get(j);
                if (areClose(p1, p2, 55))
                {
                    canvas.drawCircle(p2.x, p2.y, 50, GamePaint.eraser);

                    drawable.setBounds(p1.x-55, p1.y-55, p1.x+55, p1.y+55);
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
            state = GameState.ALL_PLACED;
            Log.i(LOG_TAG, "Blobs: All objects are placed.");
            if(level != 0){
                drawVirtualObjects();
            }
            // In pilot level, virtual objects are drawn later.
            return true;
        }
        else{
            return false;
        }
    }

    public void drawVirtualObjects(){
        Canvas canvas = new Canvas(MainActivity.bmpOverlay);
        for(Point p1: virtualPoints){
            drawable.setBounds(p1.x-55, p1.y-55, p1.x+55, p1.y+55);
            drawable.draw(canvas);
        }
        MainActivity.soundPool.play(MainActivity.sOkay,1f,1f,1,0,1f);

        canvas.drawRect(left, GamePaint.red);
        canvas.drawRect(right, GamePaint.red);
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

    public void removeObjects(){
        Canvas canvas = new Canvas(MainActivity.bmpOverlay);
        canvas.drawPaint(GamePaint.eraser);
        canvas.drawRect(left, GamePaint.red);
        canvas.drawRect(right, GamePaint.red);
    }

    private void initializePoints(List<Integer> indices, Point offset, boolean isWanted){
        for(int ind : indices){
            Point pt = basePoints.get(ind-1);
            if(isWanted){
                wantedPoints.add(new Point(pt.x + offset.x, pt.y+ offset.y));
            }
            else{
                virtualPoints.add(new Point(pt.x + offset.x, pt.y+ offset.y));
            }
        }
    }

}
