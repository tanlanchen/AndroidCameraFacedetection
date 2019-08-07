package com.example.httpdemo;

import android.content.Context;
import android.graphics.Matrix;
import android.graphics.RectF;

import android.opengl.GLES20;
import android.util.Log;
import org.opencv.core.Rect;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;

import static android.opengl.GLES20.*;

public class FilterFace {
    private static final String TAG = "FACE";

    //顶点程序
    private static final String VERTEX_SHADER =
            "uniform mat4 u_MVPMatrix;" +
                    "attribute vec4 a_position;" +
                    "attribute vec2 a_texCoord;" +
                    "varying vec2 v_texCoord;" +
                    "void main() {" +
                    "  gl_Position = a_position;" +
                    "  v_texCoord = a_texCoord;" +
                    "}";

    private static final String FRAGMENT_SHADER =
            "precision lowp float;" +
                    "varying vec2 v_texCoord;" +
                    "uniform sampler2D u_samplerTexture;" +
                    "void main() {" +
                    "  gl_FragColor = texture2D(u_samplerTexture, v_texCoord);" +
                    "}";

    public static final String POSITION_ATTRIBUTE = "a_position";
    public static final String TEXTURE_COORD_ATTRIBUTE = "a_texCoord";
    public static final String TEXTURE_SAMPLER_UNIFORM = "u_samplerTexture";


    private float[] quadVertex =new float[]{
            -1.0f, 0.296f, 0.0f, // Position 0
            0, 0, // TexCoord 1
            -1f, -0.296f, 0.0f, // Position 1
            0, 1.0f, // TexCoord 0
            1f, -0.296f, 0.0f, // Position 2
            1.0f, 1.0f, // TexCoord 3
            1f, 0.296f, 0.0f, // Position 3
            1.0f, 0, // TexCoord 2
    };
    private short[] quadIndex = new short[]{
            0, 1, 2, // 0号点，1号点，2号点组成一个三角形
            0, 2, 3, // 0号点，2号点，3号点组成一个三角形
    };

    private float[] TEX_VERTEX = new float[]{
            0, 0, // TexCoord 1
            0, 1.0f, // TexCoord 0
            1.0f, 1.0f, // TexCoord 3
            1.0f, 0, // TexCoord 2
    };

    private FloatBuffer mVertexBuffer;
    private ShortBuffer mIndexBuffer;
    private FloatBuffer mTexVertexBuffer;
    private final float[] mMVPMatrix = new float[16];

    public static Face mFace=null;
    private int mW,mH;

    private Matrix mMatrix = new Matrix();
    private Context mContext;
    private int vertexShader = -1;
    private int fragmentShader = -1;

    private int mShaderProgram = -1;


    private int mPositionLocation = -1;
    private int mTextureCoordLocation = -1;
    private int mTextureSamplerLocation = -1;

    public FilterFace(Context context) {
        super();
        mContext = context;
        loadVertex();
        vertexShader = loadShader(GL_VERTEX_SHADER, VERTEX_SHADER);
        fragmentShader = loadShader(GL_FRAGMENT_SHADER, FRAGMENT_SHADER);
        mShaderProgram = linkProgram(vertexShader, fragmentShader);
        loadShadingLanguageVariable();
    }

    private void loadVertex() {
        // float size = 4
        this.mVertexBuffer = ByteBuffer.allocateDirect(quadVertex.length * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();
        this.mVertexBuffer.put(quadVertex).position(0);
        // short size = 2
        this.mIndexBuffer = ByteBuffer.allocateDirect(quadIndex.length * 2)
                .order(ByteOrder.nativeOrder())
                .asShortBuffer();
        this.mIndexBuffer.put(quadIndex).position(0);

        this.mTexVertexBuffer = ByteBuffer.allocateDirect(TEX_VERTEX.length * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();
        this.mTexVertexBuffer.put(TEX_VERTEX).position(0);
    }

    //加载sl着色语句中的变量
    public void loadShadingLanguageVariable(){
        mPositionLocation = glGetAttribLocation(mShaderProgram, FilterFace.POSITION_ATTRIBUTE);
        mTextureCoordLocation = glGetAttribLocation(mShaderProgram, FilterFace.TEXTURE_COORD_ATTRIBUTE);
        mTextureSamplerLocation = glGetUniformLocation(mShaderProgram, FilterFace.TEXTURE_SAMPLER_UNIFORM);
    }

    //将顶点和纹理坐标数据使用FloatBuffer来存储，防止内存回收
    public FloatBuffer createBuffer(float[] vertexData) {
        FloatBuffer buffer = ByteBuffer.allocateDirect(vertexData.length * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();
        buffer.put(vertexData, 0, vertexData.length).position(0);
        return buffer;
    }

    //加载着色器，GL_VERTEX_SHADER代表生成顶点着色器，GL_FRAGMENT_SHADER代表生成片段着色器
    public int loadShader(int type, String shaderSource) {
        //创建Shader
        int shader = glCreateShader(type);
        if (shader == 0) {
            throw new RuntimeException("Create Shader Failed!" + glGetError());
        }
        //加载Shader代码
        glShaderSource(shader, shaderSource);
        ////编译Shader
        glCompileShader(shader);
        return shader;
    }

    //将两个Shader链接至program中
    public int linkProgram(int verShader, int fragShader) {
        //创建program
        int program = glCreateProgram();
        if (program == 0) {
            throw new RuntimeException("Create Program Failed!" + glGetError());
        }
        //附着顶点和片段着色器
        glAttachShader(program, verShader);
        glAttachShader(program, fragShader);
        //链接program
        glLinkProgram(program);
        //告诉OpenGL ES使用此program
        glUseProgram(program);
        return program;
    }

    //绘制纹理图形
    public void drawTexture2D(int textureId) {
        //使用制定sl程序
        GLES20.glUseProgram(mShaderProgram);
        //激活纹理
        glActiveTexture(GLES20.GL_TEXTURE1);
        //绑定对应纹理ID
        glBindTexture(GLES20.GL_TEXTURE_2D, textureId);
        glUniform1i(mTextureSamplerLocation, 1);

        if (mVertexBuffer != null) {
            //顶点坐标从位置0开始读取
            mVertexBuffer.position(0);
            glEnableVertexAttribArray(mPositionLocation);
            glVertexAttribPointer(mPositionLocation, 3, GL_FLOAT, false, 20, mVertexBuffer);

            //纹理坐标从位置2开始读取
            mVertexBuffer.position(3);
            glEnableVertexAttribArray(mTextureCoordLocation);
            glVertexAttribPointer(mTextureCoordLocation, 2, GL_FLOAT, false, 20, mVertexBuffer);
        }

//        glDrawArrays(GL_TRIANGLES, 0, 6);
//        glBindFramebuffer(GL_FRAMEBUFFER, 0);

        if(mFace!=null) {
            GLES20.glDrawElements(GLES20.GL_TRIANGLES, 6, GLES20.GL_UNSIGNED_SHORT, mIndexBuffer);
            Log.e("demo", "画贴纸纹理inFilterFace");
        }
    }

    private RectF mRect = new RectF();

    public  void setFaces(Face face, int w, int h){

        if(face!=null) {
            mFace = face;
        }
        else{
            mFace=null;
        }
        mW = w;
        mH = h;
//        quadVertex;
        Log.e(TAG, "DrawTexture  mW=" +mW +", mH="+mH );

    //    prepareMatrix(mMatrix, true, 90, mW, mH);
     //   mMatrix.postRotate(0);
        mRect=rectchange(mFace.faceRect);
     //   mMatrix.mapRect(mRect);//计算出在父布局的真实坐标
        //识别的Rect 系数，使装饰图片根据人脸与摄像头的距离放大或者缩小
        float dx = mRect.bottom - mRect.top;
        boolean isHeadShow = true;
//        mRect.left=463.32, mRect.top=983.94, mRect.right=847.26, mRect.bottom=1479.63
        Log.e(TAG, "DrawTexture  mRect.left=" +mRect.left +", mRect.top="+mRect.top +", mRect.right="+mRect.right +", mRect.bottom="+mRect.bottom );

        float[] lefttop = tranXY(mRect.left,mRect.top);
        float[] righttop = tranXY(mRect.right,mRect.top);
        float[] leftbottom = tranXY(mRect.left,mRect.bottom);
        float[] rightbottom = tranXY(mRect.right,mRect.bottom);
        quadVertex[0] = lefttop[0];
        quadVertex[1] = lefttop[1];
        quadVertex[15] = righttop[0];
        quadVertex[16] = righttop[1];
        quadVertex[5] = leftbottom[0];
        quadVertex[6] = leftbottom[1];
        quadVertex[10] = rightbottom[0];
        quadVertex[11] = rightbottom[1];
        Log.i(TAG, "DrawTexture  lefttop[0]=" +lefttop[0] +", lefttop[1]="+lefttop[1] +",\n " +
                "righttop[0]="+righttop[0] +", righttop[0]="+righttop[0]+",\n " +
                "leftbottom[0]="+leftbottom[0] +", leftbottom[1]="+leftbottom[1]+",\n " +
                "rightbottom[0]="+rightbottom[0] +", rightbottom[1]="+rightbottom[1] );
        Log.e("demo","进入一次setfaces");
        //重新对装饰坐标进行读取
        loadVertex();
    }

    private RectF rectchange(Rect faceRect) {
        RectF iRect = new RectF();
       iRect.left=3*(float)(faceRect.y+faceRect.height);
        iRect.top=3*(640-(float)faceRect.x)-(float)(3*faceRect.width);
        iRect.right=3*(float)faceRect.y;
        iRect.bottom=3*(640-(float)faceRect.x+faceRect.width)-(float)(3*faceRect.width);

  /*     iRect.left=3*(float)faceRect.y;
        iRect.top=3*(640-(float)faceRect.x+faceRect.width);

        iRect.right=3*(float)(faceRect.y+faceRect.height);
        iRect.bottom=3*(640-(float)faceRect.x);
  */
 /*       iRect.left=3*(float)faceRect.x;
        iRect.top=3*(float)faceRect.y;
        iRect.right=3*(float)(faceRect.x+faceRect.width);
        iRect.bottom=3*((float)faceRect.y+faceRect.height);
        */
        return iRect;

    }

    //从手机坐标系到-->GLSurfaceView 世界坐标的投影坐标
    public float[] tranXY(float x,float y){
        //GLSurfaceView 世界坐标的投影坐标
        float[] point = new float[2];
        int centerX = mW/2;
        int centerY = mH/2;
        if (x > centerX){
            point[0] = -(x - centerX) / centerX;
        }else{
            point[0] = (centerX - x) / centerX;
        }

        if (y > centerY){
            point[1] = -(y - centerY) / centerY;
        }else{
            point[1] = (centerY - y) / centerY;
        }
        return point;
    }

    public static void prepareMatrix(Matrix matrix, boolean mirror, int displayOrientation,
                                     int viewWidth, int viewHeight) {
        // Need mirror for front camera.
        matrix.setScale(mirror ? -1 : 1, 1);
        // This is the value for android.hardware.Camera.setDisplayOrientation.
        matrix.postRotate(displayOrientation);
        // Camera driver coordinates range from (-1000, -1000) to (1000, 1000).
        // UI coordinates range from (0, 0) to (width, height).
        matrix.postScale(viewWidth / 2000f, viewHeight / 2000f);
        matrix.postTranslate(viewWidth / 2f, viewHeight / 2f);
    }

}
