/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2013-2025 Threema GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License, version 3,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.android.vending.licensing;

/**
 * Interface used as part of a {@link Policy} to allow application authors to obfuscate
 * licensing data that will be stored into a SharedPreferences file.
 * <p>
 * Any transformation scheme must be reversable. Implementing classes may optionally implement an
 * integrity check to further prevent modification to preference data. Implementing classes
 * should use device-specific information as a key in the obfuscation algorithm to prevent
 * obfuscated preferences from being shared among devices.
 */
public interface Obfuscator {

    /**
     * Obfuscate a string that is being stored into shared preferences.
     *
     * @param original The data that is to be obfuscated.
     * @param key      The key for the data that is to be obfuscated.
     * @return A transformed version of the original data.
     */
    String obfuscate(String original, String key);

    /**
     * Undo the transformation applied to data by the obfuscate() method.
     *
     * @param obfuscated The data that is to be un-obfuscated.
     * @param key        The key for the data that is to be un-obfuscated.
     * @return The original data transformed by the obfuscate() method.
     * @throws ValidationException Optionally thrown if a data integrity check fails.
     */
    String unobfuscate(String obfuscated, String key) throws ValidationException;
}
