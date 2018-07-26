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

package com.theta360.cloudupload;

import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.icu.text.DateFormat;
import android.icu.text.SimpleDateFormat;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.WindowManager;

import com.theta360.cloudupload.Util.LogUtilDebugTree;
import com.theta360.cloudupload.httpserver.AndroidWebServer;
import com.theta360.cloudupload.httpserver.ErrorType;
import com.theta360.cloudupload.httpserver.Theta360SQLiteOpenHelper;
import com.theta360.cloudupload.receiver.ChangeLedReceiver;
import com.theta360.cloudupload.receiver.FinishApplicationReceiver;
import com.theta360.cloudupload.receiver.SpecifiedResultReceiver;
import com.theta360.cloudupload.receiver.UploadStatusReceiver;
import com.theta360.pluginlibrary.activity.PluginActivity;
import com.theta360.pluginlibrary.callback.KeyCallback;
import com.theta360.pluginlibrary.receiver.KeyReceiver;
import com.theta360.pluginlibrary.values.LedColor;
import com.theta360.pluginlibrary.values.LedTarget;
import java.io.File;
import java.io.FileFilter;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import timber.log.Timber;

public class MainActivity extends PluginActivity {

    private final DateFormat df = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
    private final int LOG_DELETE_ELAPSED_DAYS = 30;

    private Context con;
    private AndroidWebServer webUI;
    private int noOperationTimeoutMSec = AndroidWebServer.TIMEOUT_DEFAULT_MINUTE * 60 * 1000;
    private ShutDownTimer shutDownTimer;
    private SettingPolling settingPolling;
    private ExecutorService shutDownTimerService = null;
    private ExecutorService settingPollingService = null;
    private static Object lock;

    private ChangeLedReceiver mChangeLedReceiver;
    private ChangeLedReceiver.Callback onChangeLedReceiver = new ChangeLedReceiver.Callback() {
        @Override
        public void callReadyCallback() {
            changeReadyLED();
        }
        @Override
        public void callTransferringCallback() {
            changeTransferringLED();
        }
        @Override
        public void callStopTransferringCallback() {
            changeStopTransferringLED();
        }
        @Override
        public void callErrorCallback() {
            playPPPSoundWithErrorLED();
        }
    };

    private UploadStatusReceiver mUploadStatusReceiver;
    private UploadStatusReceiver.Callback onUploadStatusReceiver = new UploadStatusReceiver.Callback() {
        @Override
        public void callStartCallback() {
            shutDownTimer.reset(true, noOperationTimeoutMSec);
        }
        @Override
        public void callEndCallback() {
            shutDownTimer.reset(false, noOperationTimeoutMSec);
        }
    };

    private SpecifiedResultReceiver mSpecifiedResultReceiver;
    private SpecifiedResultReceiver.Callback onSpecifiedResultReceiver = new SpecifiedResultReceiver.Callback() {
        @Override
        public void callSpecifiedResultCallback(String result) {
            Intent data = new Intent();
            Bundle bundle = new Bundle();
            ErrorType errorType = ErrorType.getType(result);
            bundle.putInt("ResultCode", errorType.getCode());
            bundle.putString("ResultMessage", errorType.getMessage());
            data.putExtras(bundle);
            setResult(RESULT_OK, data);
            finish();
        }
    };

    private FinishApplicationReceiver mFinishApplicationReceiver;
    private FinishApplicationReceiver.Callback onFinishApplicationReceiver = new FinishApplicationReceiver.Callback() {
        @Override
        public void callFinishApplicationCallback() {
            exitProcess();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        lock = new Object();
        con = getApplicationContext();

        // Initialize log
        DateFormat dateFormat = new SimpleDateFormat("yyyyMMdd");
        long currentTimeMillis = System.currentTimeMillis();
        String logFileDirPath = con.getFilesDir().getAbsolutePath();
        String logFileName = String.format("app_%s.log", dateFormat.format(new Date(currentTimeMillis)));
        Timber.plant(new LogUtilDebugTree(logFileDirPath, logFileName));
        // Fill the log header
        Timber.i("\n\n*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*\n"
                + "*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*\n"
                + "\n    logging start ... " + df.format(new Date(System.currentTimeMillis()))
                + "\n\n");

        // Delete logs after a certain number of days
        long logDeleteElapsedMillis = currentTimeMillis - LOG_DELETE_ELAPSED_DAYS * (1000L * 60L * 60L * 24L);
        FileFilter fileFilter = new FileFilter() {
            @Override
            public boolean accept(File file) {
                if (!(file.getName().matches("app_\\d{8}.log"))) {
                    return false;
                }
                return file.lastModified() <= logDeleteElapsedMillis;
            }
        };
        for (File file : new File(logFileDirPath).listFiles(fileFilter)) {
            file.delete();
        }

        // Do not sleep while launching the application
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // Initial setting of LEDs
        notificationLedHide(LedTarget.LED4); // Camera
        notificationLedHide(LedTarget.LED5); // Video
        notificationLedHide(LedTarget.LED6); // LIVE
        notificationLedHide(LedTarget.LED8); // Error

        // Start HTTP server
        webUI = new AndroidWebServer(con);
        if (webUI.getIsReady())
            changeReadyLED();

        // Set a callback when a button operation event is acquired.
        setKeyCallback(new KeyCallback() {
            @Override
            public void onKeyLongPress(int keyCode, KeyEvent event) {
                Timber.i("onKeyLongPress");

                if (keyCode == KeyReceiver.KEYCODE_MEDIA_RECORD) {
                    exitProcess();
                }
            }

            /**
             * {@inheritDoc}
             * Responding to events when button is pressed
             */
            @Override
            public void onKeyDown(int keyCode, KeyEvent event) {
                Timber.i("onKeyDown");

                if (keyCode == KeyReceiver.KEYCODE_CAMERA) {
                    webUI.startUpload();
                }
            }

            /**
             * {@inheritDoc}
             *
             * Responding to events when button is released
             */
            @Override
            public void onKeyUp(int keyCode, KeyEvent event) {
                Timber.i("onKeyUp");
            }
        });

    }

    @Override
    protected void onResume() {
        super.onResume();
        // WIFI connection (CL mode)
        notificationWlanCl();

        mChangeLedReceiver = new ChangeLedReceiver(onChangeLedReceiver);
        IntentFilter changeLedFilter = new IntentFilter();
        changeLedFilter.addAction(ChangeLedReceiver.CHANGE_READY_LED);
        changeLedFilter.addAction(ChangeLedReceiver.CHANGE_TRANSFERRING_LED);
        changeLedFilter.addAction(ChangeLedReceiver.CHANGE_STOP_TRANSFERRING_LED);
        changeLedFilter.addAction(ChangeLedReceiver.CHANGE_ERROR_LED);
        registerReceiver(mChangeLedReceiver, changeLedFilter);

        mUploadStatusReceiver = new UploadStatusReceiver(onUploadStatusReceiver);
        IntentFilter uploadStatusFilter = new IntentFilter();
        uploadStatusFilter.addAction(UploadStatusReceiver.UPLOAD_START);
        uploadStatusFilter.addAction(UploadStatusReceiver.UPLOAD_END);
        registerReceiver(mUploadStatusReceiver, uploadStatusFilter);

        mSpecifiedResultReceiver = new SpecifiedResultReceiver(onSpecifiedResultReceiver);
        IntentFilter specifiedResultFilter = new IntentFilter();
        specifiedResultFilter.addAction(SpecifiedResultReceiver.SPECIFED_RESULT);
        registerReceiver(mSpecifiedResultReceiver, specifiedResultFilter);

        mFinishApplicationReceiver = new FinishApplicationReceiver(onFinishApplicationReceiver);
        IntentFilter finishApplicationFilter = new IntentFilter();
        finishApplicationFilter.addAction(FinishApplicationReceiver.FINISH_APPLICATION);
        registerReceiver(mFinishApplicationReceiver, finishApplicationFilter);

        // Intent from other plugins
        Intent intent = getIntent();
        if(intent != null){
            List<String> photoList = intent.getStringArrayListExtra("com.theta360.cloudupload.photoList");
            if (photoList != null && photoList.size() > 0) {
                webUI.uploadSpecifiedPhotoList(photoList);
            }
        }
    }

    @Override
    protected void onStart() {
        super.onStart();

        createShutDownTimer();
        createSettingPolling();
        webUI.createUploadProcess();
    }


    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(mChangeLedReceiver);
        unregisterReceiver(mUploadStatusReceiver);
        unregisterReceiver(mSpecifiedResultReceiver);
        unregisterReceiver(mFinishApplicationReceiver);
    }

    @Override
    protected void onStop() {
        super.onStop();

        if (shutDownTimer != null) {
            shutDownTimer.exit();
        }
        if (settingPolling != null) {
            settingPolling.exit();
        }
        webUI.exitUpload();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        webUI.destroy();
    }

    /**
     * Call the shooting application when this plug-in ends.
     */
    @SuppressLint("WrongConstant")
    private void callRecordingApp() {
        con.sendBroadcastAsUser(new Intent("com.theta360.devicelibrary.receiver.ACTION_BOOT_BASIC"), android.os.Process.myUserHandle());
    }

    /**
     * End processing
     */
    private void exitProcess() {
        Timber.i("Application is terminated.");

        // Launch the shooting application (com.theta 360.receptor).
        callRecordingApp();

        // Finish plug-in
        finishAndRemoveTask();
    }

    private void changeReadyLED() {
        notificationLedHide(LedTarget.LED4);  // Camera
        notificationLedHide(LedTarget.LED5);  // Video
        notificationLedHide(LedTarget.LED8);  // Error
        notificationLedShow(LedTarget.LED6);  // LIVE
    }

    private void changeTransferringLED() {
        notificationLedHide(LedTarget.LED5); // Video
        notificationLedHide(LedTarget.LED8); // Error
        notificationLedBlink(LedTarget.LED4, LedColor.BLUE, 1000);  // Camera
        notificationLedBlink(LedTarget.LED6, LedColor.BLUE, 1000);  // LIVE
    }

    private void changeStopTransferringLED() {
        notificationLedHide(LedTarget.LED6);  // LIVE
    }

    private void changeErrorLED() {
        notificationLedHide(LedTarget.LED4);  // Camera
        notificationLedHide(LedTarget.LED6);  // LIVE
        notificationLedBlink(LedTarget.LED5, LedColor.BLUE, 1000); // Video
        notificationLedBlink(LedTarget.LED8, LedColor.BLUE, 1000); // Error
    }

    /**
     * PPP(Error) sound playback and error LED control
     */
    private void playPPPSoundWithErrorLED() {
        notificationAudioWarning();
        changeErrorLED();
    }

    /**
     * Create an end monitoring timer
     */
    private void createShutDownTimer() {
        shutDownTimer = new ShutDownTimer(new ShutDownTimerCallBack() {
            @Override
            public void callBack() {
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                exitProcess();
            }
        }, noOperationTimeoutMSec);

        try {
            shutDownTimerService = Executors.newSingleThreadExecutor();
            shutDownTimerService.execute(shutDownTimer);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            shutDownTimerService.shutdown();
        }
    }

    /**
     * Create setting monitoring thread
     */
    private void createSettingPolling() {

        // Create setting monitoring class
        settingPolling = new SettingPolling();

        try {
            settingPollingService = Executors.newSingleThreadExecutor();
            settingPollingService.execute(settingPolling);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            settingPollingService.shutdown();
        }
    }


    /**
     * Inner class for monitoring setting status
     */
    private class SettingPolling implements Runnable {

        // Confirmation interval. Unit millisecond
        private static final long CHECK_INTERVAL_MSEC = 1000;
        private Boolean isExit;
        private Boolean isStop;

        public SettingPolling() {
            isExit = false;
            isStop = false;
        }

        /**
         * End thread
         */
        public void exit() {
            isExit = true;
        }


        /**
         * Start monitoring
         */
        public void changeStart() {
            isStop = false;
        }

        /**
         * End monitoring
         */
        public void changeStop() {
            isStop = true;
        }


        @Override
        public void run() {

            Boolean first = true;
            while (!isExit) {
                try {
                    Thread.sleep(CHECK_INTERVAL_MSEC);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                if (isStop) {
                    continue;
                }

                if (webUI.isRequested() || first) {
                    webUI.clearRequested();

                    if (first) {
                        first = false;
                    }

                    synchronized (lock) {
                        // Check setting
                        Theta360SQLiteOpenHelper hlpr = new Theta360SQLiteOpenHelper(con);
                        SQLiteDatabase dbObject = hlpr.getWritableDatabase();
                        Cursor cursor = dbObject.query("theta360_setting", null, null, null, null, null, null, null);

                        try {
                            if (cursor.moveToNext()) {
                                noOperationTimeoutMSec = cursor.getInt(cursor.getColumnIndex("no_operation_timeout_minute")) * 60 * 1000;
                                Timber.d("noOperationTimeoutMSec : " + noOperationTimeoutMSec);

                            } else {
                                // Create new record if DB is empty.
                                ContentValues values = new ContentValues();
                                values.put("no_operation_timeout_minute", AndroidWebServer.TIMEOUT_DEFAULT_MINUTE);
                                values.put("status", "");

                                long num = dbObject.insert("theta360_setting", null, values);
                                if (num != 1) {
                                    throw new SQLiteException("[setting data] initialize database error");
                                }
                            }
                            // Reset auto stop timer
                            if (!shutDownTimer.getIsUploading()) {
                                shutDownTimer.reset(false, noOperationTimeoutMSec);
                            }

                        } catch (Exception e) {
                            e.printStackTrace();
                            throw new SQLiteException("[setting data] Unexpected exception");
                        } finally {
                            cursor.close();
                            dbObject.close();
                        }
                    }
                }
            }
        }
    }

}
