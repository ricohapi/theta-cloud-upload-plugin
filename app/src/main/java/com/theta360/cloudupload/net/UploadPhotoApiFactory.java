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

/**
 * Upload photos, authentication API object generation class
 */
public class UploadPhotoApiFactory {

    public static final String GOOGLE_PHOTO = "google_photo";

    public static UploadPhotoApi createUploadPhotoApi(Context context, String type) {
        switch (type) {
            case GOOGLE_PHOTO:
                return new GoogleDataApi(context);
            default:
                return null;
        }
    }
}
