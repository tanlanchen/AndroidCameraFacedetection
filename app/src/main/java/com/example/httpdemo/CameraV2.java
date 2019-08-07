package com.example.httpdemo;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.hardware.Camera;
import android.hardware.camera2.*;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.*;

import android.util.Log;
import android.util.Size;
import android.view.Surface;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.core.app.ActivityCompat;
import org.opencv.android.Utils;
import org.opencv.core.Mat;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.*;

import static org.opencv.core.CvType.CV_8UC3;


public class CameraV2 {
    static {
        System.loadLibrary("facedetection");
    }




    public static final String TAG = "Filter_CameraV2";

    private Activity mActivity;
    private CameraDevice mCameraDevice;
    private String mCameraId;
    private Size mPreviewSize;
    private Size mReaderSize;
    private HandlerThread mCameraThread;
    private Handler mCameraHandler;
    private SurfaceTexture mSurfaceTexture;
    private CaptureRequest.Builder mCaptureRequestBuilder;
    private CaptureRequest mCaptureRequest;
    private CameraCaptureSession mCameraCaptureSession;
    private ImageReader mImageReader;
    static Face mFace=null;
    private Handler mHandler;
    private int count=0;

    public CameraV2(Activity activity) {
        mActivity = activity;
        startCameraThread();
    }

    public String setupCamera(int width, int height) {
        CameraManager cameraManager = (CameraManager) mActivity.getSystemService(Context.CAMERA_SERVICE);
        try {
            for (String id : cameraManager.getCameraIdList()) {
                CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(id);
                if (characteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK) {
                    continue;
                }
                StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                mPreviewSize = getOptimalSize(map.getOutputSizes(SurfaceTexture.class), width, height);
                mCameraId = id;
                Log.e(TAG, "preview width = " + mPreviewSize.getWidth() + ", height = " + mPreviewSize.getHeight() + ", cameraId = " + mCameraId);
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
        return mCameraId;
    }

    public void startCameraThread() {
        mCameraThread = new HandlerThread("CameraThread");
        mCameraThread.start();
        mCameraHandler = new Handler(mCameraThread.getLooper());
    }

    public boolean openCamera(Handler handler) {
        mHandler = handler;
        CameraManager cameraManager = (CameraManager) mActivity.getSystemService(Context.CAMERA_SERVICE);
        try {
            if (ActivityCompat.checkSelfPermission(mActivity, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
            cameraManager.openCamera(mCameraId, mStateCallback, mCameraHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    public CameraDevice.StateCallback mStateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            mCameraDevice = camera;
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {
            camera.close();
            mCameraDevice = null;
        }

        @Override
        public void onError(@NonNull CameraDevice camera, int error) {
            camera.close();
            mCameraDevice = null;
        }
    };

    private Size getOptimalSize(Size[] sizeMap, int width, int height) {
        List<Size> sizeList = new ArrayList<>();
        for (Size option : sizeMap) {
            if (width > height) {
                if (option.getWidth() > width && option.getHeight() > height) {
                    sizeList.add(option);
                }
            } else {
                if (option.getWidth() > height && option.getHeight() > width) {
                    sizeList.add(option);
                }
            }
        }
        if (sizeList.size() > 0) {
            return Collections.min(sizeList, new Comparator<Size>() {
                @Override
                public int compare(Size lhs, Size rhs) {
                    return Long.signum(lhs.getWidth() * lhs.getHeight() - rhs.getWidth() * rhs.getHeight());
                }
            });
        }
        return sizeMap[0];
    }


    public void setPreviewTexture(SurfaceTexture surfaceTexture) {
        mSurfaceTexture = surfaceTexture;
    }

    public void startPreview() {

         mReaderSize = new Size(640,  360);
        mSurfaceTexture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());
        Surface surface = new Surface(mSurfaceTexture);
        try {
            mCaptureRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            mCaptureRequestBuilder.addTarget(surface);
            initImageReader(mReaderSize, ImageFormat.YUV_420_888);

            mCaptureRequestBuilder.addTarget(mImageReader.getSurface());
            mCameraDevice.createCaptureSession(Arrays.asList(surface, mImageReader.getSurface()), new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession session) {
                    try {
                        mCaptureRequest = mCaptureRequestBuilder.build();
                        mCameraCaptureSession = session;
                        mCameraCaptureSession.setRepeatingRequest(mCaptureRequest, null, mCameraHandler);
                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                    }
                }


                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession session) {

                }
            }, mCameraHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void initImageReader(Size size, int format) {
        mImageReader = ImageReader.newInstance((int)size.getWidth(), (int)size.getHeight(), format, /*maxImages*/10);
        mImageReader.setOnImageAvailableListener(mOnImageAvailableListener, mCameraHandler);
    }
    private ImageReader.OnImageAvailableListener mOnImageAvailableListener
            = new ImageReader.OnImageAvailableListener() {

        /**
         *  当有一张图片可用时会回调此方法，但有一点一定要注意：
         *  一定要调用 reader.acquireNextImage()和close()方法，否则画面就会卡住！！！！！我被这个坑坑了好久！！！
         *    很多人可能写Demo就在这里打一个Log，结果卡住了，或者方法不能一直被回调。
         **/
        @RequiresApi(api = Build.VERSION_CODES.KITKAT)
        @Override
        public void onImageAvailable(ImageReader reader) {

//            Image img = reader.acquireNextImage();
////            /**
////             *  因为Camera2并没有Camera1的Priview回调！！！所以该怎么能到预览图像的byte[]呢？就是在这里了！！！我找了好久的办法！！！
////             **/
//            ByteBuffer buffer = img.getPlanes()[0].getBuffer();
//            buffer.position(0);
//            byte[] data = new byte[buffer.remaining()];
//
//            buffer.get(data);
//            img.close();
            //      double scale=1;//测试时缩小的倍数

            Mat inputimg=new Mat();
            // 获取捕获的照片数据.setDisplayOrientation(90)
            Image mImage = reader.acquireNextImage();
            Log.e("image大小",mImage.getWidth()+" "+mImage.getHeight());
     /*       if (mImage== null) return;
            ByteArrayOutputStream outputbytes = new ByteArrayOutputStream();

            ByteBuffer bufferY = mImage.getPlanes()[0].getBuffer();
            byte[] data0 = new byte[bufferY.remaining()];
            bufferY.get(data0);

            ByteBuffer bufferU = mImage.getPlanes()[1].getBuffer();
            byte[] data1 = new byte[bufferU.remaining()];
            bufferU.get(data1);

            ByteBuffer bufferV = mImage.getPlanes()[2].getBuffer();
            byte[] data2 = new byte[bufferV.remaining()];
            bufferV.get(data2);

            try {
                outputbytes.write(data0);
                outputbytes.write(data2);
                outputbytes.write(data1);
            } catch (IOException e) {
                e.printStackTrace();
            }

            final YuvImage yuvImage = new YuvImage(outputbytes.toByteArray(), ImageFormat.NV21, mImage.getWidth(),mImage.getHeight(), null);
            ByteArrayOutputStream outBitmap = new ByteArrayOutputStream();
            yuvImage.compressToJpeg(new Rect(0, 0, mImage.getWidth(),mImage.getHeight()), 95, outBitmap);

*/

            byte[] data = ImageFormatUtil.imageToByteArray(mImage);





            Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0,data.length);
            Log.e("bitmat大小 ", "temp" + bitmap.getWidth() + " " + bitmap.getHeight());
     /*       ByteBuffer byteBuffer = mImage.getPlanes()[0].getBuffer();
            byte[] bytes = new byte[byteBuffer.remaining()];
            byteBuffer.get(bytes);
                Bitmap temp = BitmapFactory.decodeByteArray(bytes,0,bytes.length);
            Log.e("bitmat大小 ", "temp" + temp.getWidth() + "" + temp.getHeight());
                if(temp!=null) {
                    Utils.bitmapToMat(temp, inputimg);
                    Bitmap newBitmap = Bitmap.createBitmap(mPreviewSize.getWidth(), mPreviewSize.getWidth(), temp.getConfig());

                    Log.e("bitmat大小 ", "newBitmap" + newBitmap.getWidth() + "" + newBitmap.getHeight());

Utils.bitmapToMat(temp, inputimg);
                    //   Mat inputimg=imageToMat(mImage);
                    Log.e("mat大小", "未调整" + inputimg.cols() + " " + inputimg.rows());
    /*        if(inputimg.size().area()>0) {
                Imgproc.resize(inputimg,inputimg, new Size((1 / scale) * inputimg.cols(), (1 / scale) * inputimg.rows()));
               Log.e("mat大小","调整后"+inputimg.cols()+" "+inputimg.rows());
     */
            Utils.bitmapToMat(bitmap, inputimg);
            Log.e("mat大小", "未调整" + inputimg.cols() + " " + inputimg.rows());
                    long addr = inputimg.getNativeObjAddr();
                    //               Scalar FACE_RECT_COLOR = new Scalar(255.0, 0.0, 0.0);
                    //
                    long startTime = System.currentTimeMillis(); // 获取开始时间

                    Face[] facesArray = facedetect(addr);
                    if (facesArray != null && facesArray.length >= 1) {
                        if(facesArray[0].faceConfidence>=70) {
                            mFace = facesArray[0];
         /*                 if(count==0) {

                                Log.e("demo","进入写文件try");

                                writeFrame(Environment.getExternalStorageDirectory() + "/DCIM/myPicture.jpg",data);




                                count++;
                            }
                            */
                        }

                        else
                            mFace = null;
                    } else {
                        mFace = null;
                    }
                    //在此得到人脸坐标
      /*      for (int i = 0; i < facesArray.length; i++) {
                Point pt1 = new Point(scale*facesArray[i].faceRect.x, scale*facesArray[i].faceRect.y);
                Point pt2 = new Point(scale*(facesArray[i].faceRect.x + facesArray[i].faceRect.width),
                        scale* (facesArray[i].faceRect.y + facesArray[i].faceRect.height));
                //   Imgproc.rectangle(inputimg, pt1, pt2, FACE_RECT_COLOR, FACE_RECT_THICKNESS);
            }
            double facex= scale*facesArray[0].faceRect.x;
            double facey= scale*facesArray[0].faceRect.y;
        */
                    long endTime = System.currentTimeMillis(); // 获取结束时间
                    Log.e("facedetection", "运行时间： " + (endTime - startTime) + "ms");

            //   }
            mImage.close();

           Message m = mHandler.obtainMessage();
            if (null == mFace ) {
                m.obj = null;

                Log.e("face", "onFaceDetection : There is no face found.");
            } else {
                Log.e("face", "onFaceDetection : face found.");
                m.obj = mFace;


           //         Log.i("face","face.score="+face.score);
            //        Log.i("face","rect.left="+rect.left+"\nrect.top="+rect.top+"\nrect.right="+rect.right+"\nrect.bottom="+rect.bottom);
                    //         Log.i("face","id="+face.id+" \nface.leftEye.x="+face.leftEye.x+" \nface.leftEye.y"+face.leftEye.y+" \nface.mouth.x="+face.mouth.x+" \nface.mouth.y="+face.mouth.y);

            }
            m.sendToTarget();

        }


    };
    public static void writeFrame(String fileName, byte[] data) {
        try {
            BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(fileName));
            bos.write(data);
            bos.flush();
            bos.close();
            Log.e("demo","写文件--------------------------------------------------------");
        } catch (IOException e) {
            e.printStackTrace();
            Log.e("demo","未写文件--------------------------------------------------------");
        }
    }

 /*   public static Mat imageToMat(Image image) {
        ByteBuffer buffer;
        int rowStride;
        int pixelStride;
        int width = image.getWidth();
        int height = image.getHeight();
        int offset = 0;

        Image.Plane[] planes = image.getPlanes();
        byte[] data = new byte[image.getWidth() * image.getHeight() * ImageFormat.getBitsPerPixel(ImageFormat.YUV_420_888) / 8];
        byte[] rowData = new byte[planes[0].getRowStride()];

        for (int i = 0; i < planes.length; i++) {
            buffer = planes[i].getBuffer();
            rowStride = planes[i].getRowStride();
            pixelStride = planes[i].getPixelStride();
            int w = (i == 0) ? width : width / 2;
            int h = (i == 0) ? height : height / 2;
            for (int row = 0; row < h; row++) {
                int bytesPerPixel = ImageFormat.getBitsPerPixel(ImageFormat.YUV_420_888) / 8;
                if (pixelStride == bytesPerPixel) {
                    int length = w * bytesPerPixel;
                    buffer.get(data, offset, length);

                    if (h - row != 1) {
                        buffer.position(buffer.position() + rowStride - length);
                    }
                    offset += length;
                } else {


                    if (h - row == 1) {
                        buffer.get(rowData, 0, width - pixelStride + 1);
                    } else {
                        buffer.get(rowData, 0, rowStride);
                    }

                    for (int col = 0; col < w; col++) {
                        data[offset++] = rowData[col * pixelStride];
                    }
                }
            }
        }

        Mat mat = new Mat(height , width, CV_8UC3);
        mat.put(0, 0, data);

        return mat;
    }
    */
    public static native Face[] facedetect(long matAddr);
}