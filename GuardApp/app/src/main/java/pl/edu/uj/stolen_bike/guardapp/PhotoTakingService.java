package pl.edu.uj.stolen_bike.guardapp;

/**
 * Created by shybovycha on 10/06/16.
 */

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.PixelFormat;
import android.hardware.Camera;
import android.os.IBinder;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Base64;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.WindowManager;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Takes a single photo on service start.
 */
public class PhotoTakingService extends Service {
    private Intent intent;

    @SuppressWarnings("deprecation")
    private void takePhoto() {
        final Context context = this;
        final SurfaceView preview = new SurfaceView(context);
        SurfaceHolder holder = preview.getHolder();
        // deprecated setting, but required on Android versions prior to 3.0
        holder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);

        holder.addCallback(new SurfaceHolder.Callback() {
            @Override
            //The preview must happen at or after this point or takePicture fails
            public void surfaceCreated(SurfaceHolder holder) {
                showMessage("Surface created");

                Camera camera = null;

                try {
                    camera = Camera.open();
                    showMessage("Opened camera");

                    try {
                        camera.setPreviewDisplay(holder);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }

                    camera.startPreview();
                    showMessage("Started preview");

                    camera.takePicture(null, null, new Camera.PictureCallback() {

                        @Override
                        public void onPictureTaken(byte[] data, Camera camera) {
                            showMessage("Took picture");

                            Bitmap bmp = BitmapFactory.decodeByteArray(data, 0, data.length);
                            ByteArrayOutputStream stream = new ByteArrayOutputStream();
                            bmp.compress(Bitmap.CompressFormat.JPEG, 70, stream);

                            String photoDataStr = Base64.encodeToString(stream.toByteArray(), Base64.DEFAULT);

                            StolenBikeRecord record = (StolenBikeRecord) intent.getSerializableExtra("record");

                            record.setImage(photoDataStr);

                            Intent intent = new Intent("photoTaken");
                            intent.putExtra("record", record);

                            LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(intent);
                            camera.release();
                        }
                    });
                } catch (Exception e) {
                    e.printStackTrace();

                    if (camera != null)
                        camera.release();
                    throw new RuntimeException(e);
                }
            }

            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {
            }

            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            }
        });

        WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                1, 1, //Must be at least 1x1
                WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY,
                0,
                //Don't know if this is a safe default
                PixelFormat.UNKNOWN);

        //Don't set the preview visibility to GONE or INVISIBLE
        wm.addView(preview, params);
    }

    private void showMessage(String message) {
        Log.i("Camera", message);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        this.intent = intent;

        this.takePhoto();

        return 0;
    }
}