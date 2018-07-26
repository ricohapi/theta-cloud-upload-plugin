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

package com.theta360.cloudupload.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.support.annotation.NonNull;

/**
 * Receive broadcast of LED change
 */
public class ChangeLedReceiver extends BroadcastReceiver {
    public static final String CHANGE_READY_LED = "com.theta360.cloudupload.change-ready-led";
    public static final String CHANGE_TRANSFERRING_LED = "com.theta360.cloudupload.change-transferring-led";
    public static final String CHANGE_STOP_TRANSFERRING_LED = "com.theta360.cloudupload.change-stop-transferring-led";
    public static final String CHANGE_ERROR_LED = "com.theta360.cloudupload.change-error-led";

    private Callback mCallback;

    public ChangeLedReceiver(Callback callback) {
        mCallback = callback;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        final String action = intent.getAction();

        switch (action) {
            case CHANGE_READY_LED:
                mCallback.callReadyCallback();
                break;
            case CHANGE_TRANSFERRING_LED:
                mCallback.callTransferringCallback();
                break;
            case CHANGE_STOP_TRANSFERRING_LED:
                mCallback.callStopTransferringCallback();
                break;
            case CHANGE_ERROR_LED:
                mCallback.callErrorCallback();
                break;
        }
    }

    public interface Callback {
        void callReadyCallback();
        void callTransferringCallback();
        void callStopTransferringCallback();
        void callErrorCallback();
    }
}
