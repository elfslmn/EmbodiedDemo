package com.esalman17.embodieddemo;

import android.animation.Animator;
import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.SoundPool;
import android.os.Bundle;
import android.os.Environment;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.TranslateAnimation;
import android.view.inputmethod.InputMethodManager;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageView;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.Switch;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;

import com.airbnb.lottie.LottieAnimationView;
import com.transitionseverywhere.Slide;
import com.transitionseverywhere.TransitionManager;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class MainActivity extends Activity {
    public static int REMOVAL_DELAY = 2500;
    public static String CHILD_NAME = "Elif";
    public static int CHILD_AGE = 3;
    SimpleDateFormat parser = new SimpleDateFormat("d_MMM_HH_mm");

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
    LinearLayout infoLayout;
    ImageView mainImView, overlayImView;
    LottieAnimationView momoView, confettiView;
    TextView tvDebug, tvLevel, tvResult, tvInfo;
    EditText etName, etAge;
    Switch touch_switch;

    Slide slide = new Slide();

    boolean m_opened;
    boolean touch_mode = false;
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

    View.OnClickListener levelListener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            clearPreviousMode(); // TODO kaldır ??
            switch (view.getId()){
                case R.id.buttonIntro:
                    initializeTestMode(-1);
                    break;
                case R.id.buttonPilot:
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

    View.OnFocusChangeListener focusChangeListener =  new View.OnFocusChangeListener(){
        @Override
        public void onFocusChange(View view, boolean hasFocus) {
            if (!hasFocus) {
                InputMethodManager inputMethodManager =(InputMethodManager)getSystemService(Activity.INPUT_METHOD_SERVICE);
                inputMethodManager.hideSoftInputFromWindow(view.getWindowToken(), 0);
            }
        }
    };

    public static int[] convertIntegers(List<Integer> integers)
    {
        int[] ret = new int[integers.size()];
        Iterator<Integer> iterator = integers.iterator();
        for (int i = 0; i < ret.length; i++)
        {
            ret[i] = iterator.next().intValue();
        }
        return ret;
    }

    ArrayList<Integer> touch_descriptors = new ArrayList<>(30);
    int downx, downy;
    View.OnTouchListener touchListener = new View.OnTouchListener() {
        @Override
        public boolean onTouch(View view, MotionEvent event) {
            if(event.getAction() == MotionEvent.ACTION_DOWN){
                downx = (int) event.getX();
                downy = (int) event.getY();
            }
            else if(event.getAction() == MotionEvent.ACTION_UP){
                if((Math.abs(downx - (int) event.getX()) + Math.abs(downy - (int) event.getY())) < 100 ){
                    touch_descriptors.add(downx);
                    touch_descriptors.add(downy);
                    touch_descriptors.add(1); // 1 means it is a retro blob
                    Log.d(LOG_TAG,"RETRO x="+downx +" y="+downy +" dist=" + (Math.abs(downx - (int) event.getX()) + Math.abs(downy - (int) event.getY())) );
                }
                else{
                    touch_descriptors.clear();
                    touch_descriptors.add(downx);
                    touch_descriptors.add(downy);
                    touch_descriptors.add(-1); // 0 means it is a gesture blob
                    Log.d(LOG_TAG,"GESTURE x="+downx +" y="+downy+" dist=" + (Math.abs(downx - (int) event.getX()) + Math.abs(downy - (int) event.getY())));
                }
                //Log.d(LOG_TAG, "touch_descriptors "+touch_descriptors.size());
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        shapeDetectedCallback(convertIntegers(touch_descriptors));
                    }
                }).start();
            }
            return true;
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
        infoLayout = findViewById(R.id.layout_info);
        infoLayout.setVisibility(View.GONE); // TODO for debug

        mainImView =  findViewById(R.id.imageViewMain);
        overlayImView = findViewById(R.id.imageViewOverlay);
        momoView = findViewById(R.id.momoView);
        confettiView = findViewById(R.id.confettiView);
        tvDebug = findViewById(R.id.textViewDebug);
        tvLevel = findViewById(R.id.textViewLevel);
        tvResult = findViewById(R.id.textViewResults);
        tvInfo = findViewById(R.id.textViewInfo);

        etName = findViewById(R.id.etName);
        etName.setOnFocusChangeListener(focusChangeListener);
        etAge = findViewById(R.id.etAge);
        etAge.setOnFocusChangeListener(focusChangeListener);

        slide.setSlideEdge(Gravity.BOTTOM);
        slide.addTarget(tvLevel);
        slide.addTarget(tvResult);
        slide.addTarget(infoLayout);
        slide.setDuration(500);

        touch_switch = findViewById(R.id.switch_touch);
        touch_switch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean isChecked) {
                if(isChecked){
                    StopCaptureNative();
                    touch_mode = true;
                    overlayImView.setOnTouchListener(touchListener);
                }
                else{
                    touch_mode = false;
                    touch_descriptors.clear();
                    overlayImView.setOnTouchListener(null);
                }
            }
        });

        findViewById(R.id.buttonSubmit).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String name = etName.getText().toString();
                if(TextUtils.isEmpty(name)){
                    Toast.makeText(MainActivity.this, "İsim girin", Toast.LENGTH_LONG).show();
                    return;
                }
                else{
                    CHILD_NAME = name;
                }

                String age = etAge.getText().toString();
                if(TextUtils.isEmpty(age)){
                    Toast.makeText(MainActivity.this, "Yaş girin", Toast.LENGTH_LONG).show();
                    return;
                }
                else{
                    try {
                        CHILD_AGE = Integer.parseInt(age.trim());
                        if (CHILD_AGE <= 3) REMOVAL_DELAY = 2500;
                        else REMOVAL_DELAY = 1250;
                    }
                    catch (NumberFormatException e){
                        e.printStackTrace();
                        Toast.makeText(MainActivity.this, "Yaşı rakamla girin", Toast.LENGTH_LONG).show();
                        return;
                    }
                }
                TransitionManager.beginDelayedTransition(mainLayout,slide);
                infoLayout.setVisibility(View.GONE);
                mainLayout.setClickable(false);
                mainLayout.setFocusableInTouchMode(false);
            }
        });


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

        findViewById(R.id.buttonCorrect).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(game == null) {
                    Log.i(LOG_TAG, "Game is null");
                    return;
                }
                game.state = GameState.ASSESMENT_FINISHED;
                momoView.clearAnimation();
                momoView.setVisibility(View.GONE);
                playSound(sApplause, sCong);
                overlayImView.setImageDrawable(null);
                confettiView.setAnimation("trophy.json");
                confettiView.playAnimation();
                playMedia(R.raw.taslari_al,3000); // Delay for confetti complete

                float secs = (float) game.assestmentTime / 1000;
                String time = String.format("Solved in %.3f seconds with %d wrong", secs, wrong);
                results[game.level] = time;
                tvDebug.setText(results[game.level]);
            }
        });

        findViewById(R.id.buttonWrong).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(game == null) {
                    Log.i(LOG_TAG, "Game is null");
                    return;
                }
                game.state = GameState.ASSESMENT_FINISHED;
                float secs = (float) game.assestmentTime / 1000;
                String time = String.format("Cannot solved in %.3f seconds with %d wrong", secs, wrong);
                results[game.level] = time;
                tvDebug.setText(results[game.level]);
                overlayImView.setImageDrawable(null);

                playMedia(R.raw.taslari_al);
                game.state = GameState.STONES_CLEARED;
            }
        });

        findViewById(R.id.buttonContinue).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // TODO make continue to introduction
                if(game == null) {
                    Log.i(LOG_TAG, "Game is null");
                    return;
                }
                switch (game.state){
                    case ASSESMENT_FINISHED:
                        game.state = GameState.STONES_CLEARED;
                        confettiView.setAnimation("cak_bakalim.json");
                        confettiView.playAnimation();
                        playMedia(R.raw.cak_bakalim);
                        break;
                    case STONES_CLEARED:
                        game.state = GameState.HIGH_FIVED;
                        int next_level = game.level +1;
                        final int id = getButtonId(next_level);
                        if(id != -1){
                            setLevelMapAnimation(next_level);
                            confettiView.playAnimation();
                            confettiView.addAnimatorListener(new Animator.AnimatorListener() {
                                @Override
                                public void onAnimationStart(Animator animator) {
                                    playMedia(R.raw.next_game);
                                }
                                @Override
                                public void onAnimationEnd(Animator animator) {
                                    confettiView.removeAllAnimatorListeners();
                                    findViewById(id).performClick();
                                }
                                @Override
                                public void onAnimationCancel(Animator animator) {}
                                @Override
                                public void onAnimationRepeat(Animator animator) {}
                            });
                        }

                        break;

                }
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
        sWrong = soundPool.load(this, R.raw.bir_daha_dusun, 1);
        sCong = soundPool.load(this, R.raw.supersin, 1);

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
        touch_descriptors.clear();
        overlayImView.setVisibility(View.VISIBLE);
        momoView.setVisibility(View.VISIBLE);
        confettiView.setVisibility(View.VISIBLE);
        tvInfo.setText("Level "+test);

        timer = new Timer(false);
        wrong = 0;

        momoView.setAnimation("rightanswer.json");
        momoView.setRepeatCount(-1);
        momoView.setSpeed(2.0f);

        if(test == 0){ // PILOT LEVEL
            pause = true;
            game = new GameHalfVirtual(this, test);
            game.setBackground(mainImView, R.drawable.task_a0);
            overlayImView.setImageBitmap(bmpOverlay);
            setInitialPosition(momoView, -30,400,0,0);

            mediaPlayer = MediaPlayer.create(MainActivity.this, R.raw.karsiya_yol_var);
            mediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override
                public void onCompletion(MediaPlayer mediaPlayer) {
                    mediaPlayer.release();
                    Animation walking = new TranslateAnimation(0, 600,-50, -20);
                    walking.setDuration(2500);
                    walking.setFillAfter(true);
                    momoView.startAnimation(walking);
                    momoView.playAnimation();
                    walking.setAnimationListener(new Animation.AnimationListener() {
                        @Override
                        public void onAnimationStart(Animation animation) {
                            playMedia(R.raw.hop_gectim,1000);
                        }
                        @Override
                        public void onAnimationEnd(Animation animation) {
                            momoView.pauseAnimation();
                            game.setState(GameState.LEFT_PLACED);
                            pause = false;
                            playMedia(R.raw.tas_koy_pilot);
                        }
                        @Override
                        public void onAnimationRepeat(Animation animation) {}
                    });
                }
            });
            mediaPlayer.start();
        }
        else if(test == 1 || test == 2){
            game = new GameBothReal(this, test);
            if(test == 1) game.setBackground(mainImView, R.drawable.task_a1);
            else game.setBackground(mainImView, R.drawable.task_a2);
            overlayImView.setImageBitmap(bmpOverlay); // bmpOverlay is initalized in game constructor */
            setInitialPosition(momoView, -30,400,0,0);
            playMedia(R.raw.tas_koy_task_a, 2000);
        }
        else if(test == 3){
            game = new GameStacking(this, test);
            game.setBackground(mainImView, R.drawable.task_b3);
            overlayImView.setImageBitmap(bmpOverlay); // bmpOverlay is initalized in game constructor */
            setInitialPosition(momoView, -30,400,0,0);
        }
        else if(test == 4){
            game = new GameStacking(this, test);
            game.setBackground(mainImView, R.drawable.task_b4);
            overlayImView.setImageBitmap(bmpOverlay); // bmpOverlay is initalized in game constructor */
            setInitialPosition(momoView, -50,400,0,0);
        }
        else if(test == 5 || test == 6){
            game = new GameDrag(this, test);
            game.setBackground(mainImView, R.drawable.task_c2);
            overlayImView.setImageBitmap(bmpOverlay); // bmpOverlay is initalized in game constructor */
            setInitialPosition(momoView, -30,40,0,0);
        }
        if(game != null && game.level != 0) showLevelInfo("LEVEL " + test);
    }
    private void endTestMode(){
        mainImView.setImageBitmap(bmpCam);
        overlayImView.setImageDrawable(null);
        overlayImView.setVisibility(View.GONE);
        momoView.setImageDrawable(null);
        momoView.clearAnimation();
        momoView.setVisibility(View.GONE);
        confettiView.setImageDrawable(null);
        confettiView.setVisibility(View.GONE);
        tvResult.setVisibility(View.GONE);
        tvInfo.setText(null);
        soundPool.stop(sBackPlayId);
        if(mediaPlayer != null){
            mediaPlayer.release();
        }
        if(timer != null) {
            timer.cancel();
        }
    }
    boolean pause = false;
    public void shapeDetectedCallback(final int[] descriptors){
        //Log.d(LOG_TAG, "shapeDetectedCallback" );
        if (!touch_mode && !m_opened)
        {
            Log.i(LOG_TAG, "Device in Java not initialized");
            return;
        }
        if(game == null)
        {
            Log.i(LOG_TAG, "Game is null");
            return;
        }
        if(pause){
            Log.i(LOG_TAG, "Game is paused");
            return;
        }

        if(game.level == 0){ // ------------------ PILOT ------------------------------------------------
            if(game.state == GameState.OBJECT_PLACEMENT || game.state == GameState.LEFT_PLACED){
                final boolean objectsPlaced =game.processBlobDescriptors(descriptors);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        overlayImView.setImageBitmap(bmpOverlay);
                        if(objectsPlaced) {
                            Animation walking = new TranslateAnimation(600, 1100,-50, -20);
                            walking.setDuration(2500);
                            walking.setFillAfter(true);
                            momoView.playAnimation();
                            momoView.startAnimation(walking);
                            walking.setAnimationListener(new Animation.AnimationListener() {
                                @Override
                                public void onAnimationStart(Animation animation) {
                                    playMedia(R.raw.hop_gectim,700);
                                }
                                @Override
                                public void onAnimationEnd(Animation animation) {
                                    momoView.pauseAnimation();
                                    game.drawRects();
                                    runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            overlayImView.setImageBitmap(bmpOverlay);
                                            mainImView.setImageDrawable(null);
                                        }});
                                    mediaPlayer = MediaPlayer.create(MainActivity.this, R.raw.soru_cok);
                                    mediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                                        @Override
                                        public void onCompletion(MediaPlayer mediaPlayer) {
                                            game.state = GameState.ASSESMENT_RUNNING;
                                            game.startTime = System.currentTimeMillis();
                                            Log.d(LOG_TAG, "Assesment has started");
                                        }
                                    });
                                    mediaPlayer.start();
                                }
                                @Override
                                public void onAnimationRepeat(Animation animation) {}
                            });
                        }
                    }
                });
            }
            else if(game.state == GameState.ASSESMENT_RUNNING) {
                final int answer = game.processGestureDescriptors(descriptors);
                processAnswer(answer);
            }
        }
        else if(game.level == 1 || game.level == 2){  //--------------- LEVEL 1-2 -----------------------------------------------------------
            if(game.state == GameState.OBJECT_PLACEMENT || game.state == GameState.LEFT_PLACED || game.state == GameState.RIGHT_PLACED) {
                final boolean objectsPlaced =game.processBlobDescriptors(descriptors);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        overlayImView.setImageBitmap(bmpOverlay);
                        if(objectsPlaced) {
                            if(game.state == GameState.LEFT_PLACED){
                                pause = true;
                                Log.d( "Level-"+game.level, "All left objects are placed");

                                Animation walking = null;
                                if(game.level == 1) walking = new TranslateAnimation(0, 400,-50, -20);
                                else if(game.level == 2) walking = new TranslateAnimation(0, 600,-100, -100);
                                playMedia(R.raw.hop_gectim,1000);
                                walking.setDuration(2500);
                                walking.setStartOffset(200);
                                walking.setFillAfter(true);
                                momoView.playAnimation();
                                momoView.startAnimation(walking);
                                walking.setAnimationListener(new Animation.AnimationListener() {
                                    @Override
                                    public void onAnimationStart(Animation animation) {}

                                    @Override
                                    public void onAnimationEnd(Animation animation) {
                                        momoView.pauseAnimation();
                                        if(game.level == 2){
                                            Animation down = new TranslateAnimation(600, 600,-100, 0);
                                            down.setDuration(500);
                                            down.setFillAfter(true);
                                            momoView.startAnimation(down);
                                            sleep(500);
                                        }
                                        playMedia(R.raw.diger_koy);
                                        sleep(1000);
                                        pause = false;
                                    }
                                    @Override
                                    public void onAnimationRepeat(Animation animation) {}
                                });
                            }
                            else if(game.state == GameState.RIGHT_PLACED){
                                pause = true;
                                Log.d( "Level-"+game.level, "Right objects are placed");
                                Animation walking = null;
                                if(game.level == 1) walking = new TranslateAnimation(400, 700,-50, -50);
                                else if(game.level == 2) walking = new TranslateAnimation(600, 900,0, -450);
                                walking.setDuration(3000);
                                walking.setStartOffset(200);
                                walking.setFillAfter(true);
                                momoView.playAnimation();
                                momoView.startAnimation(walking);
                                walking.setAnimationListener(new Animation.AnimationListener() {
                                    @Override
                                    public void onAnimationStart(Animation animation) {}
                                    @Override
                                    public void onAnimationEnd(Animation animation) {
                                        momoView.pauseAnimation();
                                        Animation back = null;
                                        if(game.level == 1) back = new TranslateAnimation(700, 400,-50, -50);
                                        else if(game.level == 2) back = new TranslateAnimation(900, 600,-450, 0);
                                        back.setDuration(2000);
                                        back.setStartOffset(200);
                                        back.setFillAfter(true);
                                        momoView.startAnimation(back);
                                        back.setAnimationListener(new Animation.AnimationListener() {
                                            @Override
                                            public void onAnimationStart(Animation animation) {}
                                            @Override
                                            public void onAnimationEnd(Animation animation) {
                                                playMedia(R.raw.gecemem);
                                                ((GameBothReal)game).changePoints();
                                                sleep(4000); // to wait media end
                                                pause = false;
                                            }
                                            @Override
                                            public void onAnimationRepeat(Animation animation) {}
                                        });
                                    }
                                    @Override
                                    public void onAnimationRepeat(Animation animation) {}
                                });
                            }
                            else if(game.state == GameState.ALL_PLACED){
                                if(touch_mode) StopCaptureNative(); // if place with camera but mock gesture
                                touch_descriptors.clear();
                                pause = true;
                                Log.d( "Level-"+game.level, "All objects are placed");

                                Animation walking = null;
                                if(game.level == 1){
                                    walking = new TranslateAnimation(350, 1100,-50, -50);
                                    walking.setDuration(4000);
                                }
                                else if(game.level == 2){
                                    walking = new TranslateAnimation(600, 850,0, -280);
                                    walking.setDuration(2000);
                                }
                                walking.setStartOffset(200);
                                walking.setFillAfter(true);
                                playMedia(R.raw.hop_gectim,1500);
                                momoView.playAnimation();
                                momoView.startAnimation(walking);
                                walking.setAnimationListener(new Animation.AnimationListener() {
                                    @Override
                                    public void onAnimationStart(Animation animation) {}

                                    @Override
                                    public void onAnimationEnd(Animation animation) {
                                        if(game.level == 1){
                                            momoView.pauseAnimation();
                                            askQuestion();
                                        }
                                        else if(game.level == 2){
                                            Animation down = new TranslateAnimation(850, 1100,-280, 0);
                                            down.setDuration(2000);
                                            down.setFillAfter(true);
                                            momoView.startAnimation(down);
                                            down.setAnimationListener(new Animation.AnimationListener() {
                                                @Override
                                                public void onAnimationStart(Animation animation) {}
                                                @Override
                                                public void onAnimationEnd(Animation animation) {
                                                    momoView.pauseAnimation();
                                                    askQuestion();
                                                }
                                                @Override
                                                public void onAnimationRepeat(Animation animation) {}
                                            });
                                        }
                                    }
                                    @Override
                                    public void onAnimationRepeat(Animation animation) {}
                                });
                            }

                        }
                    }
                });
            }
            else if(game.state == GameState.ASSESMENT_RUNNING) {
                final int correctAnswer = game.processGestureDescriptors(descriptors);
                processAnswer(correctAnswer);
            }
        }

        else if(game.level == 3 || game.level == 4){ //------------- LEVEL 3-4 -------------------------------------------
            if(game.state == GameState.OBJECT_PLACEMENT || game.state == GameState.LEFT_PLACED) {
                final boolean objectsPlaced = game.processBlobDescriptors(descriptors);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        overlayImView.setImageBitmap(bmpOverlay);
                        if(objectsPlaced) {
                            if(game.state == GameState.LEFT_PLACED){
                                Log.d( "Level-3", "All left objects are placed");
                                final Animation walking = new TranslateAnimation(0, 230,0, -20);
                                walking.setStartOffset(800);
                                walking.setDuration(1000);
                                walking.setFillAfter(true);
                                momoView.startAnimation(walking);
                                walking.setAnimationListener(new Animation.AnimationListener() {
                                    @Override
                                    public void onAnimationStart(Animation animation) {}
                                    @Override
                                    public void onAnimationEnd(Animation animation) {
                                        Animation walking2 = new TranslateAnimation(230, 500,-20, -50);
                                        walking2.setStartOffset(1000);
                                        walking2.setDuration(1000);
                                        walking2.setFillAfter(true);
                                        momoView.startAnimation(walking2);
                                    }
                                    @Override
                                    public void onAnimationRepeat(Animation animation) {}
                                });
                            }
                            else if(game.state == GameState.ALL_PLACED) {
                                if (touch_mode) StopCaptureNative(); // if place with camera but mock gesture
                                touch_descriptors.clear();
                                Log.d( "Level-3", "Both side objects are placed");

                                Animation walking = null ;
                                if(game.level == 3) walking = new TranslateAnimation(500, 1150,-50, -70);
                                else walking = new TranslateAnimation(500, 1150,-120, -120);
                                walking.setDuration(3000);
                                walking.setStartOffset(200);
                                walking.setFillAfter(true);
                                momoView.playAnimation();
                                momoView.startAnimation(walking);
                                walking.setAnimationListener(new Animation.AnimationListener() {
                                    @Override
                                    public void onAnimationStart(Animation animation) {}
                                    @Override
                                    public void onAnimationEnd(Animation animation) {
                                        momoView.pauseAnimation();
                                        game.drawRects();
                                        runOnUiThread(new Runnable() {
                                            @Override
                                            public void run() {
                                                overlayImView.setImageBitmap(bmpOverlay);
                                                mainImView.setImageDrawable(null);
                                            }});

                                        // TODO Ask question and start assesment after audio finished
                                        // For debug directly start the assesmnet
                                        game.state = GameState.ASSESMENT_RUNNING;
                                        game.startTime = System.currentTimeMillis();
                                        Log.d(LOG_TAG, "Assesment has started");
                                    }
                                    @Override
                                    public void onAnimationRepeat(Animation animation) {}
                                });



                            }
                        }
                    }
                });
            }
            else if(game.state == GameState.ASSESMENT_RUNNING) {
                final int correctAnswer = game.processGestureDescriptors(descriptors);
                processAnswer(correctAnswer);
            }
        }
        else if(game.level == 5 || game.level == 6) { //------------------------ LEVEL 5-6 ---------------------------------------
            if(game.state == GameState.OBJECT_PLACEMENT ) {
                final boolean objectsPlaced = game.processBlobDescriptors(descriptors);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        overlayImView.setImageBitmap(bmpOverlay);
                        if(objectsPlaced) {
                            Log.d( "Level-5", "All left objects are placed");
                            final Animation walking = new TranslateAnimation(0, 1000,0, 0);
                            walking.setDuration(2000);
                            walking.setFillAfter(true);
                            momoView.playAnimation();
                            momoView.startAnimation(walking);
                            walking.setAnimationListener(new Animation.AnimationListener() {
                                @Override
                                public void onAnimationStart(Animation animation) {}
                                @Override
                                public void onAnimationEnd(Animation animation) {
                                    momoView.pauseAnimation();
                                    // TODO sound: dikkatlice bak ( taskc-9)
                                    ((GameDrag)game).drawVirtualObjects();
                                    game.drawRects();
                                    runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            overlayImView.setImageBitmap(bmpOverlay);
                                            mainImView.setImageDrawable(null);
                                        }
                                    });
                                    // TODO bekle, tasları sondur, sonra soruyu sor
                                    game.state = GameState.ASSESMENT_RUNNING;
                                    game.startTime = System.currentTimeMillis();
                                    Log.d(LOG_TAG, "Assesment has started");

                                }
                                @Override
                                public void onAnimationRepeat(Animation animation) {}
                            });
                        }
                    }
                });

            }
            else if(game.state == GameState.ASSESMENT_RUNNING){
                final int correctAnswer = game.processGestureDescriptors(descriptors);
                processAnswer(correctAnswer);
            }

        }

    }

    private void playSound(int id){
        playSound(id,0);
    }

    private void playSound(int id, int id2){
        if(!isPlaying && soundsLoaded){
            if(id == sWrong){
                wrong++;
                if(wrong == 5) return;
            }
            isPlaying = true;
            soundPool.play(id, 1f, 1f, 1, 0, 1f);

            if(id2 != 0) soundPool.play(id2, 1f, 1f, 1, 0, 1f);

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
        if(delay > 0){
            timer.schedule(new TimerTask() {
                @Override
                public void run() {
                    mediaPlayer.start();
                }
            }, delay);
        }
        else{
            mediaPlayer.start();
        }

    }

    private void showLevelInfo(String info){
        tvLevel.setText(info);
        TransitionManager.beginDelayedTransition(mainLayout,slide);
        tvLevel.setVisibility(View.VISIBLE);
        delayedUICommand(2000, new Runnable() {
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
                id = R.id.buttonPilot;
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

    private void setLevelMapAnimation(int nextlevel){
        switch (nextlevel){
            case 0:
                confettiView.setAnimation("L1.json");
                confettiView.setMaxProgress(0.9f);
                break;
            case 1:
                confettiView.setAnimation("L2.json");
                break;
            case 3:
                confettiView.setAnimation("L3.json");
                confettiView.setMaxProgress(0.9f);
                break;
            case 4:
                confettiView.setAnimation("L4.json");
                break;
            case 5:
                confettiView.setAnimation("L5.json");
                break;
            case 6:
                confettiView.setAnimation("L6.json");
                confettiView.setMaxProgress(0.9f);
                break;
            case 7:
                confettiView.setAnimation("Bitis.json");
                break;
            default:
                return;

        }
        confettiView.setSpeed(0.5f);
    }


    private boolean saveResults(String childName, String result){
        File path = new File(Environment.getExternalStorageDirectory(), "Embodied/Study1");
        if(!path.exists() || !path.isDirectory()){
            path.mkdirs();
        }
        File file = new File(path, childName+"_" + parser.format(new Date())+ ".txt");
        try {
            FileOutputStream out = new FileOutputStream(file);
            out.write(result.getBytes());
            out.flush();
            out.close();
            Log.d(LOG_TAG, "Results are saved into "+ file.getName());
        }
        catch (Exception e) {
            e.printStackTrace();
            return false;
        }

        return true;
    }

    private void processAnswer(final int answer){
        if (answer == 1) {
            float secs = (float) game.assestmentTime / 1000;
            String time = String.format("Solved in %.3f seconds with %d wrong", secs, wrong);
            results[game.level] = time;
        }
        else if(wrong == 5){
            float secs = (float) game.assestmentTime / 1000;
            String time = String.format("Cannot solved in %.3f seconds with %d wrong", secs, wrong);
            results[game.level] = time;
        }
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (answer == 1 || wrong == 5) {
                    if(!touch_mode) StopCaptureNative();
                   /* playSound(sApplause, sCong);
                    confettiView.setAnimation("trophy.json");
                    confettiView.playAnimation();
                    overlayImView.setImageDrawable(null); */
                    playMedia(R.raw.neden_sence);
                }
                else if (answer == -1) {
                    playSound(sWrong);
                }
            }
        });
    }

    private void setInitialPosition(View view, int left, int top, int right, int bottom){
        RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) view.getLayoutParams();
        params.setMargins(left,top,right,bottom);
        view.setLayoutParams(params);
    }

    private void askQuestion(){
        game.drawRects();
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                overlayImView.setImageBitmap(bmpOverlay);
                mainImView.setImageDrawable(null);
            }});
        mediaPlayer = MediaPlayer.create(MainActivity.this, game.getQuestion());
        mediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mediaPlayer) {
                pause = false;
                game.state = GameState.ASSESMENT_RUNNING;
                game.startTime = System.currentTimeMillis();
                Log.d(LOG_TAG, "Assesment has started");
            }
        });
        mediaPlayer.start();
    }


}

