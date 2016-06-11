package pl.edu.uj.stolen_bike.guardapp;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.hardware.Camera;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.util.Base64;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import com.kontakt.sdk.android.ble.connection.OnServiceReadyListener;
import com.kontakt.sdk.android.ble.manager.ProximityManager;
import com.kontakt.sdk.android.ble.manager.ProximityManagerContract;
import com.kontakt.sdk.android.ble.manager.listeners.IBeaconListener;
import com.kontakt.sdk.android.ble.manager.listeners.simple.SimpleIBeaconListener;
import com.kontakt.sdk.android.common.KontaktSDK;
import com.kontakt.sdk.android.common.profile.IBeaconDevice;
import com.kontakt.sdk.android.common.profile.IBeaconRegion;
import okhttp3.*;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity {
    private OkHttpClient client = new OkHttpClient();
    private List<StolenBikeRecord> stolenRecords;
    private ProximityManagerContract proximityManager;

    private SurfaceView preview = null;
    private SurfaceHolder previewHolder = null;
    private Camera camera = null;
    private boolean inPreview = false;
    private boolean cameraConfigured = false;

//    private final String coachBaseUrl = "http://172.20.47.64:5984";
//    private final String apiBaseUrl = "http://172.20.47.64:8000/api";

    private final String coachBaseUrl = "http://192.168.0.145:5984";
    private final String apiBaseUrl = "http://192.168.0.145:8000/api";

    public static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    private BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            //put here whaterver you want your activity to do with the intent received
            StolenBikeRecord record = (StolenBikeRecord) intent.getSerializableExtra("record");

            MainActivity.this.sendNotification(record);
        }
    };

    private SurfaceHolder.Callback surfaceCallback = new SurfaceHolder.Callback() {
        public void surfaceCreated(SurfaceHolder holder) {
            // no-op -- wait until surfaceChanged()
        }

        public void surfaceChanged(SurfaceHolder holder,
                                   int format, int width,
                                   int height) {
            initPreview(width, height);
            startPreview();
        }

        public void surfaceDestroyed(SurfaceHolder holder) {
            // no-op
        }
    };

    private void initPreview(int width, int height) {
        if (camera != null && previewHolder.getSurface() != null) {
            try {
                camera.setPreviewDisplay(previewHolder);
            } catch (Throwable t) {
                Log.e("GUARD_APP", "Exception in setPreviewDisplay()", t);
            }

            if (!cameraConfigured) {
                Camera.Parameters parameters = camera.getParameters();
                List<Camera.Size> previewSizes = parameters.getSupportedPreviewSizes();
                Camera.Size size = previewSizes.get(0);

                parameters.setPreviewSize(size.width, size.height);
                camera.setParameters(parameters);
                cameraConfigured = true;
            }
        }
    }

    private void startPreview() {
        if (cameraConfigured && camera != null) {
            camera.startPreview();
            inPreview = true;
        }
    }

    private void sendNotification(StolenBikeRecord record) {
        String photoData = record.getImage();

        Log.d("GUARD_APP", String.format("PHOTO: `%s`", photoData));

        Map<String, String> params = new HashMap();
        params.put("beacon_uuid", record.getBeaconId());
        params.put("photo", photoData);

        // FIXME: unique for each GuardApp
        params.put("location_uuid", "1c0eb501-7080-44d1-a1d8-ebc25b9969f7");

        JSONObject reqParams = new JSONObject(params);

        RequestBody body = RequestBody.create(JSON, reqParams.toString());
        Request request = new Request.Builder()
                .url(apiBaseUrl + "/notify")
                .post(body)
                .build();

        final Handler mainHandler = new Handler(getApplicationContext().getMainLooper());
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.d("MainActivity", "onFailure");
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                Log.d("MainActivity", "onResponse");
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        Log.d("MainActivity", "back!");
                    }
                });
            }
        });
    }

    private void fetchStolenUUIDs() {
        Request request = new Request.Builder()
                .url(coachBaseUrl + "/stolen_bike/_design/stolen_bikes/_view/stolen_bikes")
                .build();

        Response response = null;

        try {
            response = client.newCall(request).execute();
            JSONObject json = new JSONObject(response.body().string());

            List<StolenBikeRecord> tmpResults = new ArrayList<>();
            JSONArray rows = json.getJSONArray("rows");

            for (int i = 0; i < rows.length(); i++) {
                JSONObject bikeDoc = rows.getJSONObject(i).getJSONObject("value");

                tmpResults.add(new StolenBikeRecord(bikeDoc.getString("_id"), bikeDoc.getString("beacon_uuid")));
            }

            stolenRecords.clear();
            stolenRecords.addAll(tmpResults);

        } catch (IOException | JSONException e) {
            e.printStackTrace();
        }
    }

    private void enqueueCameraCapture(final StolenBikeRecord record) {
        if (!inPreview)
            return;

        Log.i("GUARD_APP", "Noticed stolen beacon: " + record.getBeaconId());
        Log.i("GUARD_APP", "Capturing a photo...");

        camera.takePicture(null, null, new Camera.PictureCallback() {
            @Override
            public void onPictureTaken(byte[] data, Camera camera) {
                Log.d("GUARD_APP", "Took picture");

                Bitmap bmp = BitmapFactory.decodeByteArray(data, 0, data.length);
                ByteArrayOutputStream stream = new ByteArrayOutputStream();
                bmp.compress(Bitmap.CompressFormat.JPEG, 70, stream);

                String photoDataStr = Base64.encodeToString(stream.toByteArray(), Base64.DEFAULT);

                record.setImage(photoDataStr);

                MainActivity.this.sendNotification(record);
                camera.startPreview();
            }
        });

        Log.i("GUARD_APP", "Sending a notification...");
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        KontaktSDK.initialize("FFglolmQrpUigYJmYaUKlwhEpFuIDCfj");

        proximityManager = new ProximityManager(this);
        proximityManager.setIBeaconListener(createIBeaconListener());

        this.stolenRecords = new ArrayList<>();

        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(new Runnable() {
            public void run() {
                // fetch stolen beacons' UUIDs
                MainActivity.this.fetchStolenUUIDs();
            }
        }, 0, 10, TimeUnit.SECONDS);

        preview = (SurfaceView) findViewById(R.id.preview);
        previewHolder = preview.getHolder();
        previewHolder.addCallback(surfaceCallback);
        previewHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
    }

    @Override
    protected void onStart() {
        super.onStart();
        startScanning();
    }

    @Override
    protected void onStop() {
        proximityManager.stopScanning();
        super.onStop();
    }

    @Override
    protected void onResume() {
        super.onResume();
        LocalBroadcastManager.getInstance(this).registerReceiver(broadcastReceiver, new IntentFilter("photoTaken"));

        camera = Camera.open();
        startPreview();
    }

    @Override
    protected void onPause() {
        super.onPause();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(broadcastReceiver);

        if (inPreview) {
            camera.stopPreview();
        }

        camera.release();
        camera = null;
        inPreview = false;
    }

    @Override
    protected void onDestroy() {
        proximityManager.disconnect();
        proximityManager = null;
        super.onDestroy();
    }

    private void startScanning() {
        proximityManager.connect(new OnServiceReadyListener() {
            @Override
            public void onServiceReady() {
                proximityManager.startScanning();
            }
        });
    }

    private IBeaconListener createIBeaconListener() {
        return new SimpleIBeaconListener() {
            @Override
            public void onIBeaconDiscovered(IBeaconDevice ibeacon, IBeaconRegion region) {
                String shortId = ibeacon.getUniqueId();
                String uuid = ibeacon.getProximityUUID().toString();
                Log.i("GUARD_APP", "IBeacon discovered: " + shortId);

                for (StolenBikeRecord r : stolenRecords) {
                    if (r.getBeaconId().equals(uuid)) {
                        enqueueCameraCapture(r);
                        break;
                    }
                }
            }
        };
    }
}
