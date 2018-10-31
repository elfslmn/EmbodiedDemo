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
import android.media.MediaPlayer;
import android.media.SoundPool;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.widget.RelativeLayout;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;

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
    public static int REMOVAL_DELAY = 2500; //TODO for 3 year

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
    TableLayout levelLayout;
    ImageView mainImView, overlayImView;
    TextView tvDebug, tvLevel, tvResult;
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
    int sMerhaba, sTas, sHarika, sHazırsın;

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

    View.OnClickListener levelListener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            clearPreviousMode();
            switch (view.getId()){
                case R.id.buttonIntro:
                    initializeTestMode(0);
                    break;
                case R.id.button1:
                    initializeTestMode(1);
                    break;
                case R.id.button2:
                    initializeTestMode(2);
                    break;
                case R.id.button3:
                    initializeTestMode(3);
                    break;
                case R.id.button4:
                    initializeTestMode(4);
                    break;
                case R.id.button5:
                    initializeTestMode(5);
                    break;
                case R.id.button6:
                    initializeTestMode(6);
                    break;
            }

            currentMode = Mode.TEST;
            ChangeModeNative(2);
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
        levelLayout = findViewById(R.id.layout_levels);
        for (int i = 0; i < levelLayout.getChildCount(); i++) {
            View child = levelLayout.getChildAt(i);
            if (child instanceof TableRow) {
                TableRow row = (TableRow) child;
                for (int j = 0; j < row.getChildCount(); j++) {
                    row.getChildAt(j).setOnClickListener(levelListener);
                }
            }
        }

        mainImView =  findViewById(R.id.imageViewMain);
        overlayImView = findViewById(R.id.imageViewOverlay);
        konfettiView = findViewById(R.id.viewKonfetti);
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
        findViewById(R.id.buttonIntro).setOnClickListener(levelListener);

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
        sWrong = soundPool.load(this, R.raw.bir_daha_dusun, 1);
        sCong = soundPool.load(this, R.raw.supersin, 1);

        slide.setSlideEdge(Gravity.BOTTOM);
        slide.addTarget(tvLevel);
        slide.addTarget(tvResult);
        slide.setDuration(500);

    }

    private void clearPreviousMode(){
        if(m_opened){
            //if(currentMode != Mode.TEST ) StartCaptureNative(); // TODO it will be started in test initilize
            StartCaptureNative();
        }
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
            Toast.makeText(this, "Camera is not connected!", Toast.LENGTH_SHORT).show();
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
            Toast.makeText(this, "Camera opened.", Toast.LENGTH_SHORT).show();
            return true;
        }
        else{
            Toast.makeText(this, "Camera cannot opened.", Toast.LENGTH_SHORT).show();
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
    String[] results = new String[7];
    MediaPlayer mediaPlayer;
    Timer timer;
    int wrong = 0;

    private void initializeTestMode(int test){
        if (bmpOverlay == null) {
            bmpOverlay = Bitmap.createBitmap(displaySize.x, displaySize.y, Bitmap.Config.ARGB_8888);
        }

        timer = new Timer(false);
        overlayImView.setVisibility(View.VISIBLE);
        wrong = 0;

        if(test == 0){ // PILOT LEVEL
            StopCaptureNative();
            game = new GameHalfVirtual(this, 0);
            game.setBackground(mainImView, R.drawable.dima_and_garden);

            playMedia(R.raw.ana_naratif1);

            delayedUICommand(19000, new Runnable() {
                @Override
                public void run() {
                    overlayImView.setImageBitmap(bmpOverlay);
                    StartCaptureNative();
                }
            });
        }
        else {
            game = new GameHalfVirtual(this, test);
            game.setBackground(mainImView, R.drawable.dima_and_garden);
            overlayImView.setImageBitmap(bmpOverlay); // bmpOverlay is initalized in game constructor

            showLevelInfo("LEVEL " + test);
            playMedia(R.raw.taslari_koy);
            delayedUICommand(3000, new Runnable() {
                @Override
                public void run() {
                    StartCaptureNative();
                }
            });

        }

        /*if(soundsLoaded && test !=0){
            sBackPlayId = soundPool.play(sBack, 0.1f, 0.1f,1,-1,1f);
        }*/
    }
    private void endTestMode(){
        mainImView.setImageBitmap(bmpCam);
        overlayImView.setImageDrawable(null);
        overlayImView.setVisibility(View.GONE);
        tvResult.setVisibility(View.GONE);
        soundPool.stop(sBackPlayId);
        if(mediaPlayer != null){
            mediaPlayer.release();
        }
        if(timer != null) {
            timer.cancel();
        }
    }

    public void shapeDetectedCallback(int[] descriptors){
        if (!m_opened)
        {
            Log.i(LOG_TAG, "Device in Java not initialized");
            return;
        }
        if(game == null)
        {
            Log.i(LOG_TAG, "Game is null");
            return;
        }
        // Not need instance check because all of them is half virtual for now.
        if(game.level == 0){
            if(game.state == GameState.OBJECT_PLACEMENT) {
                final boolean allObjectsPlaced = game.processBlobDescriptors(descriptors);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        overlayImView.setImageBitmap(bmpOverlay);
                        if (allObjectsPlaced) {
                            mediaPlayer = MediaPlayer.create(MainActivity.this, R.raw.ana_naratif2_kisa);
                            mediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                                @Override
                                public void onCompletion(MediaPlayer mediaPlayer) {
                                    Log.d(LOG_TAG, "ana_naratif2 finish");
                                    mediaPlayer.release();
                                    MediaPlayer mediaPlayer2 = MediaPlayer.create(MainActivity.this, game.getQuestion());
                                    mediaPlayer2.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                                        @Override
                                        public void onCompletion(MediaPlayer mediaPlayer) {
                                            mediaPlayer.release();
                                            Log.d(LOG_TAG, "soru_fazla finish");
                                            game.state = GameState.ASSESMENT_RUNNING;
                                            game.startTime = System.currentTimeMillis();
                                            delayedUICommand(REMOVAL_DELAY, new Runnable() {
                                                @Override
                                                public void run() {
                                                    Log.d(LOG_TAG, "object removed");
                                                    ((GameHalfVirtual)game).removeObjects();
                                                    overlayImView.setImageBitmap(bmpOverlay);
                                                }
                                            });
                                        }
                                    });
                                    mediaPlayer2.start();

                                }
                            });
                            mediaPlayer.start();
                        }
                    }
                });

            }
            else if(game.state == GameState.ASSESMENT_RUNNING) {
                final int correctAnswer = game.processGestureDescriptors(descriptors);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (correctAnswer == 1) {
                            StopCaptureNative();
                            playSound(sApplause, sCong);
                            overlayImView.setImageDrawable(null);
                            addKonfetti("burst");
                            float secs = (float) game.assestmentTime / 1000;
                            String time = String.format("Solved in %.3f seconds with %d wrong", secs, wrong);
                            tvDebug.setText(time);
                        } else if (correctAnswer == -1) {
                            playSound(sWrong);
                        }
                    }
                });

                if(correctAnswer == 1){
                    mediaPlayer = MediaPlayer.create(this, R.raw.taslari_al);
                    mediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                        @Override
                        public void onCompletion(MediaPlayer mediaPlayer) {
                            mediaPlayer.release();
                            MediaPlayer mediaPlayer2 = MediaPlayer.create(MainActivity.this, R.raw.next_game);
                            mediaPlayer2.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                                @Override
                                public void onCompletion(MediaPlayer mediaPlayer) {
                                    mediaPlayer.release();
                                    runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            findViewById(R.id.button1).performClick();
                                        }
                                    });
                                }
                            });
                            sleep(4000);
                            mediaPlayer2.start();
                        }
                    });
                    sleep(3000);
                    mediaPlayer.start();
                }
            }
        }
        else{
            if(game.state == GameState.OBJECT_PLACEMENT) {
                final boolean allObjectsPlaced =game.processBlobDescriptors(descriptors);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        overlayImView.setImageBitmap(bmpOverlay);
                        if(allObjectsPlaced){
                            tvDebug.setText("Assesment has started");
                            mediaPlayer = MediaPlayer.create(MainActivity.this, game.getQuestion());
                            mediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                                @Override
                                public void onCompletion(MediaPlayer mediaPlayer) {
                                    mediaPlayer.release();
                                    Log.d(LOG_TAG, "soru finish");
                                    game.state = GameState.ASSESMENT_RUNNING;
                                    game.startTime = System.currentTimeMillis();
                                    Log.d(LOG_TAG, "Assesment has started");
                                    delayedUICommand(2500, new Runnable() { //TODO for 3 year
                                        @Override
                                        public void run() {
                                            Log.d(LOG_TAG, "object removed");
                                            ((GameHalfVirtual)game).removeObjects();
                                            overlayImView.setImageBitmap(bmpOverlay);
                                        }
                                    });
                                }
                            });
                            mediaPlayer.start();
                        }
                    }
                });
            }

            else if(game.state == GameState.ASSESMENT_RUNNING) {
                final int correctAnswer = game.processGestureDescriptors(descriptors);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (correctAnswer == 1) {
                            StopCaptureNative();
                            playSound(sApplause, sCong);
                            overlayImView.setImageDrawable(null);
                            addKonfetti("burst");
                            float secs = (float) game.assestmentTime / 1000;
                            String time = String.format("Solved in %.3f seconds with %d wrong", secs, wrong);
                            tvDebug.setText(time);
                        } else if (correctAnswer == -1) {
                            playSound(sWrong);
                        }
                    }
                });

                if(correctAnswer == 1){
                    if(game.level == 6){
                        // ALL LEVELS ARE FINISHED
                        delayedUICommand(4000, new Runnable() {
                            @Override
                            public void run() {
                                // TODO write reults to txt and textview
                                tvResult.setText("Finished");
                                TransitionManager.beginDelayedTransition(mainLayout,slide);
                                tvResult.setVisibility(View.VISIBLE);
                            }
                        });
                    }
                    else {
                        mediaPlayer = MediaPlayer.create(this, R.raw.taslari_al);
                        mediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                            @Override
                            public void onCompletion(MediaPlayer mediaPlayer) {
                                mediaPlayer.release();
                                MediaPlayer mediaPlayer2 = MediaPlayer.create(MainActivity.this, R.raw.next_game);
                                mediaPlayer2.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                                    @Override
                                    public void onCompletion(MediaPlayer mediaPlayer) {
                                        mediaPlayer.release();
                                        runOnUiThread(new Runnable() {
                                            @Override
                                            public void run() {
                                                // start next level
                                                initializeTestMode(game.level + 1);
                                            }
                                        });

                                    }
                                });
                                sleep(4000);
                                mediaPlayer2.start();
                            }
                        });
                        sleep(3000);
                        mediaPlayer.start();
                    }
                }
            }
        }

    }

    private void playSound(int id){
        playSound(id,0);
    }

    private void playSound(int id, int id2){
        if(!isPlaying && soundsLoaded){
            isPlaying = true;
            soundPool.play(id, 1f, 1f, 1, 0, 1f);
            if(id2 != 0) soundPool.play(id2, 1f, 1f, 1, 0, 1f);

            if(id == sWrong) wrong++;

            new Thread(new Runnable() {
                @Override
                public void run() {
                    sleep(2000);
                    isPlaying = false;
                }
            }).start();
        }
    }

    private void playMedia(int raw_id){
        playMedia(raw_id, 0);
    }

    private void playMedia(int raw_id, int delay){
        mediaPlayer = MediaPlayer.create(MainActivity.this, raw_id);
        mediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mediaPlayer) {
                mediaPlayer.release();
            }
        });
        if(delay > 0) sleep(delay);
        mediaPlayer.start();
    }

    private void showLevelInfo(String info){
        tvLevel.setText(info);
        TransitionManager.beginDelayedTransition(mainLayout,slide);
        tvLevel.setVisibility(View.VISIBLE);
        delayedUICommand(3000, new Runnable() {
            @Override
            public void run() {
                TransitionManager.beginDelayedTransition(mainLayout,slide);
                tvLevel.setVisibility(View.GONE);
            }
        });
    }

    private void sleep(long millis){
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void delayedUICommand(long delay, final Runnable r){
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                runOnUiThread(r);
            }
        }, delay);
    }

    private int getButtonId(int level){
        int id = -1;
        switch (level){
            case 0:
                id = R.id.buttonIntro;
                break;
            case 1:
                id = R.id.button1;
                break;
            case 2:
                id = R.id.button2;
                break;
            case 3:
                id = R.id.button3;
                break;
            case 4:
                id = R.id.button4;
                break;
            case 5:
                id = R.id.button5;
                break;
            case 6:
                id = R.id.button6;
                break;
        }
        return id;
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

