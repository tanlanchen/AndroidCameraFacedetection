package com.example.httpdemo;

import android.content.Context;

import android.opengl.GLSurfaceView;
import android.util.Log;



public class CameraV2GLSurfaceView extends GLSurfaceView {
    public static final String TAG = "Filter_CameraV2GLSurfaceView";
    private CameraV2Renderer mCameraV2Renderer;

    public void init(CameraV2 camera, boolean isPreviewStarted, Context context) {
        setEGLContextClientVersion(2);

        mCameraV2Renderer = new CameraV2Renderer();
        mCameraV2Renderer.init(this, camera, isPreviewStarted, context);

        setRenderer(mCameraV2Renderer);
    }

    public CameraV2GLSurfaceView(Context context) {
        super(context);
    }

    public void deinit() {
        if (mCameraV2Renderer != null) {
            mCameraV2Renderer .deinit();
            mCameraV2Renderer  = null;
            FilterFace.mFace=null;

        }
    }

    public void requestRenderAndFace(Face faces){
        //刷新 GLSurfaceView
        mCameraV2Renderer.setFaces(faces,getMeasuredWidth(),getMeasuredHeight());
        if(faces!=null) {
            requestRender();
            //    count=0;
            Log.e("demo","人脸渲染");
        }
        else {
            Log.e("demo", "未检测人脸渲染");
            //   count++;
            FilterFace.mFace=null;
        }
        //    if(count>=5){

        //       FilterFace.mFace=null;
        //   }
    }
}
