#extension GL_OES_EGL_image_external : require
precision mediump float;
uniform samplerExternalOES myTexture0;
varying vec2 vTextureCoord;

void main()
{
   gl_FragColor = texture2D(myTexture0, vTextureCoord);
}
