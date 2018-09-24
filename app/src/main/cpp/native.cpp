#include <royale/CameraManager.hpp>
#include <royale/ICameraDevice.hpp>
#include <iostream>
#include <jni.h>
#include <android/log.h>
#include <thread>
#include <chrono>
#include <mutex>
#include "opencv2/opencv.hpp"

#ifdef __cplusplus
extern "C"
{
#endif

#define DEBUG

#define LOGI(...) ((void)__android_log_print(ANDROID_LOG_INFO, "Native", __VA_ARGS__))
#define LOGE(...) ((void)__android_log_print(ANDROID_LOG_ERROR, "Native", __VA_ARGS__))

#ifdef DEBUG
#define LOGD(...) ((void)__android_log_print(ANDROID_LOG_DEBUG, "Native", __VA_ARGS__))
#else
#define LOGD(...)
#endif

using namespace royale;
using namespace std;
using namespace cv;

JavaVM *m_vm;
jmethodID m_amplitudeCallbackID, m_shapeDetectedCallbackID;
jobject m_obj;

uint16_t cam_width, cam_height;
uint16_t disp_width, disp_height;

enum Mode {UNKNOWN, CAMERA, TEST};
Mode curmode;

void setMode(int i){
    switch(i)
    {
        case 1:
            curmode = CAMERA;
            LOGD("Mode: CAMERA");
            break;
        case 2:
            curmode = TEST;
            LOGD("Mode: TEST");
            break;
        default:
            curmode = UNKNOWN;
            LOGD("Mode: UNKNOWN (%d)", i);
            break;
    }
}

const float MAX_DISTANCE = 1.0f;
const int BACKGROUND_FRAME_COUNT = 20;

// this represents the main camera device object
static std::unique_ptr<ICameraDevice> cameraDevice;

class MyListener : public IDepthDataListener
{
    Mat cameraMatrix, distortionCoefficients;
    Mat zImage;

    mutex flagMutex;
    bool back_detecting = false;
    bool detected = false;
    int count = 0;
    Mat backgrMat;
    Mat diff, diffBin;
    Mat drawing;
    float noise;

    void onNewData (const DepthData *data)
    {
        if(back_detecting)
        {
            int noisecount = 0;
            float sumNoise = 0;
            // save data as image matrix
            int k = zImage.rows * zImage.cols -1 ; // to flip screen
            for (int y = 0; y < zImage.rows; y++)
            {
                float *zRowPtr = zImage.ptr<float> (y);
                for (int x = 0; x < zImage.cols; x++, k--)
                {
                    auto curPoint = data->points.at (k);
                    if (curPoint.depthConfidence > 0)
                    {
                        zRowPtr[x] = curPoint.z < MAX_DISTANCE ? curPoint.z : MAX_DISTANCE;
                        sumNoise += curPoint.noise;
                        noisecount++;
                    }
                    else
                    {
                        zRowPtr[x] = 0;
                    }
                }
            }

            // get avarage around 20 frame
            backgrMat += zImage;
            noise += (sumNoise / noisecount);
            count++;
            if(count == BACKGROUND_FRAME_COUNT)
            {
                backgrMat /= BACKGROUND_FRAME_COUNT;
                noise /= BACKGROUND_FRAME_COUNT;
                back_detecting = false;
                detected = true;
                LOGI("Background detecting has ended. Avarage noise value is %.3f m", noise);
            }
        }
        else if (detected)
        {
            zImage = backgrMat.clone();

            // save data as image matrix
            int k = zImage.rows * zImage.cols -1 ; // to flip screen
            for (int y = 0; y < zImage.rows; y++)
            {
                float *zRowPtr = zImage.ptr<float> (y);
                for (int x = 0; x < zImage.cols; x++, k--)
                {
                    auto curPoint = data->points.at (k);
                    if (curPoint.depthConfidence > 0)
                    {
                        zRowPtr[x] = curPoint.z < MAX_DISTANCE ? curPoint.z : MAX_DISTANCE;
                    }
                }
            }

            // calculate differences between new image and background then find contours of diff blobs
            diff = backgrMat - zImage;
            //Mat temp = diff.clone();
            //undistort (temp, diff, cameraMatrix, distortionCoefficients);

            boxFilter(diff, diff, -1, Size(5,5));
            threshold(diff, diffBin, noise, 255, CV_THRESH_BINARY);
            // Find contours
            diffBin.convertTo(diffBin, CV_8UC1);
            vector<vector<Point> > contours;
            findContours(diffBin, contours, CV_RETR_EXTERNAL, CV_CHAIN_APPROX_SIMPLE, Point(0, 0));
            if(contours.size() == 0)
            {
                LOGD("No contour found");
            }

            if(curmode == CAMERA) {
                zImage.at<float>(0, 0) = MAX_DISTANCE;
                normalize(zImage, drawing, 0, 255, NORM_MINMAX, CV_8UC1);
                drawing = 255 - drawing;
                cvtColor(drawing, drawing, COLOR_GRAY2BGR);

                for (unsigned int i = 0; i < contours.size(); i++) {
                    if (contourArea(contours[i]) < 50) {
                        drawContours(drawing, contours, i, Scalar(0, 0, 255));
                        continue;
                    }

                    Moments mu = moments(contours[i], false);
                    auto center = Point2f(mu.m10 / mu.m00, mu.m01 / mu.m00);

                    if (curmode == CAMERA) {
                        drawContours(drawing, contours, i, Scalar(255, 0, 0));
                        circle(drawing, center, 2, Scalar(255, 0, 0));
                    }
                }
            }
        }

        // --------------- Fire appropriate callbacks according to current mode ----------------------
        if(curmode == CAMERA)
        {
            //  int color = (A & 0xff) << 24 | (R & 0xff) << 16 | (G & 0xff) << 8 | (B & 0xff);
            jint fill[cam_width * cam_height];
            int k = 0;
            for (int i = 0; i < drawing.rows; i++)
            {
                Vec3b *ptr = drawing.ptr<Vec3b>(i);
                for (int j = 0; j < drawing.cols; j++, k++)
                {
                    Vec3b p = ptr[j];
                    int color = (255 & 0xff) << 24 | (p[2] & 0xff) << 16 | (p[1] & 0xff) << 8 | (p[0] & 0xff);
                    fill[k] = color;
                }
            }
            // attach to the JavaVM thread and get a JNI interface pointer
            JNIEnv *env;
            m_vm->AttachCurrentThread((JNIEnv **) &env, NULL);
            jintArray intArray = env->NewIntArray(cam_width * cam_height);
            env->SetIntArrayRegion(intArray, 0, cam_width * cam_height, fill);
            env->CallVoidMethod(m_obj, m_amplitudeCallbackID, intArray);
            m_vm->DetachCurrentThread();
        }
        else if(curmode == TEST) //TEST
        {

        }

    }

public:
    void initialize(){
        zImage.create (Size (cam_width,cam_height), CV_32FC1);
        backgrMat.create (Size (cam_width,cam_height), CV_32FC1);
        backgrMat = Scalar::all (0);
        drawing = Mat::zeros(cam_height, cam_width, CV_8UC3);
        putText(drawing, "Click Backgr button",Point(30,30),FONT_HERSHEY_PLAIN ,1,Scalar(0,0,255),1);
    }

    void detectBackground(){
        LOGI("Background detecting has started.");
        back_detecting = true;
        detected = false;
        count = 0;
        noise = 0;
        backgrMat = Scalar::all (0);
        drawing = Scalar::all (0);
        putText(drawing, "Detecting background...",Point(30,30),FONT_HERSHEY_PLAIN ,1,Scalar(0,0,255),1);
    }

    void setLensParameters (LensParameters lensParameters)
    {
        // Construct the camera matrix
        // (fx   0    cx)
        // (0    fy   cy)
        // (0    0    1 )
        cameraMatrix = (Mat1d (3, 3) << lensParameters.focalLength.first, 0, lensParameters.principalPoint.first,
                0, lensParameters.focalLength.second, lensParameters.principalPoint.second,
                0, 0, 1);
        LOGI("Camera params fx fy cx cy: %f,%f,%f,%f", lensParameters.focalLength.first, lensParameters.focalLength.second,
             lensParameters.principalPoint.first, lensParameters.principalPoint.second);

        // Construct the distortion coefficients
        // k1 k2 p1 p2 k3
        distortionCoefficients = (Mat1d (1, 5) << lensParameters.distortionRadial[0],
                lensParameters.distortionRadial[1],
                lensParameters.distortionTangential.first,
                lensParameters.distortionTangential.second,
                lensParameters.distortionRadial[2]);
        LOGI("Dist coeffs k1 k2 p1 p2 k3 : %f,%f,%f,%f,%f", lensParameters.distortionRadial[0],
             lensParameters.distortionRadial[1],
             lensParameters.distortionTangential.first,
             lensParameters.distortionTangential.second,
             lensParameters.distortionRadial[2]);
    }


};

MyListener listener;

jintArray Java_com_esalman17_embodieddemo_MainActivity_OpenCameraNative (JNIEnv *env, jobject thiz, jint fd, jint vid, jint pid)
{
    // the camera manager will query for a connected camera
    {
        CameraManager manager;

        auto camlist = manager.getConnectedCameraList (fd, vid, pid);
        LOGI ("Detected %zu camera(s).", camlist.size());

        if (!camlist.empty())
        {
            cameraDevice = manager.createCamera (camlist.at (0));
        }
    }
    // the camera device is now available and CameraManager can be deallocated here

    if (cameraDevice == nullptr)
    {
        LOGI ("Cannot create the camera device");
        jintArray intArray();
    }

    // IMPORTANT: call the initialize method before working with the camera device
    CameraStatus ret = cameraDevice->initialize();
    if (ret != CameraStatus::SUCCESS)
    {
        LOGI ("Cannot initialize the camera device, CODE %d", (int) ret);
    }

    royale::Vector<royale::String> opModes;
    royale::String cameraName;
    royale::String cameraId;

    ret = cameraDevice->getUseCases (opModes);
    if (ret != CameraStatus::SUCCESS)
    {
        LOGI ("Failed to get use cases, CODE %d", (int) ret);
    }

    ret = cameraDevice->getMaxSensorWidth (cam_width);
    if (ret != CameraStatus::SUCCESS)
    {
        LOGI ("Failed to get max sensor width, CODE %d", (int) ret);
    }

    ret = cameraDevice->getMaxSensorHeight (cam_height);
    if (ret != CameraStatus::SUCCESS)
    {
        LOGI ("Failed to get max sensor height, CODE %d", (int) ret);
    }

    ret = cameraDevice->getId (cameraId);
    if (ret != CameraStatus::SUCCESS)
    {
        LOGI ("Failed to get camera ID, CODE %d", (int) ret);
    }

    ret = cameraDevice->getCameraName (cameraName);
    if (ret != CameraStatus::SUCCESS)
    {
        LOGI ("Failed to get camera name, CODE %d", (int) ret);
    }

    // display some information about the connected camera
    LOGI ("====================================");
    LOGI ("        Camera information");
    LOGI ("====================================");
    LOGI ("Id:              %s", cameraId.c_str());
    LOGI ("Type:            %s", cameraName.c_str());
    LOGI ("Width:           %d", cam_width);
    LOGI ("Height:          %d", cam_height);
    LOGI ("Operation modes: %zu", opModes.size());

    for (int i = 0; i < opModes.size(); i++)
    {
        LOGI ("    %s", opModes.at (i).c_str());
    }

    LensParameters lensParams;
    ret = cameraDevice->getLensParameters (lensParams);
    if (ret != CameraStatus::SUCCESS)
    {
        LOGE ("Failed to get lens parameters, CODE %d", (int) ret);
    }else{
        listener.setLensParameters (lensParams);
    }
    listener.initialize();

    // register a data listener
    ret = cameraDevice->registerDataListener (&listener);
    if (ret != CameraStatus::SUCCESS)
    {
        LOGI ("Failed to register data listener, CODE %d", (int) ret);
    }

    // set an operation mode
    ret = cameraDevice->setUseCase (opModes[0]);
    if (ret != CameraStatus::SUCCESS)
    {
        LOGI ("Failed to set use case, CODE %d", (int) ret);
    }

    //set exposure mode to manual
    ret = cameraDevice->setExposureMode (ExposureMode::MANUAL);
    if (ret != CameraStatus::SUCCESS)
    {
        LOGE ("Failed to set exposure mode, CODE %d", (int) ret);
    }

    //set exposure time (not working above 300)
    ret = cameraDevice->setExposureTime(250);
    if (ret != CameraStatus::SUCCESS)
    {
        LOGE ("Failed to set exposure time, CODE %d", (int) ret);
    }

    ret = cameraDevice->startCapture();
    if (ret != CameraStatus::SUCCESS)
    {
        LOGI ("Failed to start capture, CODE %d", (int) ret);
    }

    jint fill[2];
    fill[0] = cam_width;
    fill[1] = cam_height;

    jintArray intArray = env->NewIntArray (2);

    env->SetIntArrayRegion (intArray, 0, 2, fill);

    return intArray;
}

void Java_com_esalman17_embodieddemo_MainActivity_RegisterCallback (JNIEnv *env, jobject thiz)
{
    // save JavaVM globally; needed later to call Java method in the listener
    env->GetJavaVM (&m_vm);

    m_obj = env->NewGlobalRef (thiz);

    // save refs for callback
    jclass g_class = env->GetObjectClass (m_obj);
    if (g_class == NULL)
    {
        std::cout << "Failed to find class" << std::endl;
    }

    // save method ID to call the method later in the listener
    m_amplitudeCallbackID = env->GetMethodID (g_class, "amplitudeCallback", "([I)V");
    m_shapeDetectedCallbackID = env->GetMethodID (g_class, "shapeDetectedCallback", "([I)V");
}

void Java_com_esalman17_embodieddemo_MainActivity_DetectBackgroundNative (JNIEnv *env, jobject thiz)
{
    listener.detectBackground();
}

void Java_com_esalman17_embodieddemo_MainActivity_CloseCameraNative (JNIEnv *env, jobject thiz)
{
    cameraDevice->stopCapture();
}

void Java_com_esalman17_embodieddemo_MainActivity_ChangeModeNative (JNIEnv *env, jobject thiz, jint m)
{
    setMode(m);
}

void Java_com_esalman17_embodieddemo_MainActivity_RegisterDisplay (JNIEnv *env, jobject thiz, jint w, jint h)
{
    disp_width = w;
    disp_height = h;
}

#ifdef __cplusplus
}
#endif
