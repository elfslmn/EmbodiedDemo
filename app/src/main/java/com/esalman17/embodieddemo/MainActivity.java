package com.esalman17.embodieddemo;

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
    public static int REMOVAL_DELAY = 2500; //TODO for 3 year
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
                    touch_descriptors.add(0); // 0 means it is a retro blob
                    Log.d(LOG_TAG,"RETRO x="+downx +" y="+downy +" dist=" + (Math.abs(downx - (int) event.getX()) + Math.abs(downy - (int) event.getY())) );
                }
                else{
                    touch_descriptors.clear();
                    touch_descriptors.add(downx);
                    touch_descriptors.add(downy);
                    touch_descriptors.add(1); // 1 means it is a gesture blob
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
        overlayImView.setOnTouchListener(touchListener);
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
        tvInfo.setText("Level "+test);

        timer = new Timer(false);
        wrong = 0;

        if(test == 0){ // PILOT LEVEL

        }
        else if(test == 1){
            game = new GameBothReal(this, test);
            game.setBackground(mainImView, R.drawable.task_a1);
            overlayImView.setImageBitmap(bmpOverlay); // bmpOverlay is initalized in game constructor */
            showLevelInfo("LEVEL " + test);

        }
    }
    private void endTestMode(){
        mainImView.setImageBitmap(bmpCam);
        overlayImView.setImageDrawable(null);
        overlayImView.setVisibility(View.GONE);
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
        // Not need instance check because all of them is half virtual for now.

        if(game.level == 0){ // ------------------ PILOT ------------------------------------------------

        }
        else if(game.level == 1){  //------------------------ OTHER LEVELS -----------------------------------------------------------
            if(game.state == GameState.OBJECT_PLACEMENT || game.state == GameState.LEFT_PLACED) {
                final boolean allObjectsPlaced =game.processBlobDescriptors(descriptors);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        overlayImView.setImageBitmap(bmpOverlay);
                        if(allObjectsPlaced) {
                            if(game.state == GameState.LEFT_PLACED){
                                Log.d( "Level-1", "All left objects are placed");
                                // TODO momoyu karşıya gecir.
                            }
                            else if(game.state == GameState.ALL_PLACED){
                                StopCaptureNative();
                                touch_descriptors.clear();
                                Log.d( "Level-1", "Both side objects are placed");
                                // TODO Ask question and start assesment after audio finished
                                // For debug directly start the assesmnet
                                game.state = GameState.ASSESMENT_RUNNING;
                                game.startTime = System.currentTimeMillis();
                                Log.d(LOG_TAG, "Assesment has started");
                            }

                        }
                    }
                });
            }
            else if(game.state == GameState.ASSESMENT_RUNNING) {
                final int correctAnswer = game.processGestureDescriptors(descriptors);
                if (correctAnswer == 1) {
                    float secs = (float) game.assestmentTime / 1000;
                    String time = String.format("Solved in %.3f seconds with %d wrong", secs, wrong);
                    results[game.level] = time;
                }
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (correctAnswer == 1) {
                            //soundPool.stop(sBackPlayId);
                            playSound(sApplause, sCong);
                            overlayImView.setImageDrawable(null);
                            tvDebug.setText(results[game.level]);
                        } else if (correctAnswer == -1) {
                            playSound(sWrong);
                        }
                    }
                });
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


}

