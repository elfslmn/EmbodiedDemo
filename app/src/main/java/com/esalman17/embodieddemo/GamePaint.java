package com.esalman17.embodieddemo;

import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;

/**
 * Created by esalman17 on 8.10.2018.
 */

public class GamePaint {
    public static android.graphics.Paint green = new android.graphics.Paint();
    public static android.graphics.Paint red = new android.graphics.Paint();
    public static android.graphics.Paint blue = new android.graphics.Paint();
    public static android.graphics.Paint black = new android.graphics.Paint();
    public static android.graphics.Paint eraser = new android.graphics.Paint();

    static {
        green.setColor(Color.GREEN);
        green.setStyle(android.graphics.Paint.Style.FILL);

        blue.setColor(Color.BLUE);
        blue.setStyle(android.graphics.Paint.Style.FILL);

        red.setColor(Color.RED);
        red.setStyle(android.graphics.Paint.Style.STROKE);
        red.setStrokeWidth(4);

        black.setColor(Color.BLACK);
        black.setStyle(Paint.Style.FILL);

        eraser.setColor(Color.TRANSPARENT);
        eraser.setStyle(android.graphics.Paint.Style.FILL);
        eraser.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
    }
}
