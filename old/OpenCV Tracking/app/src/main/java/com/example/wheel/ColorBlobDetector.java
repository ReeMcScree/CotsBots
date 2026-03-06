package com.example.wheel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

public class ColorBlobDetector {
    private Scalar mLowerBound = new Scalar(0, 0, 0);
    private Scalar mUpperBound = new Scalar(255, 255, 255);


    private Scalar mColorRadius = new Scalar(5, 25, 25, 0);

    private Mat mSpectrum = new Mat();

    private final List<MatOfPoint> mContours = new ArrayList<>();

    // 缓存
    private final Mat mPyrDownMat = new Mat();
    private final Mat mHsvMat = new Mat();
    private final Mat mMask = new Mat();
    private final Mat mDilatedMask = new Mat();
    private final Mat mHierarchy = new Mat();

    private double minAbsArea = 200.0;

    public void setMinAbsArea(double px) {
        this.minAbsArea = px;
    }

    public void setColorRadius(Scalar radiusHsv) {
        this.mColorRadius = radiusHsv;
    }

    public void setHsvColor(Scalar hsvColor) {
        double minH = Math.max(hsvColor.val[0] - mColorRadius.val[0], 0);
        double maxH = Math.min(hsvColor.val[0] + mColorRadius.val[0], 255);

        mLowerBound = new Scalar(
                minH,
                Math.max(hsvColor.val[1] - mColorRadius.val[1], 0),
                Math.max(hsvColor.val[2] - mColorRadius.val[2], 0)
        );
        mUpperBound = new Scalar(
                maxH,
                Math.min(hsvColor.val[1] + mColorRadius.val[1], 255),
                Math.min(hsvColor.val[2] + mColorRadius.val[2], 255)
        );

        mSpectrum = new Mat(1, (int) (mUpperBound.val[0] - mLowerBound.val[0] + 1), CvType.CV_8UC3);
        for (int i = 0; i < mSpectrum.cols(); i++) {
            double h = mLowerBound.val[0] + i;
            mSpectrum.put(0, i, new byte[] {(byte) h, (byte) 255, (byte) 255});
        }
        Imgproc.cvtColor(mSpectrum, mSpectrum, Imgproc.COLOR_HSV2RGB_FULL, 4);
    }

    public Mat getSpectrum() {
        return mSpectrum;
    }

    public void process(Mat rgbaImage) {
        // 降采样可提升速度（可选）
        Imgproc.pyrDown(rgbaImage, mPyrDownMat);
        Imgproc.pyrDown(mPyrDownMat, mPyrDownMat);

        // RGB -> HSV
        Imgproc.cvtColor(mPyrDownMat, mHsvMat, Imgproc.COLOR_RGB2HSV_FULL);

        // inRange
        Core.inRange(mHsvMat, mLowerBound, mUpperBound, mMask);

        Imgproc.dilate(mMask, mDilatedMask, new Mat(), new Point(-1, -1), 2);

        List<MatOfPoint> contours = new ArrayList<>();
        Imgproc.findContours(mDilatedMask, contours, mHierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);

        mContours.clear();
        if (contours.isEmpty()) return;

        double bestArea = -1.0;
        MatOfPoint bestContour = null;

        for (MatOfPoint c : contours) {
            double area = Imgproc.contourArea(c);
            if (area > bestArea) {
                bestArea = area;
                bestContour = c;
            }
        }

        if (bestContour != null && bestArea >= minAbsArea) {
            Core.multiply(bestContour, new Scalar(4, 4), bestContour);
            mContours.add(bestContour);
        }
    }

    public List<MatOfPoint> getContours() {
        return Collections.unmodifiableList(mContours);
    }
}
