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

package ch.threema.app.services;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.view.View;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.crypto.CipherInputStream;

import ch.threema.app.cache.ThumbnailCache;
import ch.threema.app.messagereceiver.MessageReceiver;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.base.ThreemaException;
import ch.threema.localcrypto.MasterKeyLockedException;
import ch.threema.storage.models.AbstractMessageModel;
import ch.threema.storage.models.ContactModel;
import ch.threema.storage.models.GroupModel;

public interface FileService {

    /**
     * get the default path for data backup files
     */
    File getBackupPath();

    /**
     * Get the Uri for data backup files
     *
     * @return Uri of data backup path or null if not yet selected by user
     */
    Uri getBackupUri();

    /**
     * get the "default" path for blob downloads
     */
    File getBlobDownloadPath();

    /**
     * get the path of the application
     */
    File getAppDataPath();

    /**
     *
     */
    String getGlobalWallpaperFilePath();

    /**
     * get the file path of wallpapers
     */
    File getWallpaperDirPath();

    /**
     * get the file path of avatar
     */
    File getAvatarDirPath();

    /**
     * get the file path of group avatars
     */
    File getGroupAvatarDirPath();

    /**
     * get the temporary file path
     */
    File getTempPath();

    File getIntTmpPath();

    File getExtTmpPath();

    /**
     * create a temporary file
     *
     * @throws IOException
     */
    File createTempFile(String prefix, String suffix) throws IOException;

    File createTempFile(String prefix, String suffix, boolean isPublic) throws IOException;

    /**
     * cleanup temporary directory
     */
    @WorkerThread
    void cleanTempDirs();

    /**
     *
     */
    String getWallpaperFilePath(MessageReceiver messageReceiver);

    String getWallpaperFilePath(String uniqueIdString);

    /**
     * create a file object for the wallpaper
     *
     * @throws IOException
     */
    File createWallpaperFile(MessageReceiver messageReceiver) throws IOException;

    boolean hasUserDefinedProfilePicture(@NonNull String identity);

    boolean hasContactDefinedProfilePicture(@NonNull String identity);

    /**
     * decrypt a file and save into a new one
     */
    boolean decryptFileToFile(File from, File to);

    /**
     * remove all files (content file, thumbnail) of a message
     */
    boolean removeMessageFiles(AbstractMessageModel messageModel, boolean withThumbnails);

    /**
     * return a decrypted file from a message
     * null if the message or file does not exist
     *
     * @throws Exception
     */
    File getDecryptedMessageFile(AbstractMessageModel messageModel) throws Exception;

    /**
     * return a decrypted file from a message
     * null if the message or file does not exist
     *
     * @throws Exception
     */
    File getDecryptedMessageFile(AbstractMessageModel messageModel, String filename) throws Exception;

    /**
     * return a cipher input stream of a message
     * return null if the file is missing
     *
     * @throws Exception
     */
    CipherInputStream getDecryptedMessageStream(AbstractMessageModel messageModel) throws Exception;

    /**
     * return the cipher input stream of a thumbnail
     * return null if the thumbnail missing
     *
     * @throws Exception
     */
    CipherInputStream getDecryptedMessageThumbnailStream(AbstractMessageModel messageModel) throws Exception;

    /**
     * copy a decrypted message file into "gallery" folder
     * <p>
     * TODO: move to another service
     */
    void copyDecryptedFileIntoGallery(Uri sourceUri, AbstractMessageModel messageModel) throws Exception;

    File getMessageFile(AbstractMessageModel messageModel);

    /**
     * Write a message (modify if needed) and return true on success.
     */
    boolean writeConversationMedia(AbstractMessageModel messageModel, byte[] data) throws Exception;

    /**
     * Write a message (modify if needed) and return true on success.
     */
    boolean writeConversationMedia(AbstractMessageModel messageModel, byte[] data, int pos, int length) throws Exception;

    /**
     * Write a message (modify if needed) and return true on success.
     */
    boolean writeConversationMedia(AbstractMessageModel messageModel, byte[] data, int pos, int length, boolean overwrite) throws Exception;

    /**
     * Save a group avatar (resize if needed) and return true on success. Additionally, this resets
     * the avatar cache for this group.
     */
    boolean writeGroupAvatar(GroupModel groupModel, byte[] photoData) throws IOException, MasterKeyLockedException;

    /**
     * get the group avatar as InputStream
     */
    InputStream getGroupAvatarStream(GroupModel groupModel) throws IOException, MasterKeyLockedException;

    /**
     * get the group avatar if the file exists
     */
    Bitmap getGroupAvatar(GroupModel groupModel) throws IOException, MasterKeyLockedException;

    /**
     * Remove the group avatar. Additionally, this resets the avatar cache for this group.
     */
    void removeGroupAvatar(@NonNull GroupModel groupModel);

    boolean hasGroupAvatarFile(GroupModel groupModel);

    /**
     * Write the contact profile picture set by the user. Additionally, this resets the avatar cache
     * for this contact.
     */
    boolean writeUserDefinedProfilePicture(@NonNull String identity, File file);

    /**
     * Write the contact profile picture set by the user. Additionally, this resets the avatar cache
     * for this contact.
     */
    boolean writeUserDefinedProfilePicture(@NonNull String identity, byte[] avatarFile) throws IOException, MasterKeyLockedException;

    /**
     * Write the contact profile picture received by the contact. Additionally, this resets the
     * avatar cache for this contact.
     */
    boolean writeContactDefinedProfilePicture(@NonNull String identity, byte[] encryptedBlob) throws IOException, MasterKeyLockedException;

    /**
     * Write the contact profile picture from Android's address book. Additionally, this resets the
     * avatar cache for this contact.
     */
    boolean writeAndroidDefinedProfilePicture(@NonNull String identity, byte[] avatarFile) throws Exception;

    /**
     * return the decrypted bitmap of a contact avatar
     * if no file exists, null will be returned
     */
    Bitmap getUserDefinedProfilePicture(@NonNull String identity) throws Exception;

    Bitmap getAndroidDefinedProfilePicture(@NonNull ContactModel contactModel) throws Exception;

    /**
     * Return a input stream of a local saved contact avatar
     */
    InputStream getUserDefinedProfilePictureStream(@NonNull String identity) throws IOException, MasterKeyLockedException;

    /**
     * Return a input stream of a contact photo
     */
    InputStream getContactDefinedProfilePictureStream(@NonNull String identity) throws IOException, MasterKeyLockedException;

    /**
     * return the decrypted bitmap of a contact-provided profile picture
     * returns null if no file exists
     */
    Bitmap getContactDefinedProfilePicture(@NonNull String identity) throws Exception;

    /**
     * Remove the user defined profile picture for the contact with the given identity.
     * Additionally, this resets the avatar cache for this contact.
     *
     * @param identity the identity of the contact
     * @return true if the avatar was deleted, false if the remove failed or no avatar file exists
     */
    boolean removeUserDefinedProfilePicture(@NonNull String identity);

    /**
     * Remove the contact defined profile picture for the contact with the given identity.
     * Additionally, this resets the avatar cache for this contact.
     *
     * @param identity the identity of the contact
     * @return true if avatar was deleted, false if the remove failed or no avatar file exists
     */
    boolean removeContactDefinedProfilePicture(@NonNull String identity);

    /**
     * Remove the profile picture from Android's address book. Additionally, this resets the avatar
     * cache for this contact.
     *
     * @param identity the identity of the contact
     * @return true if the avatar was deleted, false if the remove failed or no avatar file exists
     */
    boolean removeAndroidDefinedProfilePicture(@NonNull String identity);

    /**
     * Remove all avatars in the respective directory. Note that this does *not* reset the avatar
     * caches.
     */
    void removeAllAvatars();

    /**
     * Save the thumbnail bytes to disk using the file name specified in the supplied AbstractMessageModel
     *
     * @param messageModel   Message Model used as the source for the file name
     * @param thumbnailBytes Byte Array of the thumbnail bitmap
     * @throws Exception
     */
    void saveThumbnail(AbstractMessageModel messageModel, byte[] thumbnailBytes) throws Exception;

    /**
     * write a thumbnail to disk
     */
    void writeConversationMediaThumbnail(AbstractMessageModel messageModel, @NonNull byte[] thumbnail) throws Exception;

    /**
     * return whether a thumbnail file exists for the specified message model
     */
    boolean hasMessageThumbnail(AbstractMessageModel messageModel);

    /**
     * return the decrypted thumbnail as bitmap
     */
    @Nullable
    Bitmap getMessageThumbnailBitmap(AbstractMessageModel messageModel, @Nullable ThumbnailCache thumbnailCache) throws Exception;

    /**
     * return the "default" thumbnail
     */
    Bitmap getDefaultMessageThumbnailBitmap(Context context, AbstractMessageModel messageModel, ThumbnailCache thumbnailCache, String mimeType, boolean returnNullIfNotCached, @ColorInt int tintColor);

    /**
     * clear directory
     */
    void clearDirectory(File directory, boolean recursive) throws IOException, ThreemaException;

    /**
     * remove the directory
     */
    boolean remove(File directory, boolean removeWithContent) throws IOException, ThreemaException;

    /**
     * copy the content of a uri into a temporary file
     */
    File copyUriToTempFile(Uri uri, String prefix, String suffix, boolean isPublic);

    /**
     * export the message file to the "share file"
     */
    Uri copyToShareFile(AbstractMessageModel currentModel, File decodedFile);

    Uri getShareFileUri(File destFile, String filename);

    long getInternalStorageUsage();

    long getInternalStorageSize();

    long getInternalStorageFree();

    /**
     * Decrypt messages specified by the 'models' parameter and return a list of URIs of the temporary files
     * When receiving the URIs in OnDecryptedFilesComplete.onComplete, they will have the same order as passed in by `models` list.
     * It is possible, that this URI list contains null entries.
     * Note that you have to ensure that only image, video or file messages are provided
     *
     * @param models                   List of AbstractMessageModels to be decrypted
     * @param onDecryptedFilesComplete Callback
     */
    void loadDecryptedMessageFiles(final List<AbstractMessageModel> models, final OnDecryptedFilesComplete onDecryptedFilesComplete);

    void loadDecryptedMessageFile(final AbstractMessageModel model, final OnDecryptedFileComplete onDecryptedFileCompleted);

    void saveMedia(final AppCompatActivity activity, final View feedbackView, final CopyOnWriteArrayList<AbstractMessageModel> selectedMessages, boolean quiet);

    void saveAppLogo(File logo, @ConfigUtils.AppThemeSetting String theme);

    File getAppLogo(@ConfigUtils.AppThemeSetting String theme);

    @NonNull
    Uri getTempShareFileUri(@NonNull Bitmap bitmap) throws IOException;

    /**
     * Copy the decrypted thumbnail to a temporary file accessible through our FileProvider and return the Uri of the temporary file
     *
     * @param messageModel Message Model used as the source for the thumbnail
     * @param maxSize      Maximum size of the thumbnail in bytes. Set to Integer.MAX_VALUE if no limit
     * @return Uri of the temporary file or null if the thumbnail does not exist, is too large or an error occurred
     */
    @WorkerThread
    @Nullable
    Uri getThumbnailShareFileUri(AbstractMessageModel messageModel, int maxSize);

    interface OnDecryptedFileComplete {
        void complete(File decryptedFile);

        void error(String message);
    }

    interface OnDecryptedFilesComplete {
        void complete(ArrayList<Uri> uriList);

        void error(String message);
    }
}
