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
/*
 * This file is auto-generated.  DO NOT MODIFY.
 * Original file: aidl/ILicensingService.aidl
 */
package com.google.android.vending.licensing;

import java.lang.String;

import android.os.RemoteException;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Binder;
import android.os.Parcel;

public interface ILicensingService extends android.os.IInterface {
    /**
     * Local-side IPC implementation contentViewStub class.
     */
    public static abstract class Stub extends android.os.Binder implements com.google.android.vending.licensing.ILicensingService {
        private static final java.lang.String DESCRIPTOR = "com.android.vending.licensing.ILicensingService";

        /**
         * Construct the contentViewStub at attach it to the interface.
         */
        public Stub() {
            this.attachInterface(this, DESCRIPTOR);
        }

        /**
         * Cast an IBinder object into an ILicensingService interface,
         * generating a proxy if needed.
         */
        public static com.google.android.vending.licensing.ILicensingService asInterface(android.os.IBinder obj) {
            if ((obj == null)) {
                return null;
            }
            android.os.IInterface iin = obj.queryLocalInterface(DESCRIPTOR);
            if (((iin != null) && (iin instanceof com.google.android.vending.licensing.ILicensingService))) {
                return ((com.google.android.vending.licensing.ILicensingService) iin);
            }
            return new com.google.android.vending.licensing.ILicensingService.Stub.Proxy(obj);
        }

        public android.os.IBinder asBinder() {
            return this;
        }

        public boolean onTransact(int code, android.os.Parcel data, android.os.Parcel reply, int flags) throws android.os.RemoteException {
            switch (code) {
                case INTERFACE_TRANSACTION: {
                    reply.writeString(DESCRIPTOR);
                    return true;
                }
                case TRANSACTION_checkLicense: {
                    data.enforceInterface(DESCRIPTOR);
                    long _arg0;
                    _arg0 = data.readLong();
                    java.lang.String _arg1;
                    _arg1 = data.readString();
                    com.google.android.vending.licensing.ILicenseResultListener _arg2;
                    _arg2 = com.google.android.vending.licensing.ILicenseResultListener.Stub.asInterface(data.readStrongBinder());
                    this.checkLicense(_arg0, _arg1, _arg2);
                    return true;
                }
            }
            return super.onTransact(code, data, reply, flags);
        }

        private static class Proxy implements com.google.android.vending.licensing.ILicensingService {
            private android.os.IBinder mRemote;

            Proxy(android.os.IBinder remote) {
                mRemote = remote;
            }

            public android.os.IBinder asBinder() {
                return mRemote;
            }

            public java.lang.String getInterfaceDescriptor() {
                return DESCRIPTOR;
            }

            public void checkLicense(long nonce, java.lang.String packageName, com.google.android.vending.licensing.ILicenseResultListener listener) throws android.os.RemoteException {
                android.os.Parcel _data = android.os.Parcel.obtain();
                try {
                    _data.writeInterfaceToken(DESCRIPTOR);
                    _data.writeLong(nonce);
                    _data.writeString(packageName);
                    _data.writeStrongBinder((((listener != null)) ? (listener.asBinder()) : (null)));
                    mRemote.transact(Stub.TRANSACTION_checkLicense, _data, null, IBinder.FLAG_ONEWAY);
                } finally {
                    _data.recycle();
                }
            }
        }

        static final int TRANSACTION_checkLicense = (IBinder.FIRST_CALL_TRANSACTION + 0);
    }

    public void checkLicense(long nonce, java.lang.String packageName, com.google.android.vending.licensing.ILicenseResultListener listener) throws android.os.RemoteException;
}
