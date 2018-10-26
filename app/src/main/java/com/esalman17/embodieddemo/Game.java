package com.esalman17.embodieddemo;

import android.graphics.Canvas;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.widget.ImageView;

/**
 * Created by esalman17 on 22.10.2018.
 */

abstract public class Game {
    protected Rect left, right;
    protected Rect gesture_left, gesture_right;
    protected Side correctSide;

    public long startTime, assestmentTime; // both in msec
    public GameState state;
    public int level;

    protected Drawable drawable;

    public enum Side {
        LEFT,
        RIGHT
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

}
