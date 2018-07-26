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

package com.theta360.cloudupload.settingdata;

import com.theta360.cloudupload.httpserver.AndroidWebServer;

/**
 * Define the data type of the setting that the user decides.
 *
 * If the bit rate is auto, AUTO_BITRATE will be entered.
 */
public class SettingData {

    private int noOperationTimeoutMinute;  // No operation timeout seconds
    private String status;  // Status
    private boolean isUploadMovie;  // Flag on whether to upload video

    /**
     * Constructor
     */
    public SettingData() {
        this.noOperationTimeoutMinute = AndroidWebServer.TIMEOUT_DEFAULT_MINUTE;
        this.status = "";
        this.isUploadMovie = false;
    }

    /**
     * Get no operation timeout seconds
     *
     * @return No operation timeout seconds
     */
    public int getNoOperationTimeoutMinute() {
        return this.noOperationTimeoutMinute;
    }

    /**
     * Set no operation timeout seconds
     *
     * @param noOperationTimeoutMinute No operation timeout seconds
     */
    public void setNoOperationTimeoutMinute(int noOperationTimeoutMinute) {
        this.noOperationTimeoutMinute = noOperationTimeoutMinute;
    }

    /**
     * Get status
     *
     * @return status
     */
    public String getStatus() {
        return this.status;
    }

    /**
     * Set status
     *
     * @param status status
     */
    public void setStatus(String status) {
        if (status != null)
            this.status = status;
    }

    /**
     * Get the flag on whether to upload video
     *
     * @return flag
     */
    public boolean getIsUploadMovie() {
        return this.isUploadMovie;
    }

    /**
     * Set the flag on whether to upload video
     *
     * @param isUploadMovie flag
     */
    public void setIsUploadMovie(int isUploadMovie) {
        this.isUploadMovie = isUploadMovie == 1;
    }
}
