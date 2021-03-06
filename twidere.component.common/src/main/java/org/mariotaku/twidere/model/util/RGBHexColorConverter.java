/*
 *         Twidere - Twitter client for Android
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.mariotaku.twidere.model.util;

import android.graphics.Color;

import com.bluelinelabs.logansquare.typeconverters.StringBasedTypeConverter;

import java.util.Locale;

/**
 * Created by mariotaku on 2017/3/11.
 */

public class RGBHexColorConverter extends StringBasedTypeConverter<Integer> {
    @Override
    public Integer getFromString(final String string) {
        if (string == null) return 0;
        if (string.startsWith("#")) {
            return Color.parseColor(string);
        }
        return Integer.parseInt(string);
    }

    @Override
    public String convertToString(final Integer object) {
        if (object == null) return null;
        return String.format(Locale.US, "#%06X", 0xFFFFFF & object);
    }
}
