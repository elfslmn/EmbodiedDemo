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
    final static int stone_height = 11;

    private ArrayList<Rect> longStonePoints;
    private boolean[] soundPlayedPoints;
    Drawable drawable, cross;

    public GameStacking(Context context, int level){
        this.level = level;
        drawable = context.getResources().getDrawable(R.drawable.green);
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
                stackPoints.put(new Point(300, 450), -13); // -13, ilk taşın yuksekliğine bakmadan click sound için
                wantedStackHeight = 3;

                longStonePoints.add(new Rect(680, 480, 800, 560));
                longStonePoints.add(new Rect(810, 480, 930, 560));
                longStonePoints.add(new Rect(940, 480, 1060, 560));
                longStonePoints.add(new Rect(1070, 480, 1190, 560));

                correctSide = Side.RIGHT;
                question = Question.MORE;

                left = new Rect(100, 350, 500, 650);
                right = new Rect(650, 350, 1220, 650);
                break;

            case 4:
                stackPoints.put(new Point(200, 500), -13);
                stackPoints.put(new Point(370, 500), -13);
                wantedStackHeight = 2;

                longStonePoints.add(new Rect(700, 400, 820, 480));
                longStonePoints.add(new Rect(830, 400, 950, 480));
                longStonePoints.add(new Rect(960, 400, 1080, 480));

                correctSide = Side.LEFT;
                question = Question.MORE;

                left = new Rect(100, 350, 500, 650);
                right = new Rect(600, 350, 1150, 650);

                break;

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

        if(state == GameState.OBJECT_PLACEMENT) {
            for (Point p : stackPoints.keySet()) {
                cross.setBounds(p.x - 50, p.y - 50, p.x + 50, p.y + 50);
                cross.draw(canvas);
            }
        }
       else if(state == GameState.LEFT_PLACED){
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

            if( descriptors[i + 2] >= 0) // Circle stones
            {
                int height = descriptors[i + 2];
                //Log.d(LOG_TAG, "retro height : "+height);

                for (Point p2 : stackPoints.keySet()) {
                    if (areClose(p1, p2, 70)) {
                        if(state == GameState.LEFT_PLACED){ // geçtikten sonra taşın sönmesini engellemek için
                            drawable.setBounds(p1.x - 50, p1.y - 50, p1.x + 60, p1.y + 60);
                            drawable.draw(canvas);
                            match = true;
                            break;
                        }
                        canvas.drawCircle(p2.x, p2.y, 51, GamePaint.eraser);
                        if(height >= stone_height*wantedStackHeight){
                            drawable.setBounds(p1.x - 50, p1.y - 50, p1.x + 60, p1.y + 60);
                            drawable.draw(canvas);
                        }
                        else{
                            cross.setBounds(p1.x - 45, p1.y - 45, p1.x + 55, p1.y + 55);
                            cross.draw(canvas);
                        }
                        match = true;
                        if (height - stackPoints.get(p2) >= stone_height) {
                            MainActivity.soundPool.play(MainActivity.sOkay, 1f, 1f, 1, 0, 1f);
                        }
                        stackPoints.put(p2, height);
                        break;
                    }
                }
            }
            else if(state == GameState.LEFT_PLACED && descriptors[i + 2] <= -2){ // Long Stones
                int angle = descriptors[i + 2];
                for(int j=0; j<longStonePoints.size(); j++){
                    Rect r = longStonePoints.get(j);
                    if(r.contains(p1.x,p1.y) && angle > -20){
                        canvas.drawRect(r, GamePaint.green);
                        match = true;
                        longCount++;
                        if( !soundPlayedPoints[j]){
                            soundPlayedPoints[j] = true;
                            MainActivity.soundPool.play(MainActivity.sOkay,1f,1f,1,0,1f);
                        }
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
                if(stackPoints.get(p2)>= stone_height*wantedStackHeight) count++;
            }
            if(count == stackPoints.size()) // todo karşıya geçmeyi trigger etmek için bir kaç frame bekle
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
    int processGestureDescriptors(int[] descriptors)
    {
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
}
