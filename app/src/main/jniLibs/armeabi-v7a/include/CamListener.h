//
// Created by esalman17 on 5.10.2018.
//

#include <royale/LensParameters.hpp>
#include <royale/IDepthDataListener.hpp>
#include "opencv2/opencv.hpp"
#include "CallbackManager.h"
#include <mutex>
#include <jni.h>

using namespace royale;
using namespace std;
using namespace cv;

const float MAX_DISTANCE = 1.0f;
const int BACKGROUND_FRAME_COUNT = 20;
const int MIN_CONTOUR_AREA = 100;
const int MIN_DEPTH_CONFIDENCE = 100;
const int MARGIN = 25;
const int RETRO_THRESHOLD = 700;
const int MIN_RETRO_AREA = 0;
const float OBJECT_HEIGHT = 0.020f; // in meter

class CamListener : public royale::IDepthDataListener {

public:
    // Constructors
    CamListener();

    // Public methods
    void initialize(uint16_t width, uint16_t height);
    void detectBackground();
    void setMode(int i);
    void setDisplaySize(uint16_t width, uint16_t height);
    void setLensParameters (LensParameters lensParameters);

    // Public variables
    CallbackManager callbackManager;

private:
    // Private methods
    pair<int, int> convertCamPixel2ProPixel(float x, float y, float z);
    bool checkIfContourIntersectWithEdge(const vector<Point>& pts, int img_width, int img_height);
    void onNewData (const DepthData *data);
    void visualizeBlobs(Mat & src, Mat & output, const vector<vector<Point> > & contours,
                        const vector<vector<Point> > & retro_contours = vector<vector<Point> >());
    void getBlobs(vector<int> & blobs, const vector<vector<Point> > & contours,
                  const vector<vector<Point> > & retro_contours = vector<vector<Point> >());
    float updateDepthGrayImage(const DepthData* data, Mat & depth, Mat & gray, bool background);


    // Private variables
    uint16_t cam_width, cam_height;
    uint16_t disp_width, disp_height;

    Mat cameraMatrix, distortionCoefficients;
    Mat zImage, backgrMat;
    Mat diff, diffBin;
    Mat drawing;
    Mat gray, grayBin;

    mutex flagMutex;

    bool back_detecting = false;
    bool detected = false;

    int frameCounter = 0;
    float averageNoise;

    vector<int> blobCenters;
};

