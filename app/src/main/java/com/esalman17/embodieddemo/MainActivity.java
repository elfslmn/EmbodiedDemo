package com.esalman17.embodieddemo;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.media.AudioManager;
import android.media.SoundPool;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.transitionseverywhere.Slide;
import com.transitionseverywhere.TransitionManager;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Timer;
import java.util.TimerTask;

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

    RelativeLayout mainLayout;
    ImageView mainImView, overlayImView;
    TextView tvInfo, tvDebug, tvLevel, tvResult;
    KonfettiView konfettiView;

    Slide slide = new Slide();

    boolean m_opened;
    Mode currentMode;

    private static final String LOG_TAG = "MainActivity";
    private static final String ACTION_USB_PERMISSION = "ACTION_ROYALE_USB_PERMISSION";

    int[] resolution;
    Point displaySize, camRes;

    public static SoundPool soundPool;
    public static boolean soundsLoaded = false, isPlaying = false;
    public static int sOkay;
    int sWrong, sApplause, sBack, sBackPlayId, sCong;

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

        mainLayout = findViewById(R.id.mainLayout);
        mainImView =  findViewById(R.id.imageViewMain);
        overlayImView = findViewById(R.id.imageViewOverlay);
        konfettiView = findViewById(R.id.viewKonfetti);
        tvInfo = findViewById(R.id.textViewInfo);
        tvDebug = findViewById(R.id.textViewDebug);
        tvLevel = findViewById(R.id.textViewLevel);
        tvResult = findViewById(R.id.textViewResults);

        findViewById(R.id.buttonCamera).setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View view) {
                clearPreviousMode();
                ChangeModeNative(1);
                currentMode = Mode.CAMERA;

            }
        });
        findViewById(R.id.buttonBackGr).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                clearPreviousMode();
                ChangeModeNative(1);
                currentMode = Mode.CAMERA;
                DetectBackgroundNative();
            }
        });
        findViewById(R.id.buttonTest).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                clearPreviousMode();
                initializeTestMode(1);
                currentMode = Mode.TEST;
                ChangeModeNative(2);
            }
        });

        findViewById(R.id.buttonTest2).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                clearPreviousMode();
                initializeTestMode(2);
                currentMode = Mode.TEST;
                ChangeModeNative(2);
            }
        });

        soundPool = new SoundPool(5, AudioManager.STREAM_MUSIC, 0);
        soundPool.setOnLoadCompleteListener(new SoundPool.OnLoadCompleteListener() {
            @Override
            public void onLoadComplete(SoundPool soundPool, int i, int i1) {
                soundsLoaded = true;
            }
        });
        sBack = soundPool.load(this, R.raw.back_music, 10);
        sOkay = soundPool.load(this, R.raw.correct, 1);
        sApplause = soundPool.load(this, R.raw.applause, 1);
        sWrong = soundPool.load(this, R.raw.wrong2, 1);
        sCong = soundPool.load(this, R.raw.congratulations, 1);

        slide.setSlideEdge(Gravity.BOTTOM);
        slide.addTarget(tvLevel);
        slide.setDuration(500);

    }

    private void clearPreviousMode(){
        if(m_opened) StartCaptureNative();
        else openCamera();
        if(currentMode ==  Mode.TEST)
        {
            endTestMode();
        }
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

    Game game;
    String[] results = new String[4];
    private void initializeTestMode(int test){
        if (bmpOverlay == null) {
            bmpOverlay = Bitmap.createBitmap(displaySize.x, displaySize.y, Bitmap.Config.ARGB_8888);
        }

        overlayImView.setVisibility(View.VISIBLE);
        tvInfo.setVisibility(View.VISIBLE);
        tvInfo.setText("Feed the cats");

        if(test == 1) {
            game = new GameBothReal(this, 1);
            game.setBackground(mainImView, R.drawable.demo1);
            overlayImView.setImageBitmap(bmpOverlay); // bmpOverlay is initalized in game constructor
            showLevelInfo("LEVEL 1\nPlace the objects into red circles");
        }
        else if(test == 2){
            game = new GameHalfVirtual(this, 1);
            game.setBackground(mainImView, R.drawable.demo2); // bmpOverlay is initalized in game constructor
            overlayImView.setImageBitmap(bmpOverlay);
            showLevelInfo("LEVEL 3\nPlace the objects into red circles");
        }
        wrong = 0;

        if(soundsLoaded){
            sBackPlayId = soundPool.play(sBack, 0.1f, 0.1f,1,-1,1f);
        }
    }
    private void endTestMode(){
        mainImView.setImageBitmap(bmpCam);
        overlayImView.setVisibility(View.GONE);
        tvInfo.setVisibility(View.GONE);
        tvResult.setVisibility(View.GONE);
        soundPool.stop(sBackPlayId);
    }

    public void shapeDetectedCallback(int[] descriptors){
        if (!m_opened)
        {
            Log.i(LOG_TAG, "Device in Java not initialized");
            return;
        }

        if(game instanceof GameBothReal){
            if(game.state == GameState.OBJECT_PLACEMENT) {
                final boolean allObjectsPlaced =game.processBlobDescriptors(descriptors);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        overlayImView.setImageBitmap(bmpOverlay);
                        if(allObjectsPlaced){
                            tvInfo.setText("Which cat has more apple?" );
                            tvDebug.setText("Assesment has started");
                        }
                    }
                });
            }
            else if(game.state == GameState.ASSESMENT_RUNNING)
            {
                final int correctAnswer = game.processGestureDescriptors(descriptors);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if(correctAnswer == 1){
                            StopCaptureNative();
                            tvInfo.setText("That is correct" );
                            playSound(sApplause, sCong);
                            overlayImView.setImageDrawable(null);
                            addKonfetti("burst");
                            float secs = (float)game.assestmentTime /1000;
                            String time = String.format("Solved in %.3f seconds with %d wrong", secs, wrong);
                            results[0] = time;
                            tvDebug.setText(time);
                        }
                        else if(correctAnswer == -1){
                            tvInfo.setText("That is wrong. Try again");
                            playSound(sWrong);
                        }
                    }
                });

                // Test1 finished , apply test1 plus (equality)
                if(correctAnswer == 1 && game.level == 1){
                    sleep(4000);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            tvInfo.setText(null);
                            showLevelInfo("LEVEL 2\n Give equal number of apples to both cats");
                        }
                    });
                    sleep(3500);
                    game = new GameEqualize(this, 1);
                    wrong = 0;
                    StartCaptureNative();
                    game.startTime = System.currentTimeMillis();
                }
            }
        }

        else if(game instanceof GameEqualize){
            if(game.state == GameState.ASSESMENT_RUNNING) {
                final boolean correctAnswer = game.processBlobDescriptors(descriptors);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (correctAnswer) {
                            StopCaptureNative();
                            tvInfo.setText("That is correct");
                            playSound(sApplause, sCong);
                            overlayImView.setImageDrawable(null);
                            addKonfetti("burst");
                            float secs = (float) game.assestmentTime / 1000;
                            String time = String.format("Solved in %.3f seconds", secs);
                            results[1] = time;
                            tvDebug.setText(time);
                        } else {
                            overlayImView.setImageBitmap(bmpOverlay);
                        }
                    }
                });
            }
        }

        else if(game instanceof GameHalfVirtual){
            if(game.state == GameState.OBJECT_PLACEMENT) {
                final boolean allObjectsPlaced =game.processBlobDescriptors(descriptors);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        overlayImView.setImageBitmap(bmpOverlay);
                        if(allObjectsPlaced){
                            tvInfo.setText("Which cat has more oranges?" );
                            tvDebug.setText("Assesment has started");
                        }
                    }
                });
            }
            else if(game.state == GameState.ASSESMENT_RUNNING)
            {
                final int correctAnswer = game.processGestureDescriptors(descriptors);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if(correctAnswer == 1){
                            StopCaptureNative();
                            tvInfo.setText("That is correct" );
                            playSound(sApplause, sCong);
                            overlayImView.setImageDrawable(null);
                            addKonfetti("burst");
                            float secs = (float)game.assestmentTime /1000;
                            String time = String.format("Solved in %.3f seconds with %d wrong", secs, wrong);
                            results[game.level+1] = time;
                            tvDebug.setText(time);
                        }
                        else if(correctAnswer == -1){
                            tvInfo.setText("That is wrong. Try again");
                            playSound(sWrong);
                        }
                    }
                });

                // Test2 finished , apply test2 plus
                if(correctAnswer == 1 && game.level == 1){
                    sleep(4000);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            showLevelInfo("LEVEL 4\n Choose one of the rectangles");
                        }
                    });
                    sleep(3500); 
                    game = new GameHalfVirtual(this, 2);
                    wrong = 0;
                    StartCaptureNative();
                }
                // Test2 finished , end of the session
                else if(correctAnswer == 1 && game.level == 2){
                    sleep(4000);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            String info = "Results: \n";
                            for(int i=0; i <4; i++){
                                info += "Level "+(i+1)+": ";
                                if(results[i] == null){
                                   info += "Did not attempted \n";
                                }
                                else{
                                   info += results[i]+"\n";
                                }
                            }
                            tvInfo.setText(null);
                            soundPool.stop(sBackPlayId);
                            tvResult.setText(info);
                            TransitionManager.beginDelayedTransition(mainLayout,slide);
                            tvResult.setVisibility(View.VISIBLE);
                        }
                    });
                }
            }
        }



    }

    private void playSound(int id){
        playSound(id,0);
    }
    int wrong = 0;
    private void playSound(int id, int id2){
        if(!isPlaying && soundsLoaded){
            isPlaying = true;
            soundPool.play(id, 1f, 1f, 1, 0, 1f);
            if(id2 != 0) soundPool.play(id2, 1f, 1f, 1, 0, 1f);

            if(id == sWrong) wrong++;

            new Thread(new Runnable() {
                @Override
                public void run() {
                    sleep(1000);
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

    private void showLevelInfo(String info){
        tvLevel.setText(info);
        TransitionManager.beginDelayedTransition(mainLayout,slide);
        tvLevel.setVisibility(View.VISIBLE);
        Timer t = new Timer(false);
        t.schedule(new TimerTask() {
            @Override
            public void run() {
                runOnUiThread(new Runnable() {
                    public void run() {
                        TransitionManager.beginDelayedTransition(mainLayout,slide);
                        tvLevel.setVisibility(View.GONE);
                    }
                });
            }
        }, 3000);
    }

    private void sleep(long millis){
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }


}

