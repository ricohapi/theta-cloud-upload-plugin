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

package com.theta360.cloudupload.net;

import android.content.Context;
import android.os.AsyncTask;
import android.util.Base64;
import android.util.Log;
import com.theta360.cloudupload.httpserver.AndroidWebServer;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.spec.AlgorithmParameterSpec;
import java.util.Properties;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import javax.net.ssl.HttpsURLConnection;

/**
 * Upload photos, authentication class.
 */
public abstract class UploadPhotoApi implements Cloneable {
    private final String TAG = "UploadPhotoApi";

    protected RequestCodeTask requestCodeTask;
    protected RequestTokenTask requestTokenTask;
    protected RefreshTokenTask refreshTokenTask;
    protected RequestUserinfoTask requestUserinfoTask;
    protected UploadFileTask uploadFileTask;

    protected Properties props;

    private UploadPhotoApiCallback callback;
    private String userId;
    private String clientId;
    private String clientSecret;
    private String redirectUrl;
    private String userCode;
    private String deviceCode;
    private int expiresIn;
    private int interval;
    private String accessToken;
    private String refreshToken;
    private String uploadData;
    private String uploadDataName;
    private Context con;

    public UploadPhotoApi(Context context) {
        con = context;
        try {
            props = new Properties();
            props.load(con.getAssets().open("api.properties"));
        } catch (Exception ex) {
            Log.d(TAG, ex.getMessage());
        }
    }

    public abstract String getApiType();

    public void setCallback(UploadPhotoApiCallback callback) {
        this.callback = callback;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }
    public String getUserId() {
        return this.userId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    };
    public String getClientId() {
        return this.clientId;
    }

    public void setClientSecret(String clientSecret) {
        this.clientSecret = clientSecret;
    };
    public String getClientSecret() {
        return this.clientSecret;
    }

    public abstract void setApiResult(JSONObject json) throws JSONException;

    public void setRedirectUrl(String redirectUrl) {
        this.redirectUrl = redirectUrl;
    }
    public String getRedirectUrl() {
        return this.redirectUrl;
    }

    public void setUserCode(String userCode) {
        this.userCode = userCode;
    }
    public String getUserCode() {
        return this.userCode;
    }

    public void setDeviceCode(String deviceCode) {
        this.deviceCode = deviceCode;
    }
    public String getDeviceCode() {
        return this.deviceCode;
    }

    public void setExpiresIn(int expiresIn) {
        this.expiresIn = expiresIn;
    }
    public int getExpiresIn() {
        return this.expiresIn;
    }

    public void setInterval(int interval) {
        this.interval = interval;
    }
    public int getInterval() {
        return this.interval;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }
    public String getAccessToken() {
        return this.accessToken;
    }

    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }
    public String getRefreshToken() {
        return this.refreshToken;
    }

    public void setUploadData(String uploadData) {
        this.uploadData = uploadData;
    }

    public void setUploadDataName(String uploadDataName) {
        this.uploadDataName = uploadDataName;
    }

    public abstract void startRequestCode();

    public void cancelRequestCode() {
        if (requestCodeTask != null) {
            requestCodeTask.cancel(true);
            requestCodeTask = null;
        }
    }

    public abstract void startRequestToken();

    public void cancelRequestToken() {
        if (requestTokenTask != null) {
            requestTokenTask.cancel(true);
            requestTokenTask = null;
        }
    }

    public abstract void startRefreshToken();

    public void cancelRefreshToken() {
        if (refreshTokenTask != null) {
            refreshTokenTask.cancel(true);
            refreshTokenTask = null;
        }
    }

    public abstract void startRequestUserinfo();

    public void cancelRequestUserinfo() {
        if (requestUserinfoTask != null) {
            requestUserinfoTask.cancel(true);
            requestUserinfoTask = null;
        }
    }

    public abstract void startUploadFile();

    public void cancelUploadFile() {
        if (uploadFileTask != null) {
            uploadFileTask.cancel(true);
            uploadFileTask = null;
        }
    }

    public String getProperty(String key) {
        try {
            Properties props = new Properties();
            props.load(con.getAssets().open("api.properties"));
            return props.getProperty(key);
        } catch (Exception ex) {
            Log.d(TAG, ex.getMessage());
            return "";
        }
    }

    private class Result {
        private String result;
        private Exception exception;

        public Result(String result) {
            this.result = result;
        }

        public Result(Exception exception) {
            this.exception = exception;
        }

        public String getResult() {
            return result;
        }

        public Exception getException() {
            return exception;
        }
    }

    protected class RequestCodeTask extends AsyncTask<String, Void, Result> {
        @Override
        protected Result doInBackground(String... params) {
            Result result = null;

            if (!(isCancelled() || params == null || params.length <= 0)) {
                try {
                    URL url = new URL(params[0]);
                    result = requestCode(url, params[1]);
                } catch (Exception ex) {
                    result = new Result(ex);
                }
            }

            return result;
        }

        @Override
        protected void onPostExecute(Result result) {
            if (callback != null) {
                if (result.getException() != null) {
                    callback.failedRequestCode(result.getException().getMessage());
                } else if (result.getResult() != null) {
                    callback.completedRequestCode(result.getResult());
                }
            }
        }

        private Result requestCode(URL url, String urlParams) {
            HttpsURLConnection connection = null;
            Result result;
            byte[] postData = urlParams.getBytes(StandardCharsets.UTF_8);

            try {
                connection = (HttpsURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setReadTimeout(10000);
                connection.setConnectTimeout(10000);
                connection.addRequestProperty("Content-Type", "application/x-www-form-urlencoded");
                connection.setDoOutput(true);
                connection.setDoInput(true);
                try (DataOutputStream wr = new DataOutputStream(connection.getOutputStream())) {
                    wr.write(postData);
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
                connection.connect();
                int responseCode = connection.getResponseCode();
                String response;
                if (responseCode == HttpsURLConnection.HTTP_OK) {
                    try (BufferedReader br = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                        StringBuilder sb = new StringBuilder();
                        String line;
                        while ((line = br.readLine()) != null) {
                            sb.append(line);
                        }
                        response = sb.toString();
                    }
                    result = new Result(response);
                } else {
                    result = new Result(new Exception(String.valueOf(responseCode)));
                }

            } catch (Exception ex) {
                result = new Result(ex);

            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }

            return result;
        }
    }

    protected class RequestTokenTask extends AsyncTask<String, Void, Result> {
        @Override
        protected Result doInBackground(String... params) {
            Result result = null;

            if (!(isCancelled() || params == null || params.length <= 0)) {
                try {
                    URL url = new URL(params[0]);
                    result = requestToken(url, params[1]);
                } catch (Exception ex) {
                    result = new Result(ex);
                }
            }

            return result;
        }

        @Override
        protected void onPostExecute(Result result) {
            if (callback != null) {
                if (result.getException() != null) {
                    callback.failedRequestToken(result.getException().getMessage());
                } else if (result.getResult() != null) {
                    callback.completedRequestToken(result.getResult());
                }
            }
        }

        private Result requestToken(URL url, String urlParams) {
            HttpsURLConnection connection = null;
            Result result;
            byte[] postData = urlParams.getBytes(StandardCharsets.UTF_8);

            try {
                connection = (HttpsURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setReadTimeout(10000);
                connection.setConnectTimeout(10000);
                connection.addRequestProperty("Content-Type", "application/x-www-form-urlencoded");
                connection.setDoOutput(true);
                connection.setDoInput(true);
                try(DataOutputStream wr = new DataOutputStream(connection.getOutputStream())) {
                    wr.write(postData);
                }
                connection.connect();
                int responseCode = connection.getResponseCode();
                String response;
                if (responseCode == HttpsURLConnection.HTTP_OK) {
                    try (BufferedReader br = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                        StringBuilder sb = new StringBuilder();
                        String line;
                        while ((line = br.readLine()) != null) {
                            sb.append(line);
                        }
                        response = sb.toString();
                    }
                    result = new Result(response);
                } else {
                    result = new Result(new Exception(String.valueOf(responseCode)));
                }

            } catch (Exception ex) {
                result = new Result(ex);

            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }

            return result;
        }
    }

    protected class RefreshTokenTask extends AsyncTask<String, Void, Result> {
        @Override
        protected Result doInBackground(String... params) {
            Result result = null;

            if (!isCancelled() && params != null && params.length > 0) {
                try {
                    Properties props = new Properties();
                    props.load(con.getAssets().open("api.properties"));
                    URL url = new URL(params[0]);
                    result = refreshToken(url, params[1]);
                } catch (Exception ex) {
                    result = new Result(ex);
                }
            }

            return result;
        }

        @Override
        protected void onPostExecute(Result result) {
            if (callback != null) {
                if (result.getException() != null) {
                    callback.failedRefreshToken(result.getException().getMessage());
                } else if (result.getResult() != null) {
                    callback.completedRefreshToken(result.getResult());
                }
            }
        }

        private Result refreshToken(URL url, String urlParams) {
            HttpsURLConnection connection = null;
            Result result;
            byte[] postData = urlParams.getBytes(StandardCharsets.UTF_8);

            try {
                connection = (HttpsURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setReadTimeout(10000);
                connection.setConnectTimeout(10000);
                connection.addRequestProperty("Content-Type", "application/x-www-form-urlencoded");
                connection.setDoOutput(true);
                connection.setDoInput(true);
                try(DataOutputStream wr = new DataOutputStream(connection.getOutputStream())) {
                    wr.write(postData);
                }
                connection.connect();
                int responseCode = connection.getResponseCode();
                String response;
                if (responseCode == HttpsURLConnection.HTTP_OK) {
                    try (BufferedReader br = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                        StringBuilder sb = new StringBuilder();
                        String line;
                        while ((line = br.readLine()) != null) {
                            sb.append(line);
                        }
                        response = sb.toString();
                    }
                    result = new Result(response);
                } else {
                    result = new Result(new Exception(String.valueOf(responseCode)));
                }

            } catch (Exception ex) {
                result = new Result(ex);

            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }

            return result;
        }
    }

    protected class RequestUserinfoTask extends AsyncTask<String, Void, Result> {
        @Override
        protected Result doInBackground(String... params) {
            Result result = null;

            if (!(isCancelled() || params == null || params.length <= 0)) {
                try {
                    URL url = new URL(params[0]);
                    result = requestUserinfo(url);
                } catch (Exception ex) {
                    result = new Result(ex);
                }
            }

            return result;
        }

        @Override
        protected void onPostExecute(Result result) {
            if (callback != null) {
                if (result.getException() != null) {
                    callback.failedRequestUserinfo(result.getException().getMessage());
                } else if (result.getResult() != null) {
                    callback.completedRequestUserinfo(result.getResult());
                }
            }
        }

        private Result requestUserinfo(URL url) {
            HttpsURLConnection connection = null;
            Result result;

            try {
                connection = (HttpsURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setReadTimeout(10000);
                connection.setConnectTimeout(10000);
                connection.connect();
                int responseCode = connection.getResponseCode();
                String response;
                if (responseCode == HttpsURLConnection.HTTP_OK) {
                    try (BufferedReader br = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                        StringBuilder sb = new StringBuilder();
                        String line;
                        while ((line = br.readLine()) != null) {
                            sb.append(line);
                        }
                        response = sb.toString();
                    }
                    result = new Result(response);
                } else {
                    result = new Result(new Exception(String.valueOf(responseCode)));
                }

            } catch (Exception ex) {
                result = new Result(ex);

            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }

            return result;
        }
    }

    protected class UploadFileTask extends AsyncTask<String, Void, Result> {
        @Override
        protected Result doInBackground(String... params) {
            Result result = null;

            if (!(isCancelled() || params == null || params.length <= 0)) {
                try {
                    Properties props = new Properties();
                    props.load(con.getAssets().open("api.properties"));
                    URL url = new URL(params[0]);
                    result = uploadFile(url);
                } catch (Exception ex) {
                    result = new Result(ex);
                }
            }

            return result;
        }

        @Override
        protected void onPostExecute(Result result) {
            if (callback != null) {
                if (result.getException() != null) {
                    callback.failedUploadFile(result.getException().getMessage());
                } else if (result.getResult() != null) {
                    callback.completedUploadFile(result.getResult());
                }
            }
        }

        private Result uploadFile(URL url) {
            HttpsURLConnection connection = null;
            Result result;

            byte[] byteArray = Base64.decode(uploadData, Base64.DEFAULT);

            try {
                connection = (HttpsURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setReadTimeout(AndroidWebServer.UPLOAD_TIMEOUT_MSEC);
                connection.setConnectTimeout(AndroidWebServer.UPLOAD_TIMEOUT_MSEC);
                connection.addRequestProperty("Content-Type", "image/jpeg");
                connection.addRequestProperty("Slug", uploadDataName);
                connection.setDoOutput(true);
                connection.setDoInput(true);
                try (DataOutputStream wr = new DataOutputStream(connection.getOutputStream())) {
                    wr.write(byteArray);
                }
                connection.connect();
                int responseCode = connection.getResponseCode();
                String response;
                if (responseCode == HttpsURLConnection.HTTP_OK || responseCode == HttpsURLConnection.HTTP_CREATED) {
                    try (BufferedReader br = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                        StringBuilder sb = new StringBuilder();
                        String line;
                        while ((line = br.readLine()) != null) {
                            sb.append(line);
                        }
                        response = sb.toString();
                    }
                    result = new Result(response);
                } else {
                    result = new Result(new Exception(String.valueOf(responseCode)));
                }

            } catch (SocketTimeoutException ex) {
                result = new Result(new Exception(String.valueOf(HttpsURLConnection.HTTP_CLIENT_TIMEOUT)));
            } catch (Exception ex) {
                result = new Result(ex);

            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }

            return result;
        }
    }
}
