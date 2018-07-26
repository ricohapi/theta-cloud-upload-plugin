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
 * Receive broadcasts of start/end uploading
 */
public class UploadStatusReceiver extends BroadcastReceiver {
    public static final String UPLOAD_START = "com.theta360.cloudupload.upload-start";
    public static final String UPLOAD_END = "com.theta360.cloudupload.upload-end";

    private Callback mCallback;

    public UploadStatusReceiver(Callback callback) {
        mCallback = callback;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        final String action = intent.getAction();

        switch (action) {
            case UPLOAD_START:
                mCallback.callStartCallback();
                break;
            case UPLOAD_END:
                mCallback.callEndCallback();
                break;
        }
    }

    public interface Callback {
        void callStartCallback();
        void callEndCallback();
    }
}
