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

/**
 * Uploaded photo information
 */
public class PhotoInformation {

    private String path;
    private String datetime;
    private String userId;

    /**
     * Constructor
     */
    public PhotoInformation() {
        this.path = "";
        this.datetime = "";
        this.userId = "";
    }

    /**
     * Get path
     *
     * @return path
     */
    public String getPath() {
        return this.path;
    }

    /**
     * Set path
     *
     * @param path path
     */
    public void setPath(String path) {
        this.path = path;
    }

    /**
     * Get datetime
     *
     * @return Date time
     */
    public String getDatetime() {
        return this.datetime;
    }

    /**
     * Set datetime
     *
     * @param datetime Date time
     */
    public void setDatetime(String datetime) {
        this.datetime = datetime;
    }

    /**
     * Get userId
     *
     * @return User ID
     */
    public String getUserId() {
        return this.userId;
    }

    /**
     * Set userId
     *
     * @param userId User ID
     */
    public void setUserId(String userId) {
        this.userId = userId;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null || path == null || datetime == null || userId == null) {
            return false;
        }
        PhotoInformation uploadedPhoto = (PhotoInformation)obj;
        return path.equals(uploadedPhoto.getPath())
                && datetime.equals(uploadedPhoto.getDatetime())
                && userId.equals(uploadedPhoto.getUserId());
    }
}
