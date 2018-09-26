package com.esalman17.embodieddemo;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.concurrent.TimeUnit;

enum Mode{
    CAMERA,
    TEST,
}

public class MainActivity extends Activity {

    static {
        System.loadLibrary("usb_android");
        System.loadLibrary("royale");
        System.loadLibrary("nativelib");
    }

    private PendingIntent mUsbPi;
    private UsbManager manager;
    private UsbDeviceConnection usbConnection;

    private Bitmap bmpCam = null;
    private Bitmap bmpOverlay = null;
    private ImageView mainImView, overlayImView;
    TextView tvInfo, tvDebug;

    Paint green = new Paint();
    Paint red = new Paint();
    Paint eraser = new Paint();

    int assessment = -1;

    boolean m_opened;
    Mode currentMode;

    private static final String LOG_TAG = "MainActivity";
    private static final String ACTION_USB_PERMISSION = "ACTION_ROYALE_USB_PERMISSION";

    int[] resolution;
    Point displaySize, camRes;

    public native int[] OpenCameraNative(int fd, int vid, int pid);
    public native void CloseCameraNative();
    public native void RegisterCallback();

    public native void RegisterDisplay(int width, int height);
    public native void DetectBackgroundNative();
    public native void ChangeModeNative(int mode);

    //broadcast receiver for user usb permission dialog
    private final BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (ACTION_USB_PERMISSION.equals(action)) {
                UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);

                if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                    if (device != null) {
                        RegisterCallback();
                        performUsbPermissionCallback(device);
                        createBitmap();
                    }
                } else {
                    System.out.println("permission denied for device" + device);
                }
            }
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRequestedOrientation (ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        getWindow().setBackgroundDrawableResource(R.drawable.back);
        setContentView(R.layout.activity_main);
        Log.d(LOG_TAG, "onCreate()");

        // hide the navigation bar
        final int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;

        getWindow().getDecorView().setSystemUiVisibility(flags);
        final View decorView = getWindow().getDecorView();
        decorView.setOnSystemUiVisibilityChangeListener(new View.OnSystemUiVisibilityChangeListener() {
            @Override
            public void onSystemUiVisibilityChange(int visibility)
            {
                if((visibility & View.SYSTEM_UI_FLAG_FULLSCREEN) == 0)
                {
                    decorView.setSystemUiVisibility(flags);
                }
            }
        });

        mainImView =  findViewById(R.id.imageViewMain);
        overlayImView = findViewById(R.id.imageViewOverlay);
        tvInfo = findViewById(R.id.textViewInfo);
        tvDebug = findViewById(R.id.textViewDebug);

        findViewById(R.id.buttonCamera).setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View view) {
                if (!m_opened) {
                    openCamera();
                }
                if(currentMode ==  Mode.TEST)
                {
                    endTestMode();
                }
                ChangeModeNative(1);
                currentMode = Mode.CAMERA;

            }
        });
        findViewById(R.id.buttonBackGr).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (!m_opened) {
                    openCamera();
                }
                if(currentMode ==  Mode.TEST)
                {
                    endTestMode();
                }
                ChangeModeNative(1);
                currentMode = Mode.CAMERA;
                DetectBackgroundNative();
            }
        });
        findViewById(R.id.buttonTest).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (!m_opened) {
                    openCamera();
                }

                initializeTestMode();
                currentMode = Mode.TEST;
                ChangeModeNative(2);
            }
        });

        green.setColor(Color.GREEN);
        green.setStyle(Paint.Style.FILL);
        red.setColor(Color.RED);
        red.setStyle(Paint.Style.STROKE);
        red.setStrokeWidth(3);
        eraser.setColor(Color.TRANSPARENT);
        eraser.setStyle(Paint.Style.FILL);
        eraser.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));

    }

    @Override
    protected void onPause() {
        if (m_opened) {
            CloseCameraNative();
            m_opened = false;
        }
        super.onPause();
        Log.d(LOG_TAG, "onPause()");
        unregisterReceiver(mUsbReceiver);
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(LOG_TAG, "onResume()");
        registerReceiver(mUsbReceiver, new IntentFilter(ACTION_USB_PERMISSION));

        displaySize = new Point();
        getWindowManager().getDefaultDisplay().getRealSize(displaySize);
        Log.i(LOG_TAG, "Window display size: x=" + displaySize.x + ", y=" + displaySize.y);
        RegisterDisplay(displaySize.x, displaySize.y);
    }

    @Override
    protected void onDestroy() {
        Log.d(LOG_TAG, "onDestroy()");
        //unregisterReceiver(mUsbReceiver);

        if(usbConnection != null) {
            usbConnection.close();
        }

        super.onDestroy();
    }

    public void openCamera() {
        Log.d(LOG_TAG, "openCamera");

        //check permission and request if not granted yet
        manager = (UsbManager) getSystemService(Context.USB_SERVICE);

        if (manager != null) {
            Log.d(LOG_TAG, "Manager valid");
        }

        HashMap<String, UsbDevice> deviceList = manager.getDeviceList();

        Log.d(LOG_TAG, "USB Devices : " + deviceList.size());

        Iterator<UsbDevice> iterator = deviceList.values().iterator();
        UsbDevice device;
        boolean found = false;
        while (iterator.hasNext()) {
            device = iterator.next();
            if (device.getVendorId() == 0x1C28 ||
                    device.getVendorId() == 0x058B ||
                    device.getVendorId() == 0x1f46) {
                Log.d(LOG_TAG, "royale device found");
                found = true;
                if (!manager.hasPermission(device)) {
                    Intent intent = new Intent(ACTION_USB_PERMISSION);
                    intent.setAction(ACTION_USB_PERMISSION);
                    mUsbPi = PendingIntent.getBroadcast(this, 0, intent, 0);
                    manager.requestPermission(device, mUsbPi);
                } else {
                    RegisterCallback();
                    performUsbPermissionCallback(device);
                    createBitmap();
                }
                break;
            }
        }
        if (!found) {
            Log.e(LOG_TAG, "No royale device found!!!");
        }
    }

    private void performUsbPermissionCallback(UsbDevice device) {
        usbConnection = manager.openDevice(device);
        Log.i(LOG_TAG, "permission granted for: " + device.getDeviceName() + ", fileDesc: " + usbConnection.getFileDescriptor());

        int fd = usbConnection.getFileDescriptor();

        resolution = OpenCameraNative(fd, device.getVendorId(), device.getProductId());
        camRes = new Point(resolution[0], resolution[1]);

        if (resolution[0] > 0) {
            m_opened = true;
        }
    }

    private void createBitmap() {

        if (bmpCam == null) {
            bmpCam = Bitmap.createBitmap(resolution[0], resolution[1], Bitmap.Config.ARGB_8888);
        }
    }

    public void amplitudeCallback(int[] amplitudes) {
        if (!m_opened)
        {
            Log.d(LOG_TAG, "Device in Java not initialized");
            return;
        }
        bmpCam.setPixels(amplitudes, 0, resolution[0], 0, 0, resolution[0], resolution[1]);

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if(currentMode == Mode.CAMERA )
                {
                    mainImView.setImageBitmap(bmpCam);
                }
            }
        });
    }

// TEST MODE FUNCTIONS ---------------------------------------------------------------------------------

    ArrayList<Point> wantedPoints;
    Rect left, right;
    long start;
    private void initializeTestMode(){
        if (bmpOverlay == null) {
            bmpOverlay = Bitmap.createBitmap(displaySize.x, displaySize.y, Bitmap.Config.ARGB_8888);
        }
        mainImView.setImageResource(R.drawable.demo1);
        overlayImView.setVisibility(View.VISIBLE);

        tvInfo.setVisibility(View.VISIBLE);
        tvInfo.setText("Feed the cats");
        assessment = -1;

        wantedPoints = new ArrayList<>(5);
        wantedPoints.add(new Point(280, 450));
        wantedPoints.add(new Point(400, 400));
        wantedPoints.add(new Point(500, 500));
        wantedPoints.add(new Point(800, 450));
        wantedPoints.add(new Point(950, 450));
        left = new Rect(200, 250, 580, 600);
        right = new Rect(700, 250, 1080, 600);

        Canvas canvas = new Canvas(bmpOverlay);
        initializeCanvas(canvas);
        overlayImView.setImageBitmap(bmpOverlay);
    }
    private void endTestMode(){
        mainImView.setImageBitmap(bmpCam);
        overlayImView.setVisibility(View.GONE);
        tvInfo.setVisibility(View.GONE);
    }

    public void shapeDetectedCallback(int[] descriptors){
        if (!m_opened)
        {
            Log.i(LOG_TAG, "Device in Java not initialized");
            return;
        }

        if(assessment == -1) {
            Canvas canvas = new Canvas(bmpOverlay);
            initializeCanvas(canvas);

            int count = 0;
            for (int i = 0; i <= descriptors.length - 3; i += 3)
            {
                if (descriptors[i + 2] == 1) continue;

                Point p1 = new Point(descriptors[i], descriptors[i + 1]);
                for (Point p2 : wantedPoints)
                {
                    if (areClose(p1, p2, 50))
                    {
                        count++;
                        canvas.drawCircle(p2.x, p2.y, 50, green);
                    }
                }
            }
            if(count == wantedPoints.size())
            {
                assessment = 0;
                Log.d(LOG_TAG,"assesment: "+ assessment);
                canvas.drawRect(left, red);
                canvas.drawRect(right, red);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        tvInfo.setText("Which cat has more food?" );
                        tvDebug.setText("Assesment has started");
                    }
                });
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            Thread.sleep(1000);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        assessment = 1;
                        start = System.currentTimeMillis();
                        Log.d(LOG_TAG,"assesment: "+ assessment);
                    }
                }).start();
            }
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    overlayImView.setImageBitmap(bmpOverlay);
                }
            });
        }
        else if(assessment == 1)
        {
            for (int i = 0; i <= descriptors.length - 3; i += 3)
            {
                if (descriptors[i + 2] == 1){
                    if(left.contains(descriptors[i],descriptors[i + 1])){
                        if (m_opened) {
                            CloseCameraNative();
                            m_opened = false;
                        }
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                tvInfo.setText("That is correct" );
                                long secs = (System.currentTimeMillis() -start) / 1000;
                                String time = "";
                                if(secs >= 60){
                                    time = String.format("%d min, %d sec", (secs/60), (secs%60));
                                }
                                else
                                {
                                    time = String.format("%d sec", (secs%60));
                                }
                                tvDebug.setText("Answered in "+time);
                            }
                        });
                    }
                    else if(right.contains(descriptors[i],descriptors[i + 1])){
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                tvInfo.setText("That is wrong. Try again");
                            }
                        });
                    }
                }
            }
        }

    }

    private void initializeCanvas(Canvas canvas){
        canvas.drawPaint(eraser);

        for(Point p : wantedPoints){
            canvas.drawCircle(p.x, p.y, 50, red);
        }
    }

    boolean areClose(Point p1, Point p2, int maxDist){
        return Math.pow(p1.x - p2.x, 2) + Math.pow(p1.y - p2.y, 2) <= maxDist*maxDist ;
    }



}

