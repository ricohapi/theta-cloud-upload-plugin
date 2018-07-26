/**
 * Copyright 2018 Ricoh Company, Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.theta360.cloudupload.httpserver;

import static fi.iki.elonen.NanoHTTPD.Response.Status.NOT_FOUND;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.res.AssetManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.media.ExifInterface;
import android.net.wifi.WifiManager;
import android.os.Environment;
import android.util.Base64;
import android.util.Log;
import com.google.gson.Gson;
import com.theta360.cloudupload.net.UploadPhotoApi;
import com.theta360.cloudupload.net.UploadPhotoApiCallback;
import com.theta360.cloudupload.net.UploadPhotoApiFactory;
import com.theta360.cloudupload.receiver.ChangeLedReceiver;
import com.theta360.cloudupload.receiver.FinishApplicationReceiver;
import com.theta360.cloudupload.receiver.SpecifiedResultReceiver;
import com.theta360.cloudupload.receiver.UploadStatusReceiver;
import com.theta360.cloudupload.settingdata.SettingData;
import org.json.JSONException;
import org.json.JSONObject;
import fi.iki.elonen.NanoHTTPD;
import fi.iki.elonen.NanoHTTPD.Response.Status;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;
import javax.net.ssl.HttpsURLConnection;
import timber.log.Timber;

/**
 * Provide web server function
 */
public class AndroidWebServer extends Activity {

    public static final int TIMEOUT_DEFAULT_MINUTE = -1;

    private final String DCIM_PATH = Environment.getExternalStorageDirectory().getAbsolutePath() + "/" + Environment.DIRECTORY_DCIM + "/";
    private final String PICTURES_PATH = Environment.getExternalStorageDirectory().getAbsolutePath() + "/" + Environment.DIRECTORY_PICTURES + "/";

    public static final int UPLOAD_TIMEOUT_MSEC = 60000;
    private final int UPLOAD_RETRY_WAIT_MSEC = 30000;
    private final int REFRESH_COUNT_MAX = 3;

    private Theta360SQLiteOpenHelper helper;
    private SQLiteDatabase dbObject;

    private static final int PORT = 8888;
    private SimpleHttpd server;

    private Context con;
    private UploadProcess uploadProcess;

    private final Object lock = new Object();
    private Boolean requested;
    private UploadPhotoApi uploadPhotoApi;
    private boolean isReady = false;
    private String userId = null;
    private String refreshToken = null;

    private List<PhotoInformation> uploadedPhotoList;
    private List<PhotoInformation> uploadingPhotoList;
    private PhotoInformation uploadingPhoto;
    private Boolean isSucceedUpload;
    private int errorCode;
    private List<PhotoInformation> specifiedPhotoList;
    private String errorType;
    private boolean isUploading = false;
    private int uploadAllNumber;
    private int uploadCurrentNumber;

    public AndroidWebServer(Context context) {
        con = context;
        create();
    }

    /**
     * {@inheritDoc}
     *
     * Create
     */
    @SuppressLint({"SetTextI18n", "LongLogTag"})
    public void create() {

        Log.d("AndroidWebServerActivity", "onCreate");
        WifiManager wifiManager = (WifiManager) con.getSystemService(Context.WIFI_SERVICE);

        assert wifiManager != null;
        int ipAddress = wifiManager.getConnectionInfo().getIpAddress();
        @SuppressLint("DefaultLocale") final String formattedIpAddress = String.format("%d.%d.%d.%d", (ipAddress & 0xff), (ipAddress >> 8 & 0xff), (ipAddress >> 16 & 0xff), (ipAddress >> 24 & 0xff));

        Timber.i("Launch server with IP [" + formattedIpAddress + "].");

        try {
            server = new SimpleHttpd();
            server.start();
            Log.i("AndroidWebServerActivity", "Start server");
        } catch (Exception e) {
            e.printStackTrace();
        }

        helper = new Theta360SQLiteOpenHelper(con);

        clearRequested();

        dbObject = helper.getWritableDatabase();
        uploadedPhotoList = new ArrayList();
        updateUploadInfo();
    }

    /**
     * {@inheritDoc}
     *
     * Discard
     */
    public void destroy() {
        if (server != null) {
            server.stop();
            Log.i("AndroidWebServerActivity", "Stop server");
        }
    }

    /**
     * Get the flag for receiving a request
     *
     * @return Flag requested
     */
    public Boolean isRequested() {
        return requested;
    }

    /**
     * Clear the flag for receiving a request
     */
    public void clearRequested() {
        requested = false;
    }

    /**
     * Check ready for uploading
     */
    public boolean getIsReady() {
        return isReady;
    }

    /**
     * Execute / stop upload processing
     */
    public void startUpload() {
        if (uploadProcess != null) {
            uploadProcess.start();
        }
    }

    /**
     * End thread of upload processing
     */
    public void exitUpload() {
        if (uploadProcess != null) {
            uploadProcess.exit();
        }
    }

    /**
     * Create thread of upload processing
     */
    public void createUploadProcess() {
        uploadProcess = new UploadProcess();

        ExecutorService uploadProcessService = null;
        try {
            uploadProcessService = Executors.newSingleThreadExecutor();
            uploadProcessService.execute(uploadProcess);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            uploadProcessService.shutdown();
        }
    }

    private class UploadProcess implements Runnable {
        private boolean isStartUpload = false;
        private boolean isExit = false;

        /**
         * Execute / stop upload processing
         */
        public void start() {
            isStartUpload = true;
        }

        /**
         * End thread
         */
        public void exit() {
            isExit = true;
        }

        @Override
        public void run() {
            while (!isExit) {
                if (!isStartUpload) {
                    continue;
                }
                updateUploadInfo();
                if (isReady) {
                    if (server.uploadFileService == null) {
                        int refreshCount = 0;
                        boolean refreshResult = false;
                        while(!refreshResult && refreshCount < REFRESH_COUNT_MAX) {
                            refreshResult = server.hasRefreshToken();
                            refreshCount++;
                        }
                        server.startUploadFile();
                    } else {
                        server.uploadFileService.shutdownNow();
                        server.uploadFileService = null;
                    }
                }
                isStartUpload = false;
            }
        }
    }

    /**
     * Upload specified image by intent
     * @param photoList Upload image list
     */
    public void uploadSpecifiedPhotoList(List<String> photoList) {
        setSpecifiedPhotoList(photoList);
        startUploadSpecifiedPhotoList();
    }

    private void setSpecifiedPhotoList(List<String> photoList) {
        specifiedPhotoList = new ArrayList();
        PhotoInformation photoInformation;
        for (String path : photoList) {
            if (!(path.endsWith(".JPG") || path.endsWith(".jpg") || path.endsWith(".jpeg"))) {
                continue;
            }
            if (!(new File(path).isFile())) {
                continue;
            }

            try {
                ExifInterface exifInterface = new ExifInterface(path);
                String datetime = exifInterface.getAttribute(ExifInterface.TAG_DATETIME);
                photoInformation = new PhotoInformation();
                photoInformation.setPath(path);
                photoInformation.setDatetime(datetime);
                specifiedPhotoList.add(photoInformation);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void startUploadSpecifiedPhotoList() {
        UploadSpecifiedPhotoList uploadSpecifiedPhotoList = new UploadSpecifiedPhotoList();

        ExecutorService uploadSpecifiedPhotoListService = null;
        try {
            uploadSpecifiedPhotoListService = Executors.newSingleThreadExecutor();
            uploadSpecifiedPhotoListService.execute(uploadSpecifiedPhotoList);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            uploadSpecifiedPhotoListService.shutdown();
        }
    }

    private class UploadSpecifiedPhotoList implements Runnable {
        @Override
        public void run() {
            errorType = "";
            updateUploadInfo();
            if (refreshToken == null || refreshToken.isEmpty()) {
                errorType = ErrorType.NOT_SETTINGS.getType();
            } else {
                int refreshCount = 0;
                boolean refreshResult = false;
                while(!refreshResult && refreshCount < REFRESH_COUNT_MAX) {
                    refreshResult = server.hasRefreshToken();
                    refreshCount++;
                }
                server.hasUploadFile();
            }
            Intent intent = new Intent(SpecifiedResultReceiver.SPECIFED_RESULT);
            intent.putExtra(SpecifiedResultReceiver.RESULT, errorType);
            con.sendBroadcast(intent);
        }
    }

    /**
     * Converts an InputStream to a string.
     *
     * @param is Source InputStream
     * @return Converted character string
     */
    private String inputStreamToString(InputStream is) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(is));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) {
            sb.append(line);
        }
        br.close();

        return sb.toString();
    }

    /**
     * Converts a string to InputStream.
     *
     * @param str Source character string
     * @return Converted InputStream
     */
    private InputStream stringToInputStream(String str) throws UnsupportedEncodingException {
        return new ByteArrayInputStream(str.getBytes("utf-8"));
    }

    private void updateUploadInfo() {
        // Confirm whether the upload destination authentication information is stored in the DB
        Cursor cursor = dbObject.query("auth_information", null, null, null, null, null, null, null);
        try {
            if (cursor.moveToNext()) {
                refreshToken = cursor.getString(cursor.getColumnIndex("refresh_token"));
                userId = cursor.getString(cursor.getColumnIndex("user_id"));
                String apiType = cursor.getString(cursor.getColumnIndex("api_type"));
                if (!apiType.isEmpty() && (uploadPhotoApi == null || !uploadPhotoApi.getApiType().equals(apiType))) {
                    uploadPhotoApi = UploadPhotoApiFactory.createUploadPhotoApi(con, apiType);
                    // Update list of uploaded photos
                    updateUploadedPhotoList();
                }
            } else {
                // Create a record if there is no record in DB
                ContentValues values;
                values = new ContentValues();
                values.put("refresh_token", "");
                values.put("user_id", "");
                values.put("api_type", "");
                dbObject.insert("auth_information", null, values);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        } finally {
            cursor.close();
        }

        if (refreshToken != null && !refreshToken.isEmpty()) {
            isReady = true;
        } else {
            isReady = false;
        }
    }

    private void updateUploadedPhotoList() {
        Cursor cursor = dbObject.query("uploaded_photo", null, "api_type = ?", new String[]{uploadPhotoApi.getApiType()}, null, null, null, null);
        uploadedPhotoList = new ArrayList();
        try {
            while (cursor.moveToNext()) {
                PhotoInformation uploadedPhoto = new PhotoInformation();
                uploadedPhoto.setPath(cursor.getString(cursor.getColumnIndex("path")));
                uploadedPhoto.setDatetime(cursor.getString(cursor.getColumnIndex("datetime")));
                uploadedPhoto.setUserId(cursor.getString(cursor.getColumnIndex("user_id")));
                uploadedPhotoList.add(uploadedPhoto);
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw new SQLiteException("[select data] Unexpected exception");
        } finally {
            cursor.close();
        }
    }

    private void changeReadyLed() {
        Intent intent = new Intent(ChangeLedReceiver.CHANGE_READY_LED);
        con.sendBroadcast(intent);
    }

    private void changeTransferringLed() {
        Intent intent = new Intent(ChangeLedReceiver.CHANGE_TRANSFERRING_LED);
        con.sendBroadcast(intent);
    }

    private void changeStopTransferringLed() {
        Intent intent = new Intent(ChangeLedReceiver.CHANGE_STOP_TRANSFERRING_LED);
        con.sendBroadcast(intent);
    }

    private void changeErrorLed() {
        Intent intent = new Intent(ChangeLedReceiver.CHANGE_ERROR_LED);
        con.sendBroadcast(intent);
    }

    private void notificationStartUpload() {
        isUploading = true;
        Intent intent = new Intent(UploadStatusReceiver.UPLOAD_START);
        con.sendBroadcast(intent);
    }

    private void notificationEndUpload() {
        isUploading = false;
        Intent intent = new Intent(UploadStatusReceiver.UPLOAD_END);
        con.sendBroadcast(intent);
    }

    /**
     * HTTP communication implementation class
     *
     */
    private class SimpleHttpd extends NanoHTTPD implements UploadPhotoApiCallback {
        private final Logger LOG = Logger.getLogger(SimpleHttpd.class.getName());
        private ExecutorService uploadFileService = null;
        private ExecutorService pollingGetTokenService = null;

        /**
         * Constructor
         */
        public SimpleHttpd() throws IOException {
            super(PORT);
        }

        /**
         * Response to request
         *
         * @param session session
         * @return resource
         */
        @Override
        public Response serve(IHTTPSession session) {
            Method method = session.getMethod();
            String uri = session.getUri();
            this.LOG.info(method + " '" + uri + "' ");

            if ("/".equals(uri)) {
                uri = "index.html";
            }

            // In the case of NanoHTTPD, since the POST request is stored in a temporary file, a buffer is given for reading again
            Map<String, String> tmpRequestFile = new HashMap<>();
            if (Method.POST.equals(method)) {
                try {
                    session.parseBody(tmpRequestFile);
                } catch (IOException e) {
                    return newFixedLengthResponse(Status.INTERNAL_ERROR, MIME_PLAINTEXT, "SERVER INTERNAL ERROR: IOException: " + e.getMessage());
                } catch (ResponseException e) {
                    return newFixedLengthResponse(e.getStatus(), MIME_PLAINTEXT, e.getMessage());
                }
            }
            Map<String, String> params = session.getParms();

            if (params.get("google_auth") != null) {
                updateUploadInfo();
                if (isReady) {
                    changeReadyLed();
                }
                try {
                    // Start upload destination authentication
                    uploadPhotoApi = UploadPhotoApiFactory.createUploadPhotoApi(con, UploadPhotoApiFactory.GOOGLE_PHOTO);
                    doAuthorization();
                    uri = "/google_auth.html";
                } catch (Exception e) {
                    e.printStackTrace();
                    throw new SQLiteException("[update data] unexpected exception");
                }
            } else if (params.get("timeout_page") != null) {
                uri = "/timeout.html";
            } else if (params.get("no_operation_timeout_minute") != null) {
                ContentValues values = new ContentValues();
                values.put("no_operation_timeout_minute", params.get("no_operation_timeout_minute"));
                dbObject.update("theta360_setting", values, null, null);
                requested = true;
            }

            return serveFile(uri);
        }

        private void startPollingGetToken() {
            PollingGetToken pollingGetToken = new PollingGetToken();

            pollingGetTokenService = null;
            try {
                pollingGetTokenService = Executors.newSingleThreadExecutor();
                pollingGetTokenService.execute(pollingGetToken);
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                pollingGetTokenService.shutdown();
            }
        }

        private class PollingGetToken implements Runnable {
            @Override
            public void run() {
                long pollingEndTimeMillis = System.currentTimeMillis() + uploadPhotoApi.getExpiresIn() * 1000;
                long pollingIntervalMillis = uploadPhotoApi.getInterval() * 1000;
                boolean isGotAccessToken = false;
                try {
                    while (System.currentTimeMillis() < pollingEndTimeMillis) {
                        Timber.d("polling get token");
                        if (!isGotAccessToken && hasAccessToken()) {
                            isGotAccessToken = true;
                        }
                        if (isGotAccessToken && hasUserinfo()) {
                            updateAuthDb();
                            break;
                        }
                        Thread.sleep(pollingIntervalMillis);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        private void startUploadFile() {
            UploadFile uploadFile = new UploadFile();

            uploadFileService = null;
            try {
                uploadFileService = Executors.newSingleThreadExecutor();
                uploadFileService.execute(uploadFile);
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                uploadFileService.shutdown();
            }
        }

        private class UploadFile implements Runnable {
            @Override
            public void run() {
                if (!hasUploadFile()) {
                    changeErrorLed();
                }
            }
        }

        private void doAuthorization() {
            uploadPhotoApi.setCallback(this);
            uploadPhotoApi.startRequestCode();
            synchronized (lock) {
                try {
                    lock.wait();
                } catch (InterruptedException ex) {
                    ex.printStackTrace();
                }
            }
        }

        private boolean hasAccessToken() {
            // Get a token
            uploadPhotoApi.setCallback(this);
            uploadPhotoApi.startRequestToken();
            synchronized (lock) {
                try {
                    lock.wait();
                } catch (InterruptedException ex) {
                    ex.printStackTrace();
                }
            }

            if (uploadPhotoApi.getAccessToken() == null || uploadPhotoApi.getAccessToken().isEmpty()) {
                return false;
            }
            changeReadyLed();
            refreshToken = uploadPhotoApi.getRefreshToken();
            return true;
        }

        private boolean hasRefreshToken() {
            uploadPhotoApi.setRefreshToken(refreshToken);
            uploadPhotoApi.setCallback(this);
            uploadPhotoApi.startRefreshToken();
            synchronized (lock) {
                try {
                    lock.wait();
                } catch (InterruptedException ex) {
                    ex.printStackTrace();
                }
            }

            if (uploadPhotoApi.getAccessToken() == null || uploadPhotoApi.getAccessToken().isEmpty()) {
                return false;
            }
            return true;
        }

        private boolean hasUserinfo() {
            uploadPhotoApi.setCallback(this);
            uploadPhotoApi.startRequestUserinfo();
            synchronized (lock) {
                try {
                    lock.wait();
                } catch (InterruptedException ex) {
                    ex.printStackTrace();
                }
            }

            if (uploadPhotoApi.getUserId() == null || uploadPhotoApi.getUserId().isEmpty()) {
                return false;
            }
            userId = uploadPhotoApi.getUserId();
            return true;
        }

        private boolean hasUploadFile() {
            notificationStartUpload();
            changeTransferringLed();
            uploadPhotoApi.setUserId(userId);
            boolean result = true;
            if (specifiedPhotoList == null || specifiedPhotoList.size() == 0) {
                uploadingPhotoList = getPhotoList(DCIM_PATH);
                uploadingPhotoList.addAll(getPhotoList(PICTURES_PATH));
            } else {
                uploadingPhotoList = new ArrayList();
                for (PhotoInformation photoInformation : specifiedPhotoList) {
                    photoInformation.setUserId(userId);
                    uploadingPhotoList.add(photoInformation);
                }
            }
            uploadAllNumber = uploadingPhotoList.size();
            uploadCurrentNumber = 0;
            Timber.i("uploading " + uploadAllNumber + " files");

            if (uploadPhotoApi.getAccessToken() == null || uploadPhotoApi.getAccessToken().isEmpty()) {
                Timber.e("Access token is empty");
                changeReadyLed();
                uploadingPhotoList = null;
                specifiedPhotoList = null;
                uploadFileService = null;
                notificationEndUpload();
                return false;
            }

            boolean isNotAuthorization = false;
            SettingData settingData = readSettingData();
            int timeoutMSec = settingData.getNoOperationTimeoutMinute() * 60 * 1000;
            try {
                boolean isFirst = true;
                for (PhotoInformation photoInformation : uploadingPhotoList) {
                    if (isFirst) {
                        isFirst = false;
                    } else {
                        uploadCurrentNumber++;
                    }
                    uploadingPhoto = photoInformation;
                    isSucceedUpload = null;
                    try {
                        File file = new File(photoInformation.getPath());
                        byte[] byteArray = new byte[(int) file.length()];
                        FileInputStream fis = new FileInputStream(file);
                        fis.read(byteArray);
                        fis.close();
                        String encodedData = Base64.encodeToString(byteArray, Base64.DEFAULT);

                        uploadPhotoApi.setUploadData(encodedData);
                        uploadPhotoApi.setUploadDataName(file.getName());
                        uploadPhotoApi.startUploadFile();
                    } catch (Exception ex) {
                        ex.printStackTrace();
                        result = false;
                        continue;
                    }

                    long startUploadingMSec = System.currentTimeMillis();
                    while (true) {
                        if (isSucceedUpload == null) {
                            if (Thread.interrupted()) {
                                throw new InterruptedException("");
                            }
                            continue;
                        } else if (isSucceedUpload) {
                            break;
                        } else {
                            if (errorCode == HttpsURLConnection.HTTP_BAD_REQUEST ||
                                    errorCode == HttpsURLConnection.HTTP_FORBIDDEN) {
                                errorType = ErrorType.BAD_SETTINGS.getType();
                                isNotAuthorization = true;
                            } else {
                                if (timeoutMSec > 0 && System.currentTimeMillis() - startUploadingMSec > timeoutMSec) {
                                    errorType = ErrorType.TIMEOUT.getType();
                                    break;
                                }
                                changeStopTransferringLed();
                                Thread.sleep(UPLOAD_RETRY_WAIT_MSEC);
                                changeTransferringLed();
                                uploadPhotoApi.startUploadFile();
                                continue;
                            }
                        }
                        break;
                    }
                    if (isNotAuthorization) {
                        result = false;
                        break;
                    }
                }
                uploadCurrentNumber++;
                // Wait 3 seconds + alpha for 3 seconds to flash the LED in the upload completed state
                Thread.sleep(3200);
            } catch (InterruptedException e){
                Thread.currentThread().interrupt();
            }
            changeReadyLed();
            uploadingPhotoList = null;
            specifiedPhotoList = null;
            uploadFileService = null;
            notificationEndUpload();

            return result;
        }

        private List<PhotoInformation> getPhotoList(String searchPath) {
            List photoList = new ArrayList();

            String path;
            for (File file: new File(searchPath).listFiles()) {
                path = file.getAbsolutePath();
                if (file.isFile()) {
                    if (!(path.endsWith(".JPG") || path.endsWith(".jpg") || path.endsWith(".jpeg"))) {
                        continue;
                    }
                    try {
                        ExifInterface exifInterface = new ExifInterface(path);
                        String datetime = exifInterface.getAttribute(ExifInterface.TAG_DATETIME);
                        PhotoInformation uploadingPhoto = new PhotoInformation();
                        uploadingPhoto.setPath(path);
                        uploadingPhoto.setDatetime(datetime);
                        uploadingPhoto.setUserId(userId);
                        if (!uploadedPhotoList.contains(uploadingPhoto)) {
                            photoList.add(uploadingPhoto);
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                } else if (file.isDirectory()) {
                    photoList.addAll(getPhotoList(path));
                }
            }

            return photoList;
        }

        /**
         * Sending files
         *
         * @param uri requested url
         * @return resource
         */
        private Response serveFile(String uri) {

            if (uri.equals("/login")) {
                if (!(pollingGetTokenService == null || pollingGetTokenService.isShutdown())) {
                    pollingGetTokenService.shutdownNow();
                }
                startPollingGetToken();
                return newChunkedResponse(Status.OK, "text/html", null);
            } else if (uri.equals("/logout")) {
                if (!(pollingGetTokenService == null || pollingGetTokenService.isShutdown())) {
                    pollingGetTokenService.shutdownNow();
                }
                refreshToken = "";
                userId = "";
                updateAuthDb();

                doAuthorization();
                InputStream destInputStream = null;
                JSONObject data = new JSONObject();
                try {
                    data.put("user_code", uploadPhotoApi.getUserCode());
                    data.put("google_auth_url", uploadPhotoApi.getRedirectUrl());
                    destInputStream = stringToInputStream(data.toString());
                } catch (Exception e) {
                    e.printStackTrace();
                }

                return newChunkedResponse(Status.OK, "text/html", destInputStream);
            } else if (uri.equals("/reacquire")) {
                doAuthorization();
                InputStream destInputStream = null;
                JSONObject data = new JSONObject();
                try {
                    data.put("user_code", uploadPhotoApi.getUserCode());
                    data.put("google_auth_url", uploadPhotoApi.getRedirectUrl());
                    destInputStream = stringToInputStream(data.toString());
                } catch (Exception e) {
                    e.printStackTrace();
                }

                return newChunkedResponse(Status.OK, "text/html", destInputStream);
            } else if (uri.equals("/check_logged_in")) {
                InputStream destInputStream = null;
                try {
                    if (userId == null || userId.isEmpty()) {
                        destInputStream = stringToInputStream("0");
                    } else {
                        destInputStream = stringToInputStream("1");
                    }
                } catch (UnsupportedEncodingException e) {
                    e.printStackTrace();
                }
                return newChunkedResponse(Status.OK, "text/html", destInputStream);
            } else if (uri.equals("/cancel")) {
                if (!(pollingGetTokenService == null || pollingGetTokenService.isShutdown())) {
                    pollingGetTokenService.shutdownNow();
                }
                return newChunkedResponse(Status.OK, "text/html", null);
            } else if (uri.equals("/done")) {
                return newChunkedResponse(Status.OK, "text/html", null);
            } else if (uri.equals("/upload")) {
                if (isReady) {
                    if (uploadFileService == null) {
                        int refreshCount = 0;
                        boolean refreshResult = false;
                        while(!refreshResult && refreshCount < REFRESH_COUNT_MAX) {
                            refreshResult = hasRefreshToken();
                            refreshCount++;
                        }
                        startUploadFile();
                    } else {
                        uploadFileService.shutdownNow();
                        uploadFileService = null;
                    }
                }
                return newChunkedResponse(Status.OK, "text/html", null);
            } else if (uri.equals("/check_uploading")) {
                InputStream destInputStream = null;
                Map<String, Integer> map = new HashMap<>();
                Gson gson = new Gson();
                try {
                    if (isUploading) {
                        map.put("isUploading", 1);
                        map.put("current", uploadCurrentNumber);
                        map.put("all", uploadAllNumber);
                    } else {
                        map.put("isUploading", 0);
                        map.put("current", 0);
                        map.put("all", 0);
                    }
                    destInputStream = stringToInputStream(gson.toJson(map));
                } catch (UnsupportedEncodingException e) {
                    e.printStackTrace();
                }
                return newChunkedResponse(Status.OK, "text/html", destInputStream);
            } else if (uri.equals("/end")) {
                Intent intent = new Intent(FinishApplicationReceiver.FINISH_APPLICATION);
                con.sendBroadcast(intent);
                return newChunkedResponse(Status.OK, "text/html", null);
            }

            String filename = uri;
            if (uri.substring(0, 1).equals("/")) {
                filename = filename.substring(1);
            }

            AssetManager as = con.getResources().getAssets();
            InputStream fis = null;
            try {
                fis = as.open(filename);
            } catch (Exception e) {

            }

            if (uri.endsWith(".ico")) {
                return newChunkedResponse(Status.OK, "image/x-icon", fis);
            } else if (uri.endsWith(".png") || uri.endsWith(".PNG")) {
                return newChunkedResponse(Status.OK, "image/png", fis);
            } else if (uri.endsWith(".js")) {
                return newChunkedResponse(Status.OK, "application/javascript", fis);
            } else if (uri.endsWith(".properties")) {
                return newChunkedResponse(Status.OK, "text/html", fis);
            } else if (uri.endsWith(".css")) {
                return newChunkedResponse(Status.OK, "text/html", fis);
            } else if (uri.endsWith(".html") || uri.endsWith(".htm")) {
                if (uri.equals("/google_auth.html")) {
                    String srcString = null;
                    try (BufferedReader br = new BufferedReader(new InputStreamReader(fis))) {
                        StringBuilder sb = new StringBuilder();
                        String line;
                        while ((line = br.readLine()) != null) {
                            sb.append(line);
                        }
                        srcString = sb.toString();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                    if (userId == null || userId.isEmpty()) {
                        srcString = srcString.replaceFirst("#IS_LOGGED_IN#", "0");
                    } else {
                        srcString = srcString.replaceFirst("#IS_LOGGED_IN#", "1");
                    }
                    srcString = srcString.replaceFirst("#GOOGLE_PHOTO_USER_CODE#", uploadPhotoApi.getUserCode());
                    srcString = srcString.replaceFirst("#GOOGLE_PHOTO_CODE_AUTH_URL#", uploadPhotoApi.getRedirectUrl());
                    try (InputStream destInputStream = new ByteArrayInputStream(srcString.getBytes("UTF-8"))) {
                        return newChunkedResponse(Status.OK, "text/html", destInputStream);
                    } catch (Exception ex) {
                        ex.printStackTrace();
                        return newChunkedResponse(Status.OK, "text/html", fis);
                    }
                } else if (uri.equals("/timeout.html")) {
                    SettingData settingData = readSettingData();
                    String srcString = null;
                    try (BufferedReader br = new BufferedReader(new InputStreamReader(fis))) {
                        StringBuilder sb = new StringBuilder();
                        String line;
                        while ((line = br.readLine()) != null) {
                            sb.append(line);
                        }
                        srcString = sb.toString();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                    srcString = srcString.replaceFirst("#NO_OPERATION_TIMEOUT_MINUTE#", String.valueOf(settingData.getNoOperationTimeoutMinute()));
                    try (InputStream destInputStream = new ByteArrayInputStream(srcString.getBytes("UTF-8"))) {
                        return newChunkedResponse(Status.OK, "text/html", destInputStream);
                    } catch (Exception ex) {
                        ex.printStackTrace();
                        return newChunkedResponse(Status.OK, "text/html", fis);
                    }
                } else {
                    updateUploadInfo();
                    SettingData settingData = readSettingData();

                    String srcString = null;
                    try {
                        srcString = inputStreamToString(fis);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                    String JSCode = "\\$(function() {\n"
                            + "\\$('#no_operation_timeout_minute_text').val('" + settingData.getNoOperationTimeoutMinute() + "');";
                    if (userId != null) {
                        JSCode += "\\$('#upload_user_id').text('" + userId + "');";
                    }
                    if (!isReady) {
                        JSCode += "\\$('#upload_btn').prop('disabled', true);";
                    }
                    JSCode += "});";
                    String newSrcString = srcString.replaceFirst("#JS_INJECTION#", JSCode);

                    InputStream destInputStream = null;
                    try {
                        destInputStream = stringToInputStream(newSrcString);
                    } catch (UnsupportedEncodingException e) {
                        e.printStackTrace();
                    }

                    return newChunkedResponse(Status.OK, "text/html", destInputStream);
                }
            } else {
                return newFixedLengthResponse(NOT_FOUND, "text/plain", uri);
            }
        }

        /**
         * Read configuration data from DB and return
         */
        private SettingData readSettingData() {
            SettingData settingData;

            Cursor cursor = dbObject.query("theta360_setting", null, null, null, null, null, null, null);
            try {
                settingData = new SettingData();
                if (cursor.moveToNext()) {
                    settingData.setNoOperationTimeoutMinute(cursor.getInt(cursor.getColumnIndex("no_operation_timeout_minute")));
                    settingData.setStatus(cursor.getString(cursor.getColumnIndex("status")));
                    settingData.setIsUploadMovie(cursor.getInt(cursor.getColumnIndex("is_upload_movie")));
                }
            } catch (Exception e) {
                e.printStackTrace();
                throw new SQLiteException("[select data] Unexpected exception");
            } finally {
                cursor.close();
            }

            return settingData;
        }

        private void updateAuthDb() {
            try {
                // Register authentication information in DB
                ContentValues values = new ContentValues();
                values.put("refresh_token", refreshToken);
                values.put("user_id", userId);
                values.put("api_type", uploadPhotoApi.getApiType());
                dbObject.update("auth_information", values, null, null);
                Timber.i("saved tokens to DB");
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void completedRequestCode(String result) {
            try {
                JSONObject json = new JSONObject(result);
                uploadPhotoApi.setApiResult(json);
                synchronized (lock) {
                    lock.notify();
                }
            } catch (JSONException ex) {
                ex.printStackTrace();
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void failedRequestCode(String result) {
            uploadPhotoApi.setRedirectUrl(new String());
            uploadPhotoApi.setDeviceCode(new String());
            uploadPhotoApi.setUserCode(new String());
            synchronized (lock) {
                lock.notify();
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void completedRequestToken(String result) {
            try {
                JSONObject json = new JSONObject(result);
                uploadPhotoApi.setApiResult(json);
                synchronized (lock) {
                    lock.notify();
                }
            } catch (JSONException ex) {
                ex.printStackTrace();
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void failedRequestToken(String result) {
            uploadPhotoApi.setAccessToken(new String());
            uploadPhotoApi.setRefreshToken(new String());
            synchronized (lock) {
                lock.notify();
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void completedRefreshToken(String result) {
            try {
                JSONObject json = new JSONObject(result);
                uploadPhotoApi.setApiResult(json);
                synchronized (lock) {
                    lock.notify();
                }
            } catch (JSONException ex) {
                ex.printStackTrace();
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void failedRefreshToken(String result) {
            synchronized (lock) {
                lock.notify();
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void completedRequestUserinfo(String result) {
            try {
                JSONObject json = new JSONObject(result);
                if (json.has("email")) {
                    String email = json.getString("email");
                    uploadPhotoApi.setUserId(email.split("@")[0]);
                } else {
                    uploadPhotoApi.setUserId("");
                }
                synchronized (lock) {
                    lock.notify();
                }
            } catch (JSONException ex) {
                ex.printStackTrace();
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void failedRequestUserinfo(String result) {
            uploadPhotoApi.setUserId("");
            synchronized (lock) {
                lock.notify();
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void completedUploadFile(String result) {
            Timber.i("succeeded upload file : " + uploadingPhoto.getPath());
            insertUploadedPhotoDb();
            isSucceedUpload = true;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void failedUploadFile(String result) {
            Timber.i("failed upload file : " + uploadingPhoto.getPath() + " by " + result);

            try {
                errorCode = Integer.parseInt(result);
            } catch (NumberFormatException ex) {
            }

            isSucceedUpload = false;
        }

        private void insertUploadedPhotoDb() {
            try {
                ContentValues values;
                values = new ContentValues();
                values.put("path", uploadingPhoto.getPath());
                values.put("datetime", uploadingPhoto.getDatetime());
                values.put("user_id", uploadingPhoto.getUserId());
                values.put("api_type", uploadPhotoApi.getApiType());
                dbObject.insert("uploaded_photo", null, values);
                uploadedPhotoList.add(uploadingPhoto);
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }
}
