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

/**
 * Upload photo, authentication callback class.
 */
public interface UploadPhotoApiCallback {
    /**
     * Successful processing of code acquisition
     * @param result Result string
     */
    void completedRequestCode(String result);

    /**
     * Failure handling of code acquisition
     * @param result Result string
     */
    void failedRequestCode(String result);

    /**
     * Successful processing of token acquisition
     * @param result Result string
     */
    void completedRequestToken(String result);

    /**
     * Failure handling of token acquisition
     * @param result Result string
     */
    void failedRequestToken(String result);

    /**
     * Successful processing of token reacquisition
     * @param result Result string
     */
    void completedRefreshToken(String result);

    /**
     * Failure handling of token reacquisition
     * @param result Result string
     */
    void failedRefreshToken(String result);

    /**
     * Successful processing of user information acquisition
     * @param result Result string
     */
    void completedRequestUserinfo(String result);

    /**
     * Failure handling of user information acquisition
     * @param result Result string
     */
    void failedRequestUserinfo(String result);

    /**
     * Successful processing of file upload
     * @param result Result string
     */
    void completedUploadFile(String result);

    /**
     * Failure handling of file upload
     * @param result Result string
     */
    void failedUploadFile(String result);
}
