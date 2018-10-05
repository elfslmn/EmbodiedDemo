//
// Created by esalman17 on 5.10.2018.
//

#include "CamListener.h"
#include "Util.h"


enum Mode {UNKNOWN, CAMERA, TEST};
Mode currentMode;

CamListener::CamListener(){}

void CamListener::initialize(uint16_t width, uint16_t height){
    cam_height = height;
    cam_width = width;
    zImage.create (Size (cam_width,cam_height), CV_32FC1);
    backgrMat.create (Size (cam_width,cam_height), CV_32FC1);
    backgrMat = Scalar::all (0);
    drawing = Mat::zeros(cam_height, cam_width, CV_8UC3);
    putText(drawing, "Click Backgr button",Point(30,30),FONT_HERSHEY_PLAIN ,1,Scalar(0,0,255),1);
}

void CamListener::setDisplaySize(uint16_t width, uint16_t height){
    disp_height = height;
    disp_width = width;
}

void CamListener::detectBackground(){
    LOGI("Background detecting has started.");
    back_detecting = true;
    detected = false;
    frameCounter = 0;
    averageNoise = 0;
    backgrMat = Scalar::all (0);
    drawing = Scalar::all (0);
    putText(drawing, "Detecting background...",Point(30,30),FONT_HERSHEY_PLAIN ,1,Scalar(0,0,255),1);
}

void CamListener::setLensParameters (LensParameters lensParameters)
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

void CamListener::setMode(int i){
    switch(i)
    {
        case 1:
            currentMode = CAMERA;
            LOGD("Mode: CAMERA");
            break;
        case 2:
            currentMode = TEST;
            LOGD("Mode: TEST");
            break;
        default:
            currentMode = UNKNOWN;
            LOGD("Mode: UNKNOWN (%d)", i);
            break;
    }
}

void CamListener::onNewData (const DepthData *data)
{
    lock_guard<mutex> lock (flagMutex);
    if(back_detecting)
    {
        zImage = Scalar::all(0);
        float frameNoise = getDepthImage(data, zImage, true);

        // get avarage around 20 frame
        backgrMat += zImage;
        averageNoise += frameNoise;
        frameCounter++;
        if(frameCounter == BACKGROUND_FRAME_COUNT)
        {
            backgrMat /= BACKGROUND_FRAME_COUNT;
            averageNoise /= BACKGROUND_FRAME_COUNT;
            back_detecting = false;
            detected = true;
            LOGI("Background detecting has ended. Avarage noise value is %.3f m", averageNoise);
        }
    }
    else if (detected)
    {
        zImage = backgrMat.clone();
        getDepthImage(data, zImage, false);

        // calculate differences between new image and background then find contours of diff blobs
        diff = backgrMat - zImage;
        medianBlur(diff, diff, 5);
        threshold(diff, diffBin, averageNoise, 255, CV_THRESH_BINARY);
        diffBin.convertTo(diffBin, CV_8UC1);
        vector<vector<Point> > contours;
        findContours(diffBin, contours, CV_RETR_EXTERNAL, CV_CHAIN_APPROX_SIMPLE, Point(0, 0));


        if(currentMode == CAMERA)
        {
            visualizeContours(zImage, drawing, contours);
        }
        else if(currentMode == TEST)
        {
            getBlobs(blobCenters, contours);
        }

    }

    // --------------- Fire appropriate callbacks according to current mode ----------------------
    if(currentMode == CAMERA)
    {
        callbackManager.sendImageToJavaSide(drawing);
    }
    else if(currentMode == TEST )
    {
        callbackManager.onShapeDetected(blobCenters);
    }

}

float CamListener::getDepthImage(const DepthData* data, Mat & img, bool background){
    int noiseCounter = 0;
    float sumNoise = 0;
    int confidence = background ? 0:MIN_DEPTH_CONFIDENCE;
    // save data as image matrix
    int k = img.rows * img.cols -1 ; // to flip screen
    for (int y = 0; y < img.rows; y++)
    {
        float *zRowPtr = img.ptr<float> (y);
        for (int x = 0; x < img.cols; x++, k--)
        {
            auto curPoint = data->points.at (k);
            if (curPoint.depthConfidence > confidence)
            {
                zRowPtr[x] = curPoint.z < MAX_DISTANCE ? curPoint.z : MAX_DISTANCE;
                sumNoise += curPoint.noise;
                noiseCounter++;
            }
        }
    }
    return sumNoise/noiseCounter;
}

pair<int, int> CamListener::convertCamPixel2ProPixel(float x, float y, float z){
    if( x<0 || y<0 || z<=0){
        return {-1,-1};
    }
    if(disp_width == 0 || cam_width == 0){
        return {-1,-1};
    }

    //scale = sin(camFov/2) / sin(proFov/2)
    float scale_x = 1.3074;
    float scale_y = 1.8256;
    float shifty = 486.69004 * exp(-0.048035356*z);
    float px = x * disp_width* scale_x / cam_width - disp_width*(scale_x -1) /2 +10;  // shiftx nearly 0
    float py = y * disp_height * scale_y / cam_height  - disp_height*(scale_y -1)/2 - shifty + 580;

    if(px > disp_width || px < 0 || py>disp_height|| py < 0){
        //LOGD("Point is outside of the projector view");
        return {-1,-1};
    }

    return  {(int)px,(int)py};
}


bool CamListener::checkIfContourIntersectWithEdge(const vector<Point>& pts, int img_width, int img_height)
{
    auto rect = boundingRect(pts);
    return (rect.tl().x == 0 || rect.tl().y == 0 || rect.br().y ==img_height || rect.br().x == img_width);
}

void CamListener::visualizeContours(Mat & src, Mat & output, const vector<vector<Point> > & contours){
    src.at<float>(0, 0) = MAX_DISTANCE;
    normalize(src, output, 0, 255, NORM_MINMAX, CV_8UC1);
    output = 255 - output;
    cvtColor(output, output, COLOR_GRAY2BGR);

    for (unsigned int i = 0; i < contours.size(); i++)
    {
        if (contourArea(contours[i]) < MIN_CONTOUR_AREA)
        {
            drawContours(output, contours, i, Scalar(0, 0, 255));
            continue;
        }

        bool conEdge = checkIfContourIntersectWithEdge(contours[i], cam_width, cam_height);
        if(conEdge)
        {
            auto ext = std::minmax_element(contours[i].begin(), contours[i].end(), [](Point const& a, Point const& b)
            {
                return a.y < b.y;
            });
            auto tip = Point2f(ext.first->x, ext.first->y );
            drawContours(output, contours, i, Scalar(0, 255, 0));
            circle(output, tip, 2, Scalar(0, 255, 0));
        }
        else
        {
            Moments mu = moments(contours[i], false);
            auto center = Point2f(mu.m10 / mu.m00, mu.m01 / mu.m00);
            drawContours(output, contours, i, Scalar(255, 0, 0));
            circle(output, center, 2, Scalar(255, 0, 0));
        }
    }
}

void CamListener::getBlobs(vector<int> & blobs, const vector<vector<Point> > & contours){
    blobs.clear();
    for (unsigned int i = 0; i < contours.size(); i++)
    {
        if (contourArea(contours[i]) < MIN_CONTOUR_AREA) continue;

        int conEdge = checkIfContourIntersectWithEdge(contours[i], cam_width, cam_height);
        float x, y;
        if(conEdge)
        {
            auto ext = std::minmax_element(contours[i].begin(), contours[i].end(), [](Point const& a, Point const& b)
            {
                return a.y < b.y;
            });
            x = ext.first->x ;
            y = ext.first->y ;
        }
        else
        {
            Moments mu = moments(contours[i], false);
            x = mu.m10 / mu.m00;
            y = mu.m01 / mu.m00;
        }

        if(x >= cam_width || x < 0 || y >= cam_height || y < 0)  continue;

        float z = zImage.at<float>((int)y,(int)x);
        auto center = convertCamPixel2ProPixel(x,y,z);
        if(center.first < 0) continue;

        blobs.push_back(center.first);
        blobs.push_back(center.second);
        blobs.push_back(conEdge);
    }
}