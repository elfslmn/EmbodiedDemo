package com.esalman17.embodieddemo;

import android.graphics.Canvas;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.util.Log;
import android.widget.ImageView;

import java.util.ArrayList;

/**
 * Created by esalman17 on 22.10.2018.
 */

abstract public class Game {
    protected Rect left, right, middle;
    protected Rect gesture_left, gesture_right, gesture_middle;
    protected Side correctSide;

    protected ArrayList<Point> basePoints = new ArrayList<Point>(12){
        {
            add(new Point(-120,-170));
            add(new Point(-5,-180));
            add(new Point(115,-180));

            add(new Point(-130,-60));
            add(new Point(0,-55));
            add(new Point(130,-65));

            add(new Point(-110,55));
            add(new Point(5,60));
            add(new Point(120,55));

            add(new Point(-120,165));
            add(new Point(-5,180));
            add(new Point(115,170));
        }
    };

    public long startTime, assestmentTime; // both in msec
    public GameState state;
    public PlacementState pState = PlacementState.NO_OBJECT;
    public int level;
    public Question question;

    protected Drawable drawable;

    public enum Side {
        LEFT,
        RIGHT,
        MIDDLE
    }

    public enum Question{
        MORE,
        LESS,
        EQUAL
    }

    abstract void initialize(int level);
    abstract Canvas initializeCanvas();
    abstract boolean processBlobDescriptors(int[] descriptors);
    abstract int processGestureDescriptors(int[] descriptors);


    final public void setBackground(ImageView view, int drawableId){
        view.setImageResource(drawableId);
    }

    final protected boolean areClose(Point p1, Point p2, int maxDist){
        return Math.pow(p1.x - p2.x, 2) + Math.pow(p1.y - p2.y, 2) <= maxDist*maxDist ;
    }

    final protected Rect getCorrectSide(){
        if(correctSide == Side.LEFT) return gesture_left;
        else if(correctSide == Side.RIGHT) return gesture_right;
        else return null;
    }

    final protected Rect getWrongSide(){
        if(correctSide == Side.LEFT) return gesture_right;
        else if(correctSide == Side.RIGHT) return gesture_left;
        else return null;
    }

    final public int getQuestion(){
        if(question == Question.MORE) return R.raw.soru_cok;
        if(question == Question.LESS) return R.raw.soru_az;
        if(question == Question.EQUAL) return R.raw.soru_esit;

        Log.d("Game", "Question is not specified");
        return -1;
    }

    public void drawRects(){
        Canvas canvas = new Canvas(MainActivity.bmpOverlay);
        canvas.drawRect(left, GamePaint.red);
        canvas.drawRect(right, GamePaint.red);
        if(gesture_middle != null) canvas.drawRect(middle, GamePaint.red);
    }

    final public void setState(GameState state){
        this.state = state;
        Log.d("Game", "State = "+state);
    }

}
