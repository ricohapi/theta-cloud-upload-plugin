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
 * Define the error.
 */
public enum ErrorType {
    SUCCESS(0, "Successful completion", "SUCCESS"),
    NOT_SETTINGS(1, "Setting is not completed", "NOT_SETTINGS"),
    BAD_SETTINGS(2, "Invalid setting content", "BAD_SETTINGS"),
    TIMEOUT(3, "Timed out", "TIMEOUT");

    private int code;
    private String message;
    private String type;

    ErrorType(int code, String message, String type) {
        this.code = code;
        this.message = message;
        this.type = type;
    }

    public int getCode() {
        return this.code;
    }

    public String getMessage() {
        return this.message;
    }

    public String getType() {
        return this.type;
    }

    public static ErrorType getType(String type) {
        try {
            return ErrorType.valueOf(type);
        } catch (IllegalArgumentException | NullPointerException e) {
            return ErrorType.SUCCESS;
        }
    }
}