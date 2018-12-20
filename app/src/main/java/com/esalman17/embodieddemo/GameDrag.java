package com.esalman17.embodieddemo;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.util.Log;

import java.util.ArrayList;

/**
 * Created by esalman17 on 19.12.2018.
 */

public class GameDrag extends Game {
    private static String LOG_TAG = "GameDrag";
    public ArrayList<Point> dragPoints;
    public ArrayList<Point> finalPoints;
    public ArrayList<Point> virtualPoints;
    Drawable object_drag, object_virtual, cross, path;
    int lastArrived = -1;
    Rect pathRect = null;

    public GameDrag(Context context, int level) {
        this.level = level;

        object_drag = context.getResources().getDrawable(R.drawable.abs1);
        object_virtual = context.getResources().getDrawable(R.drawable.abs2);
        cross = context.getResources().getDrawable(R.drawable.cross);
        path = context.getResources().getDrawable(R.drawable.path2);

        initialize(level);

    }
    @Override
    void initialize(int level) {
        state = GameState.OBJECT_PLACEMENT;

        dragPoints = new ArrayList<>(10);
        finalPoints = new ArrayList<>(10);
        virtualPoints = new ArrayList<>(10);

        switch (level) {
            case 5:
                dragPoints.add(new Point(100, 350));
                dragPoints.add(new Point(100, 450));
                dragPoints.add(new Point(100, 550));
                dragPoints.add(new Point(100, 650));

                for(Point p: dragPoints){
                    finalPoints.add(new Point(p.x+900, p.y));
                }

                virtualPoints.add(new Point(300, 450));
                virtualPoints.add(new Point(300, 600));
                virtualPoints.add(new Point(500, 350));
                virtualPoints.add(new Point(480, 550));
                virtualPoints.add(new Point(420, 450));
                virtualPoints.add(new Point(570, 640));

                correctSide = Side.RIGHT;
                question = Question.LESS;

                left = new Rect(200, 300, 650, 700);
                right = new Rect(750, 300, 1150, 700);
                break;
        }

        initializeCanvas();

        gesture_left = new Rect(left);
        gesture_left.top = 0;
        gesture_right = new Rect(right);
        gesture_right.top = 0;

    }

    @Override
    Canvas initializeCanvas() {
        Canvas canvas = new Canvas(MainActivity.bmpOverlay);
        canvas.drawPaint(GamePaint.eraser);

        if(dragPoints.size() <= lastArrived+1){
            Log.d(LOG_TAG, "No new cross to draw");
            return canvas;
        }

        Point p = dragPoints.get(lastArrived+1);
        cross.setBounds(p.x-50, p.y-50, p.x+50, p.y+50);
        cross.draw(canvas);

        for(int i=0; i <= lastArrived; i++ ){
            Point p1 = finalPoints.get(i);
            //canvas.drawCircle(p1.x, p1.y, 45, GamePaint.red);
            object_drag.setBounds(p1.x-45, p1.y-45, p1.x+45, p1.y+45);
            object_drag.draw(canvas);
        }

        return canvas;
    }

    @Override
    boolean processBlobDescriptors(int[] descriptors) {
        Canvas canvas = initializeCanvas();

        // start processing
        for (int i = 0; i <= descriptors.length - 3; i += 3) {
            if (descriptors[i + 2] < 0) continue; // -1: gesture blob, -2,-90: long stones
            if (descriptors[i + 2] > 15) continue;  // retro higher than 20 mm
            //Log.d(LOG_TAG, "Retro: "+ descriptors[i+2]);
            Point p1 = new Point(descriptors[i], descriptors[i + 1]);

            // Check if it is arrived
            boolean match = false;
            for (int j=0; j<finalPoints.size(); j++)
            {
                Point p2 = finalPoints.get(j);
                if (areClose(p1, p2, 30))
                {
                    match = true;
                    if(pathRect != null && j == lastArrived+1){ // fish arrived
                        MainActivity.soundPool.play(MainActivity.sOkay,1f,1f,1,0,1f);
                        canvas.drawCircle(pathRect.left+20, pathRect.top+50, 50, GamePaint.eraser);
                        object_drag.setBounds(p1.x-45, p1.y-45, p1.x+45, p1.y+45);
                        object_drag.draw(canvas);

                        pathRect = null;
                        lastArrived++;
                        if(lastArrived == dragPoints.size()-1){
                            state = GameState.LEFT_PLACED;
                            Log.d(LOG_TAG, "Draging done: state = LEFT_PLACED");
                            return true;
                        }
                    }
                    /*if(j <= lastArrived){
                        canvas.drawCircle(p2.x, p2.y, 50, GamePaint.eraser);
                        object_drag.setBounds(p1.x-45, p1.y-45, p1.x+45, p1.y+45);
                        object_drag.draw(canvas);
                    }*/
                    break;
                }
            }
            if(match) continue;

            if(pathRect != null){
                if(pathRect.contains(p1.x,p1.y)){
                    canvas.drawCircle(pathRect.left+50, pathRect.top+50, 50, GamePaint.eraser);
                    path.setBounds(pathRect);
                    path.draw(canvas);
                    object_drag.setBounds(p1.x-45, p1.y-45, p1.x+45, p1.y+45);
                    object_drag.draw(canvas);
                }
                else {
                    canvas.drawCircle(p1.x, p1.y, 10, GamePaint.blue);
                    pathRect = null;
                }
                continue;
            }

            //If it is a new swimmer, light up
            Point p2 = dragPoints.get(lastArrived+1);
            if (areClose(p1, p2, 45))
            {
                pathRect = new Rect(p2.x-50, p2.y-50, p2.x+950, p2.y+50);

                canvas.drawCircle(p2.x, p2.y, 50, GamePaint.eraser);
                path.setBounds(pathRect);
                path.draw(canvas);
                object_drag.setBounds(p1.x-45, p1.y-45, p1.x+45, p1.y+45);
                object_drag.draw(canvas);

                MainActivity.soundPool.play(MainActivity.sOkay,1f,1f,1,0,1f);
            }
            else{
                canvas.drawCircle(p1.x, p1.y, 10, GamePaint.blue);
            }

        }

        return false;
    }

    @Override
    int processGestureDescriptors(int[] descriptors) {
        for (int i = 0; i <= descriptors.length - 3; i += 3)
        {
            if (descriptors[i + 2] == -1){  //  -1 is an edge-connected(gesture) blob
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

    public void drawVirtualObjects(){
        Canvas canvas = new Canvas(MainActivity.bmpOverlay);
        for(Point p1: virtualPoints){
            object_virtual.setBounds(p1.x-45, p1.y-45, p1.x+45, p1.y+45);
            object_virtual.draw(canvas);
        }
        MainActivity.soundPool.play(MainActivity.sOkay,1f,1f,1,0,1f);
    }

    public void removeObjects(){
        Canvas canvas = new Canvas(MainActivity.bmpOverlay);
        canvas.drawPaint(GamePaint.eraser);
        canvas.drawRect(left, GamePaint.red);
        canvas.drawRect(right, GamePaint.red);
    }
}
