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
    gray.create (Size (cam_width,cam_height), CV_16UC1);

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
    /*if (data->exposureTimes.size () >= 3)
    {
        LOGD ("ExposureTimes: %d, %d, %d", data->exposureTimes.at (0), data->exposureTimes.at (1), data->exposureTimes.at (2));
    }*/

    lock_guard<mutex> lock (flagMutex);
    if(back_detecting)
    {
        zImage = Scalar::all(0);
        float frameNoise = updateDepthGrayImage(data, zImage, gray, true);

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
        updateDepthGrayImage(data, zImage, gray, false);

        // calculate differences between new image and background then find contours of diff blobs
        diff = backgrMat - zImage;
        medianBlur(diff, diff, 5);
        threshold(diff, diffBin, averageNoise+0.005, 255, CV_THRESH_BINARY); //TODO threshold??
        diffBin.convertTo(diffBin, CV_8UC1);
        vector<vector<Point> > contours;
        findContours(diffBin, contours, CV_RETR_EXTERNAL, CV_CHAIN_APPROX_SIMPLE, Point(0, 0));

        // Find retro blobs
        vector<vector<Point> > retro_contours;
        if(!gray.empty()){
            threshold(gray, grayBin, RETRO_THRESHOLD, 255, CV_THRESH_BINARY);
            grayBin.convertTo(grayBin, CV_8UC1);
            findContours(grayBin, retro_contours, CV_RETR_EXTERNAL, CV_CHAIN_APPROX_SIMPLE, Point(0, 0));;
        }


        if(currentMode == CAMERA)
        {
            visualizeBlobs(zImage, drawing, contours, retro_contours);
        }
        else if(currentMode == TEST)
        {
            blobCenters.clear();
            getBlobs(blobCenters, contours, retro_contours);
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

float CamListener::updateDepthGrayImage(const DepthData* data, Mat & depth, Mat & gray, bool background){
    int noiseCounter = 0;
    float sumNoise = 0;
    bool isDepth =true , isGray = true;
    if(depth.empty()) {isDepth = false; LOGD("depth not found");}
    if(gray.empty()) {isGray = false; LOGD("gray not found");}
    if(!isGray && !isDepth){
        LOGE("Both depth and gray image is null");
        return -1;
    }

    int confidence = background ? 0:MIN_DEPTH_CONFIDENCE;
    // save data as image matrix
    int k = 0, rows =0, cols = 0;
    if(isDepth){
        k = depth.rows * depth.cols -1 ; // to flip screen
        k -= MARGIN*depth.cols;
        rows = depth.rows;
        cols = depth.cols;
    }
    else if(isGray){
        k = gray.rows * gray.cols -1 ; // to flip screen
        k -= MARGIN*gray.cols;
        rows = gray.rows;
        cols = gray.cols;
    }

    for (int y = MARGIN; y < rows-MARGIN; y++)
    {
        float *zRowPtr;
        uint16_t *gRowPtr;
        if(isDepth) zRowPtr = depth.ptr<float> (y);
        if(isGray ) gRowPtr = gray.ptr<uint16_t> (y);
        k -= MARGIN;
        for (int x = MARGIN; x < cols-MARGIN; x++, k--)
        {
            auto curPoint = data->points.at (k);
            if (curPoint.depthConfidence > confidence)
            {
                if(isDepth) zRowPtr[x] = curPoint.z < MAX_DISTANCE ? curPoint.z : MAX_DISTANCE;
                sumNoise += curPoint.noise;
                noiseCounter++;
            }
            if(isGray ) gRowPtr[x] = curPoint.grayValue;

        }
        k -= MARGIN;
    }
    //LOGD("Avg noise = %.3f\t Max noise=%.3f",sumNoise/noiseCounter, maxNoise);
    return sumNoise/noiseCounter;
}

void CamListener::setCalibration(double* arr){
    lock_guard<mutex> lock (flagMutex);
    calibration_result = Vec4d(arr[0], arr[1], arr[2], arr[3]);
    LOGD("Calibration loaded = %f %f %f %f", calibration_result[0], calibration_result[1],calibration_result[2],calibration_result[3]);
}

pair<int, int> CamListener::convertCamPixel2ProPixel(float x, float y, float z){
    if( x<0 || y<0 || z<=0){
        return {-1,-1};
    }
    if(disp_width == 0 || cam_width == 0){
        return {-1,-1};
    }

    z *= 100; // z should be given in cm
    Vec4d & coef = calibration_result;
    double shiftx = coef[0] * exp(coef[1]*z);
    double shifty = coef[2] * exp(coef[3]*z);
    int px = (double)x * disp_width* x_scale / cam_width - x_offset + shiftx; // + shift due to flip
    int py = (double)y * disp_height* y_scale / cam_height - y_offset + shifty;

    if(px > disp_width || px < 0 || py>disp_height|| py < 0){
        //LOGD("Point is outside of the projector view");
        return {-1,-1};
    }

    return  { px , py };
}


bool CamListener::checkIfContourIntersectWithEdge(const vector<Point>& pts, int img_width, int img_height)
{
    auto rect = boundingRect(pts);
    return (rect.tl().x <= MARGIN || rect.tl().y <= MARGIN ||
            rect.br().y >= img_height-MARGIN || rect.br().x >= img_width-MARGIN);
}

void CamListener::visualizeBlobs(Mat & src, Mat & output, const vector<vector<Point> > & contours,
                                 const vector<vector<Point> > & retro_contours)
{
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

    for (unsigned int i = 0; i < retro_contours.size(); i++)
    {
        drawContours(output, retro_contours, i, Scalar(255, 0, 255));
    }

}

void CamListener::getBlobs(vector<int> & blobs, const vector<vector<Point> > & contours,
                           const vector<vector<Point> > & retro_contours)
{
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
            /*Moments mu = moments(contours[i], false);
            x = mu.m10 / mu.m00;
            y = mu.m01 / mu.m00; */
            continue;
        }

        if(x >= cam_width - MARGIN || x < MARGIN || y >= cam_height - MARGIN || y < MARGIN )  continue;

        float z = zImage.at<float>((int)y,(int)x);
        auto center = convertCamPixel2ProPixel(x,y,z);
        if(center.first < 0) continue;

        blobs.push_back(center.first);
        blobs.push_back(center.second);
        blobs.push_back(1); // 1 means it is a gesture blob
    }

    for (unsigned int i = 0; i < retro_contours.size(); i++)
    {
        //if (contourArea(retro_contours[i]) < MIN_RETRO_AREA) continue;

        /*Moments mu = moments(retro_contours[i], false);
        float x = mu.m10 / mu.m00;
        float y = mu.m01 / mu.m00; */
        int x = retro_contours[i][0].x;
        int y = retro_contours[i][0].y;

        if(x >= cam_width - MARGIN || x < MARGIN || y >= cam_height - MARGIN || y < MARGIN )  continue;

        float z = backgrMat.at<float>((int)y,(int)x) + OBJECT_HEIGHT;
        auto center = convertCamPixel2ProPixel(x,y,z);

        blobs.push_back(center.first);
        blobs.push_back(center.second);
        blobs.push_back(0); // 0 means it is a retro blob

    }


}