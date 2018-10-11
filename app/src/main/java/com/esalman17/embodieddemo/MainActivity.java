package com.esalman17.embodieddemo;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.media.AudioManager;
import android.media.SoundPool;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.widget.TextView;

import java.util.HashMap;
import java.util.Iterator;

import nl.dionsegijn.konfetti.KonfettiView;
import nl.dionsegijn.konfetti.models.Shape;
import nl.dionsegijn.konfetti.models.Size;


public class MainActivity extends Activity {

    static {
        System.loadLibrary("usb_android");
        System.loadLibrary("royale");
        System.loadLibrary("nativelib");
    }

    private PendingIntent mUsbPi;
    private UsbManager manager;
    private UsbDeviceConnection usbConnection;

    public Bitmap bmpCam = null;
    public static Bitmap bmpOverlay = null;
    private ImageView mainImView, overlayImView;
    TextView tvInfo, tvDebug;
    KonfettiView konfettiView;

    boolean m_opened;
    Mode currentMode;

    private static final String LOG_TAG = "MainActivity";
    private static final String ACTION_USB_PERMISSION = "ACTION_ROYALE_USB_PERMISSION";

    int[] resolution;
    Point displaySize, camRes;

    public static SoundPool soundPool;
    public static boolean soundsLoaded = false, isPlaying = false;
    public static int sOkay;
    int sWrong, sApplause, sBack, sBackPlayId;

    public native int[] OpenCameraNative(int fd, int vid, int pid);
    public native boolean StartCaptureNative();
    public native boolean StopCaptureNative();
    public native void RegisterCallback();

    public native void RegisterDisplay(int width, int height);
    public native void DetectBackgroundNative();
    public native void ChangeModeNative(int mode);

    //broadcast receiver for user usb permission dialog
    private final BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.i(LOG_TAG,"BroadcastReceiver: onReceive()");
            String action = intent.getAction();
            if (ACTION_USB_PERMISSION.equals(action)) {
                UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);

                if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                    if (device != null) {
                        RegisterCallback();
                        m_opened = performUsbPermissionCallback(device);
                        Log.d(LOG_TAG, "m_opened = " + m_opened);
                        createBitmap();
                    }
                } else {
                    Log.i(LOG_TAG,"permission denied for device" + device);
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
        konfettiView = findViewById(R.id.viewKonfetti);
        tvInfo = findViewById(R.id.textViewInfo);
        tvDebug = findViewById(R.id.textViewDebug);

        findViewById(R.id.buttonCamera).setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View view) {
                if(m_opened) StartCaptureNative();
                else openCamera();

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
                if(m_opened) StartCaptureNative();
                else openCamera();

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
                if(m_opened) StartCaptureNative();
                else openCamera();

                initializeTestMode();
                currentMode = Mode.TEST;
                ChangeModeNative(2);
            }
        });

        soundPool = new SoundPool(2, AudioManager.STREAM_MUSIC, 0);
        soundPool.setOnLoadCompleteListener(new SoundPool.OnLoadCompleteListener() {
            @Override
            public void onLoadComplete(SoundPool soundPool, int i, int i1) {
                soundsLoaded = true;
            }
        });
        sBack = soundPool.load(this, R.raw.back_music, 1);
        sOkay = soundPool.load(this, R.raw.correct, 1);
        sApplause = soundPool.load(this, R.raw.applause, 1);
        sWrong = soundPool.load(this, R.raw.wrong2, 1);

    }

    @Override
    protected void onPause() {
        if (m_opened) {
            StopCaptureNative();
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

        if (m_opened) {
            StartCaptureNative();
        }
    }

    @Override
    protected void onDestroy() {
        Log.d(LOG_TAG, "onDestroy()");
        //unregisterReceiver(mUsbReceiver);

        if(usbConnection != null) {
            usbConnection.close();
        }

        soundPool.release();
        soundPool = null;

        super.onDestroy();
    }

    public void openCamera() {
        Log.d(LOG_TAG, "openCamera");

        //check permission and request if not granted yet
        manager = (UsbManager) getSystemService(Context.USB_SERVICE);

        if (manager != null) {
            Log.d(LOG_TAG, "Usb manager valid");
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
                    Log.d(LOG_TAG, "Manager has not permission, Ask...");
                    Intent intent = new Intent(ACTION_USB_PERMISSION);
                    intent.setAction(ACTION_USB_PERMISSION);
                    mUsbPi = PendingIntent.getBroadcast(this, 0, intent, 0);
                    manager.requestPermission(device, mUsbPi);
                } else {
                    Log.d(LOG_TAG, "Manager has permission.");
                    RegisterCallback();
                    m_opened = performUsbPermissionCallback(device);
                    Log.d(LOG_TAG, "m_opened = " + m_opened);
                    createBitmap();
                }
                break;
            }
        }
        if (!found) {
            Log.e(LOG_TAG, "No royale device found!!!");
        }
    }

    private boolean performUsbPermissionCallback(UsbDevice device) {
        usbConnection = manager.openDevice(device);
        Log.i(LOG_TAG, "permission granted for: " + device.getDeviceName() + ", fileDesc: " + usbConnection.getFileDescriptor());

        int fd = usbConnection.getFileDescriptor();

        resolution = OpenCameraNative(fd, device.getVendorId(), device.getProductId());
        camRes = new Point(resolution[0], resolution[1]);

        if (resolution[0] > 0) {
            StartCaptureNative();
            return true;
        }
        else{
            return false;
        }
    }

    private void createBitmap() {

        if (bmpCam == null) {
            bmpCam = Bitmap.createBitmap(resolution[0], resolution[1], Bitmap.Config.ARGB_8888);
        }
    }

    public void amplitudeCallback(int[] amplitudes) {
        if (!m_opened || bmpCam == null)
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

    Game game1;
    private void initializeTestMode(){
        if (bmpOverlay == null) {
            bmpOverlay = Bitmap.createBitmap(displaySize.x, displaySize.y, Bitmap.Config.ARGB_8888);
        }

        overlayImView.setVisibility(View.VISIBLE);
        tvInfo.setVisibility(View.VISIBLE);
        tvInfo.setText("Feed the cats");

        game1 = new Game(this, 1);
        game1.setBackground(mainImView,R.drawable.demo1);
        overlayImView.setImageBitmap(bmpOverlay); // bmpOverlay is initalized in game constructor

        if(soundsLoaded){
            sBackPlayId = soundPool.play(sBack, 0.1f, 0.1f,1,-1,1f);
        }
    }
    private void endTestMode(){
        mainImView.setImageBitmap(bmpCam);
        overlayImView.setVisibility(View.GONE);
        tvInfo.setVisibility(View.GONE);
        soundPool.stop(sBackPlayId);
    }

    public void shapeDetectedCallback(int[] descriptors){
        if (!m_opened)
        {
            Log.i(LOG_TAG, "Device in Java not initialized");
            return;
        }

        if(game1.state == GameState.OBJECT_PLACEMENT) {
            final boolean allObjectsPlaced =game1.processBlobDescriptors(descriptors);
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    overlayImView.setImageBitmap(bmpOverlay);
                    if(allObjectsPlaced){
                        tvInfo.setText("Which cat has more food?" );
                        tvDebug.setText("Assesment has started");
                    }
                }
            });
        }
        else if(game1.state == GameState.ASSESMENT_RUNNING)
        {
            final int correctAnswer = game1.processGestureDescriptors(descriptors);
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if(correctAnswer == 1){
                        StopCaptureNative();
                        tvInfo.setText("That is correct" );
                        playSound(sApplause);
                        overlayImView.setImageDrawable(null);
                        addKonfetti("burst");
                        float secs = (float)game1.assestmentTime /1000;
                        String time = String.format("Answered in %.3f seconds", secs);
                        tvDebug.setText(time);
                    }
                    else if(correctAnswer == -1){
                        tvInfo.setText("That is wrong. Try again");
                        playSound(sWrong);
                    }
                }
            });
        }

    }

    private void playSound(int id){
        if(!isPlaying && soundsLoaded){
            isPlaying = true;
            soundPool.play(id, 1f, 1f, 1, 0, 1f);
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    isPlaying = false;
                }
            }).start();
        }
    }

    private void addKonfetti(String type){
        switch (type){
            case "top":
                konfettiView.build()
                        .addColors(Color.YELLOW, Color.GREEN, Color.BLUE, Color.RED)
                        .setDirection(0.0, 359.0)
                        .setSpeed(1f, 5f)
                        .setFadeOutEnabled(true)
                        .setTimeToLive(1500L)
                        .addShapes(Shape.RECT, Shape.CIRCLE)
                        .addSizes(new Size(12, 5f))
                        .setPosition(-50f, konfettiView.getWidth() + 50f, -50f, -50f)
                        .streamFor(500, 3000L);
                break;
            case "burst":
                konfettiView.build()
                        .addColors(Color.YELLOW, Color.GREEN, Color.BLUE, Color.RED)
                        .setDirection(0.0, 359.0)
                        .setSpeed(1f, 5f)
                        .setFadeOutEnabled(true)
                        .setTimeToLive(3000L)
                        .addShapes(Shape.RECT, Shape.CIRCLE)
                        .addSizes(new Size(15, 5f))
                        .setPosition(konfettiView.getX() + konfettiView.getWidth() / 2, konfettiView.getY() + konfettiView.getHeight() / 3)
                        .burst(200);
                konfettiView.build()
                        .addColors(Color.YELLOW, Color.GREEN, Color.BLUE, Color.RED)
                        .setDirection(0.0, 359.0)
                        .setSpeed(1f, 5f)
                        .setFadeOutEnabled(true)
                        .setTimeToLive(3000L)
                        .addShapes(Shape.RECT, Shape.CIRCLE)
                        .addSizes(new Size(15, 5f))
                        .setPosition(konfettiView.getX() + konfettiView.getWidth() / 4, konfettiView.getY() + konfettiView.getHeight() / 3)
                        .burst(200);
                konfettiView.build()
                        .addColors(Color.YELLOW, Color.GREEN, Color.BLUE, Color.RED)
                        .setDirection(0.0, 359.0)
                        .setSpeed(1f, 5f)
                        .setFadeOutEnabled(true)
                        .setTimeToLive(3000L)
                        .addShapes(Shape.RECT, Shape.CIRCLE)
                        .addSizes(new Size(15, 5f))
                        .setPosition(konfettiView.getX() + 3*konfettiView.getWidth() / 4, konfettiView.getY() + konfettiView.getHeight() / 3)
                        .burst(200);
                break;

        }

    }


}

