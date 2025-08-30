package com.capacitor.media.rsd;

import android.Manifest;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Process;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.util.Base64;
import android.util.Log;
import android.webkit.MimeTypeMap;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import java.io.ByteArrayOutputStream;
import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Logger;
import com.getcapacitor.PermissionState;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.InputStream;
import java.nio.channels.FileChannel;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

@CapacitorPlugin(
    name = "Media",
    permissions = {
        @Permission(
            strings = { Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE },
            alias = "publicStorage"
        ),
        @Permission(
            strings = { Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO },
            alias = "publicStorage13Plus"
        )
    }
)
public class MediaPlugin extends Plugin {

    private static final String PERMISSION_DENIED_ERROR = "Unable to access media, user denied permission request";

    private static final int API_LEVEL_29 = 29;
    private static final int API_LEVEL_33 = 33;

    public static final String EC_ACCESS_DENIED = "accessDenied";
    public static final String EC_ARG_ERROR = "argumentError";
    public static final String EC_DOWNLOAD_ERROR = "downloadError";
    public static final String EC_FS_ERROR = "filesystemError";

    // @todo
    @PluginMethod
    public void getMedias(PluginCall call) {
        call.unimplemented();
    }

    @PluginMethod
    public void getMediaByIdentifier(PluginCall call) {
        call.unimplemented("No need to do this on Android -- the identifier is the file path.");
    }

    @PluginMethod
    public void getAlbums(PluginCall call) {
        Log.d("DEBUG LOG", "GET ALBUMS");
        if (isStoragePermissionGranted()) {
            Log.d("DEBUG LOG", "HAS PERMISSION");
            _getAlbums(call);
        } else {
            Log.d("DEBUG LOG", "NOT ALLOWED");
            this.bridge.saveCall(call);
            requestAllPermissions(call, "permissionCallback");
        }
    }

    @PluginMethod
    public void savePhoto(PluginCall call) {
        Log.d("DEBUG LOG", "SAVE PHOTO TO ALBUM");
        if (isStoragePermissionGranted()) {
            Log.d("DEBUG LOG", "HAS PERMISSION");
            _saveMedia(call);
        } else {
            Log.d("DEBUG LOG", "NOT ALLOWED");
            this.bridge.saveCall(call);
            requestAllPermissions(call, "permissionCallback");
            Log.d("DEBUG LOG", "___SAVE PHOTO TO ALBUM AFTER PERMISSION REQUEST");
        }
    }

    @PluginMethod
    public void saveVideo(PluginCall call) {
        Log.d("DEBUG LOG", "SAVE VIDEO TO ALBUM");
        if (isStoragePermissionGranted()) {
            Log.d("DEBUG LOG", "HAS PERMISSION");
            _saveMedia(call);
        } else {
            Log.d("DEBUG LOG", "NOT ALLOWED");
            this.bridge.saveCall(call);
            requestAllPermissions(call, "permissionCallback");
        }
    }

    @PluginMethod
    public void createAlbum(PluginCall call) {
        Log.d("DEBUG LOG", "CREATE ALBUM");
        if (isStoragePermissionGranted()) {
            Log.d("DEBUG LOG", "HAS PERMISSION");
            _createAlbum(call);
        } else {
            Log.d("DEBUG LOG", "NOT ALLOWED");
            this.bridge.saveCall(call);
            requestAllPermissions(call, "permissionCallback");
        }
    }

    @PluginMethod
    public void checkPermissions(PluginCall call) {
        JSObject response = new JSObject();
        boolean granted = isStoragePermissionGranted();
        response.put("granted", granted);
        response.put("status", granted ? "granted" : "denied");
        call.resolve(response);
    }

    @PluginMethod
    public void requestPermissions(PluginCall call) {
        if (isStoragePermissionGranted()) {
            JSObject response = new JSObject();
            response.put("granted", true);
            response.put("status", "granted");
            call.resolve(response);
        } else {
            this.bridge.saveCall(call);
            requestAllPermissions(call, "requestPermissionsCallback");
        }
    }

    @PluginMethod
    public void getPhotos(PluginCall call) {
        if (isStoragePermissionGranted()) {
            _getPhotos(call);
        } else {
            this.bridge.saveCall(call);
            requestAllPermissions(call, "permissionCallback");
        }
    }

    @PluginMethod
    public void getAllPhotos(PluginCall call) {
        if (isStoragePermissionGranted()) {
            _getAllPhotos(call);
        } else {
            this.bridge.saveCall(call);
            requestAllPermissions(call, "permissionCallback");
        }
    }

    @PluginMethod
    public void getPhotoInfo(PluginCall call) {
        if (isStoragePermissionGranted()) {
            _getPhotoInfo(call);
        } else {
            this.bridge.saveCall(call);
            requestAllPermissions(call, "permissionCallback");
        }
    }

    @PluginMethod
    public void getFile(PluginCall call) {
        if (!isStoragePermissionGranted()) {
            requestPermissions(call);
            return;
        }

        String contentUriString = call.getString("contentUri");
        if (contentUriString == null || contentUriString.isEmpty()) {
            call.reject("contentUri parameter is required", EC_ARG_ERROR);
            return;
        }

        // Get optional compression parameters
        int quality = call.getInt("quality", 75);
        int maxWidth = call.getInt("maxWidth", 1024);
        int maxHeight = call.getInt("maxHeight", 1024);

        try {
            Uri contentUri = Uri.parse(contentUriString);
            _getFile(call, contentUri, quality, maxWidth, maxHeight);
        } catch (Exception e) {
            call.reject("Invalid contentUri format", EC_ARG_ERROR);
        }
    }

    @PluginMethod
    public void exitApp(PluginCall call) {
        try {
            // 结束当前Activity
            getActivity().finishAffinity();
            // 强制退出应用进程
            Process.killProcess(Process.myPid());
            // 正常退出系统
            System.exit(0);
            call.resolve();
        } catch (Exception e) {
            call.reject("Failed to exit app: " + e.getMessage());
        }
    }

    @PermissionCallback
    private void requestPermissionsCallback(PluginCall call) {
        JSObject response = new JSObject();
        boolean granted = isStoragePermissionGranted();
        response.put("granted", granted);
        response.put("status", granted ? "granted" : "denied");
        call.resolve(response);
    }

    @PermissionCallback
    private void permissionCallback(PluginCall call) {
        if (!isStoragePermissionGranted()) {
            Logger.debug(getLogTag(), "User denied storage permission");
            call.reject(PERMISSION_DENIED_ERROR, EC_ACCESS_DENIED);
            return;
        }

        switch (call.getMethodName()) {
            case "getMedias":
                call.unimplemented();
                break;
            case "getAlbums":
                _getAlbums(call);
                break;
            case "savePhoto":
            case "saveVideo":
                _saveMedia(call);
                break;
            case "createAlbum":
                _createAlbum(call);
                break;
            case "getPhotos":
                _getPhotos(call);
                break;
            case "getAllPhotos":
                _getAllPhotos(call);
                break;
            case "getPhotoInfo":
                _getPhotoInfo(call);
                break;
        }
    }

    private boolean isGalleryMode() {
        return true;
    }

    private boolean isStoragePermissionGranted() {
        // If not in gallery mode, no permissions are required
        if (!isGalleryMode()) return true;

        String permissionSet = "publicStorage";
        if (Build.VERSION.SDK_INT >= API_LEVEL_33) {
            permissionSet = "publicStorage13Plus";
        }

        return getPermissionState(permissionSet) == PermissionState.GRANTED;
    }

    private void _getAlbums(PluginCall call) {
        Log.d("DEBUG LOG", "___GET ALBUMS");

        JSObject response = new JSObject();
        JSArray albums = new JSArray();
        Set<String> bucketIds = new HashSet<String>();
        Set<String> identifiers = new HashSet<String>();

        String[] projection = new String[] {
            MediaStore.MediaColumns.BUCKET_DISPLAY_NAME,
            MediaStore.MediaColumns.BUCKET_ID,
            MediaStore.MediaColumns.DATA
        };

        Uri imageUri = isGalleryMode() ?
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI :
                MediaStore.Images.Media.INTERNAL_CONTENT_URI;

        Uri videoUri = isGalleryMode() ?
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI :
                MediaStore.Video.Media.INTERNAL_CONTENT_URI;

        Cursor[] curs = {
            getActivity().getContentResolver().query(imageUri, projection, null, null, null),
            getActivity().getContentResolver().query(videoUri, projection, null, null, null)
        };

        for (Cursor cur : curs) {
            while (cur.moveToNext()) {
                String albumName = cur.getString((cur.getColumnIndex(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)));
                String bucketId = cur.getString((cur.getColumnIndex(MediaStore.MediaColumns.BUCKET_ID)));

                if (!bucketIds.contains(bucketId)) {
                    String path = cur.getString((cur.getColumnIndex(MediaStore.MediaColumns.DATA)));
                    File fileForPath = new File(path);
                    JSObject album = new JSObject();

                    album.put("name", albumName);
                    album.put("identifier", fileForPath.getParent());
                    albums.put(album);

                    bucketIds.add(bucketId);
                    identifiers.add(fileForPath.getParent());
                }
            }

            cur.close();
        }

        File albumPath = new File(_getAlbumsPath());
        for (File sub : albumPath.listFiles()) {
            if (sub.isDirectory() && !identifiers.contains(sub.getAbsolutePath())) {
                JSObject album = new JSObject();

                album.put("name", sub.getName());
                album.put("identifier", sub.getAbsolutePath());
                identifiers.add(sub.getAbsolutePath());
                albums.put(album);
            }
        }

        response.put("albums", albums);
        Log.d("DEBUG LOG", String.valueOf(response));
        Log.d("DEBUG LOG", "___GET ALBUMS FINISHED");

        call.resolve(response);
    }

    @PluginMethod
    public void getAlbumsPath(PluginCall call) {
        JSObject data = new JSObject();
        data.put("path", _getAlbumsPath());
        call.resolve(data);
    }

    private String _getAlbumsPath() {
        return getContext().getExternalMediaDirs()[0].getAbsolutePath();
    }

    private void _saveMedia(PluginCall call) {
        Log.d("DEBUG LOG", "___SAVE MEDIA TO ALBUM");
        String inputPath = call.getString("path");
        if (inputPath == null) {
            call.reject("Input file path is required", EC_ARG_ERROR);
            return;
        }

        File inputFile;

        if (inputPath.startsWith("data:")) {
            try {
                String base64EncodedString = inputPath.substring(inputPath.indexOf(",") + 1);
                byte[] decodedBytes = Base64.decode(base64EncodedString, Base64.DEFAULT);
                String mime = inputPath.split(";", 2)[0].split(":")[1];
                String extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime);
                if (extension == null || extension.isEmpty()) {
                    call.reject("Cannot identify media type to save image.", EC_ARG_ERROR);
                    return;
                }

                try {
                    inputFile = File.createTempFile("tmp", "." + extension, getContext().getCacheDir());
                    OutputStream os = new FileOutputStream(inputFile);
                    os.write(decodedBytes);
                    os.close();
                } catch (IOException e) {
                    call.reject("Temporary file creation from data URL failed", EC_FS_ERROR);
                    return;
                }
            } catch (Exception e) {
                call.reject("Data URL parsing failed.", EC_ARG_ERROR);
                return;
            }
        } else if (inputPath.startsWith("http://") || inputPath.startsWith("https://")) {
            OkHttpClient client = new OkHttpClient();
            Request okrequest = new Request.Builder().url(inputPath).build();
            try {
                // Download image
                Response response = client.newCall(okrequest).execute();
                if (!response.isSuccessful() || response.body() == null) {
                    throw new IOException();
                }

                // Get file extension from URL
                String extension = MimeTypeMap.getFileExtensionFromUrl(inputPath);
                // If it doesn't have it there,
                // attempt to pull extension from MIME type
                if (extension.isEmpty()) {
                    ResponseBody body = response.body();
                    if (body == null) {
                        call.reject("Download failed", EC_DOWNLOAD_ERROR);
                        return;
                    }

                    MediaType mt = body.contentType();
                    if (mt == null) {
                        call.reject("Cannot identify media type to save image.", EC_ARG_ERROR);
                        return;
                    }

                    String mime = mt.type() + "/" + mt.subtype();
                    extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime);
                }

                // Still no extension? reject
                if (extension == null || extension.isEmpty()) {
                    call.reject("Cannot identify media type to save image.", EC_ARG_ERROR);
                    return;
                }

                // Save to temp file
                try {
                    inputFile = File.createTempFile("tmp", "." + extension, getContext().getCacheDir());

                    try (InputStream inputStream = response.body().byteStream();
                         OutputStream os = new FileOutputStream(inputFile)) {

                        byte[] buffer = new byte[8192];
                        int bytesRead;
                        while ((bytesRead = inputStream.read(buffer)) != -1) {
                            os.write(buffer, 0, bytesRead);
                        }
                    }
                } catch (IOException e) {
                    call.reject("Saving download to device failed.", EC_FS_ERROR);
                    return;
                }
            } catch (IOException e) {
                call.reject("Download failed", EC_DOWNLOAD_ERROR);
                return;
            }
        } else {
            Uri inputUri = Uri.parse(inputPath);
            inputFile = new File(inputUri.getPath());
        }

        String album = call.getString("albumIdentifier");
        File albumDir = null;
        Log.d("SDK BUILD VERSION", String.valueOf(Build.VERSION.SDK_INT));

        if (album != null) {
            albumDir = new File(album);
        } else {
            call.reject("Album identifier required", EC_ARG_ERROR);
            return;
        }

        if (!albumDir.exists() || !albumDir.isDirectory()) {
            call.reject("Album identifier does not exist, use getAlbums() to get", EC_ARG_ERROR);
            return;
        }

        Log.d("ENV LOG - ALBUM DIR", String.valueOf(albumDir));

        try {
            // generate image file name using current date and time
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmssSSS").format(new Date());
            String fileName = call.getString("fileName", "IMG_" + timeStamp);
            File expFile = copyFile(inputFile, albumDir, fileName);
            scanPhoto(expFile);

            JSObject result = new JSObject();
            result.put("filePath", expFile.toString());
            call.resolve(result);
        } catch (RuntimeException e) {
            call.reject("Error occurred: " + e, EC_ARG_ERROR);
            return;
        }
    }

    private void _createAlbum(PluginCall call) {
        Log.d("DEBUG LOG", "___CREATE ALBUM");
        String folderName = call.getString("name");

        if (folderName == null) {
            call.reject("Album name must be given!", EC_ARG_ERROR);
            return;
        }

        File f = new File(_getAlbumsPath(), folderName);

        if (!f.exists()) {
            if (!f.mkdir()) {
                Log.d("DEBUG LOG", "___ERROR ALBUM");
                call.reject("Cant create album", EC_FS_ERROR);
            } else {
                Log.d("DEBUG LOG", "___SUCCESS ALBUM CREATED");
                call.resolve();
            }
        } else {
            Log.d("DEBUG LOG", "___ERROR ALBUM ALREADY EXISTS");
            call.reject("Album already exists", EC_FS_ERROR);
        }
    }

    private File copyFile(File inputFile, File albumDir, String fileName) {
        // if destination folder does not exist, create it
        if (!albumDir.exists()) {
            if (!albumDir.mkdir()) {
                throw new RuntimeException("Destination folder does not exist and cannot be created.");
            }
        }

        String absolutePath = inputFile.getAbsolutePath();
        String extension = absolutePath.substring(absolutePath.lastIndexOf("."));

        File newFile = new File(albumDir, fileName + extension);

        // Read and write image files
        FileChannel inChannel = null;
        FileChannel outChannel = null;

        try {
            inChannel = new FileInputStream(inputFile).getChannel();
        } catch (FileNotFoundException e) {
            throw new RuntimeException("Source file not found: " + inputFile + ", error: " + e.getMessage());
        }
        try {
            outChannel = new FileOutputStream(newFile).getChannel();
        } catch (FileNotFoundException e) {
            throw new RuntimeException("Copy file not found: " + newFile + ", error: " + e.getMessage());
        }

        try {
            inChannel.transferTo(0, inChannel.size(), outChannel);
        } catch (IOException e) {
            throw new RuntimeException("Error transfering file, error: " + e.getMessage());
        } finally {
            if (inChannel != null) {
                try {
                    inChannel.close();
                } catch (IOException e) {
                    Log.d("SaveImage", "Error closing input file channel: " + e.getMessage());
                    // does not harm, do nothing
                }
            }
            if (outChannel != null) {
                try {
                    outChannel.close();
                } catch (IOException e) {
                    Log.d("SaveImage", "Error closing output file channel: " + e.getMessage());
                    // does not harm, do nothing
                }
            }
        }

        return newFile;
    }

    private void scanPhoto(File imageFile) {
        Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
        Uri contentUri = Uri.fromFile(imageFile);
        mediaScanIntent.setData(contentUri);
        bridge.getActivity().sendBroadcast(mediaScanIntent);
    }

    private void _getPhotos(PluginCall call) {
        String albumId = call.getString("albumId");
        JSObject response = new JSObject();
        JSArray photos = new JSArray();

        String[] projection = new String[] {
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATA,
            MediaStore.Images.Media.DATE_TAKEN,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT,
            MediaStore.Images.Media.BUCKET_ID,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME
        };

        String selection = null;
        String[] selectionArgs = null;

        if (albumId != null && !albumId.isEmpty()) {
            // Get photos from specific album by checking if the file path starts with albumId
            selection = MediaStore.Images.Media.DATA + " LIKE ?";
            selectionArgs = new String[]{albumId + "%"};
        }

        Uri imageUri = isGalleryMode() ?
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI :
                MediaStore.Images.Media.INTERNAL_CONTENT_URI;

        Cursor cursor = getActivity().getContentResolver().query(
            imageUri, projection, selection, selectionArgs, 
            MediaStore.Images.Media.DATE_TAKEN + " DESC"
        );

        if (cursor != null) {
            while (cursor.moveToNext()) {
                JSObject photo = createPhotoObject(cursor);
                photos.put(photo);
            }
            cursor.close();
        }

        response.put("photos", photos);
        call.resolve(response);
    }

    private void _getAllPhotos(PluginCall call) {
        JSObject response = new JSObject();
        JSArray photos = new JSArray();

        String[] projection = new String[] {
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATA,
            MediaStore.Images.Media.DATE_TAKEN,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT,
            MediaStore.Images.Media.BUCKET_ID,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME
        };

        Uri imageUri = isGalleryMode() ?
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI :
                MediaStore.Images.Media.INTERNAL_CONTENT_URI;

        Cursor cursor = getActivity().getContentResolver().query(
            imageUri, projection, null, null, 
            MediaStore.Images.Media.DATE_TAKEN + " DESC"
        );

        if (cursor != null) {
            while (cursor.moveToNext()) {
                JSObject photo = createPhotoObject(cursor);
                photos.put(photo);
            }
            cursor.close();
        }

        response.put("photos", photos);
        call.resolve(response);
    }

    private void _getPhotoInfo(PluginCall call) {
        String path = call.getString("path");
        if (path == null || path.isEmpty()) {
            call.reject("Path is required", EC_ARG_ERROR);
            return;
        }

        String[] projection = new String[] {
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATA,
            MediaStore.Images.Media.DATE_TAKEN,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT,
            MediaStore.Images.Media.BUCKET_ID,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME
        };

        String selection = MediaStore.Images.Media.DATA + " = ?";
        String[] selectionArgs = new String[]{path};

        Uri imageUri = isGalleryMode() ?
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI :
                MediaStore.Images.Media.INTERNAL_CONTENT_URI;

        Cursor cursor = getActivity().getContentResolver().query(
            imageUri, projection, selection, selectionArgs, null
        );

        if (cursor != null && cursor.moveToFirst()) {
            JSObject photoInfo = createPhotoObject(cursor);
            cursor.close();
            call.resolve(photoInfo);
        } else {
            if (cursor != null) {
                cursor.close();
            }
            call.reject("Photo not found", EC_ARG_ERROR);
        }
    }

    private JSObject createPhotoObject(Cursor cursor) {
        JSObject photo = new JSObject();
        
        String id = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID));
        String name = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME));
        String path = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA));
        long dateTaken = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN));
        long size = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE));
        int width = cursor.getInt(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH));
        int height = cursor.getInt(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT));
        String bucketId = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_ID));
        String albumName = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME));

        // Create content URI
        Uri contentUri = Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id);
        
        // Get album path from file path
        File file = new File(path);
        String albumId = file.getParent();

        photo.put("id", id);
        photo.put("name", name != null ? name : "");
        photo.put("path", path != null ? path : "");
        photo.put("contentUri", contentUri.toString());
        photo.put("dateTaken", dateTaken);
        photo.put("size", size);
        photo.put("width", width);
        photo.put("height", height);
        photo.put("albumId", albumId != null ? albumId : "");
        photo.put("albumName", albumName != null ? albumName : "");

        return photo;
    }

    private void _getFile(PluginCall call, Uri contentUri, int quality, int maxWidth, int maxHeight) {
        try {
            Context context = getContext();
            InputStream inputStream = context.getContentResolver().openInputStream(contentUri);
            
            if (inputStream == null) {
                call.reject("Unable to open file from contentUri", EC_FS_ERROR);
                return;
            }

            // Get file info from content resolver
            String displayName = null;
            String originalMimeType = null;
            long originalSize = 0;

            Cursor cursor = context.getContentResolver().query(
                contentUri,
                new String[]{
                    MediaStore.MediaColumns.DISPLAY_NAME,
                    MediaStore.MediaColumns.MIME_TYPE,
                    MediaStore.MediaColumns.SIZE
                },
                null,
                null,
                null
            );

            if (cursor != null && cursor.moveToFirst()) {
                int nameIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME);
                int typeIndex = cursor.getColumnIndex(MediaStore.MediaColumns.MIME_TYPE);
                int sizeIndex = cursor.getColumnIndex(MediaStore.MediaColumns.SIZE);

                if (nameIndex != -1) {
                    displayName = cursor.getString(nameIndex);
                }
                if (typeIndex != -1) {
                    originalMimeType = cursor.getString(typeIndex);
                }
                if (sizeIndex != -1) {
                    originalSize = cursor.getLong(sizeIndex);
                }
                cursor.close();
            }

            // If we couldn't get mime type from cursor, try to get it from content resolver
            if (originalMimeType == null) {
                originalMimeType = context.getContentResolver().getType(contentUri);
            }

            // Check if it's an image file
            boolean isImage = originalMimeType != null && originalMimeType.startsWith("image/");
            
            if (isImage) {
                // Process as image with compression and WebP conversion
                Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
                inputStream.close();
                
                if (bitmap == null) {
                    call.reject("Unable to decode image", EC_FS_ERROR);
                    return;
                }

                // Apply rotation based on EXIF data
                bitmap = rotateImageIfRequired(context, contentUri, bitmap);
                
                // Resize image if needed
                bitmap = resizeImage(bitmap, maxWidth, maxHeight);
                
                // Convert to WebP format
                ByteArrayOutputStream webpOutputStream = new ByteArrayOutputStream();
                bitmap.compress(Bitmap.CompressFormat.WEBP, quality, webpOutputStream);
                byte[] webpBytes = webpOutputStream.toByteArray();
                
                // Clean up bitmap
                bitmap.recycle();
                
                String base64Data = Base64.encodeToString(webpBytes, Base64.NO_WRAP);
                
                // Update file name to reflect WebP format
                String webpFileName = displayName;
                if (webpFileName != null && webpFileName.contains(".")) {
                    webpFileName = webpFileName.substring(0, webpFileName.lastIndexOf(".")) + ".webp";
                } else if (webpFileName != null) {
                    webpFileName = webpFileName + ".webp";
                }
                
                // Create response object
                JSObject ret = new JSObject();
                ret.put("base64Data", base64Data);
                ret.put("name", webpFileName != null ? webpFileName : "");
                ret.put("type", "image/webp");
                ret.put("size", webpBytes.length);
                
                call.resolve(ret);
                
            } else {
                // Process as regular file (non-image)
                byte[] buffer = new byte[8192];
                ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                int bytesRead;
                
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    byteArrayOutputStream.write(buffer, 0, bytesRead);
                }
                
                inputStream.close();
                byte[] fileBytes = byteArrayOutputStream.toByteArray();
                String base64Data = Base64.encodeToString(fileBytes, Base64.NO_WRAP);

                // Create response object
                JSObject ret = new JSObject();
                ret.put("base64Data", base64Data);
                ret.put("name", displayName != null ? displayName : "");
                ret.put("type", originalMimeType != null ? originalMimeType : "");
                ret.put("size", fileBytes.length);

                call.resolve(ret);
            }

        } catch (FileNotFoundException e) {
            call.reject("File not found: " + e.getMessage(), EC_FS_ERROR);
        } catch (IOException e) {
            call.reject("Error reading file: " + e.getMessage(), EC_FS_ERROR);
        } catch (Exception e) {
            call.reject("Unexpected error: " + e.getMessage(), EC_FS_ERROR);
        }
    }

    private Bitmap rotateImageIfRequired(Context context, Uri imageUri, Bitmap bitmap) {
        try {
            InputStream input = context.getContentResolver().openInputStream(imageUri);
            if (input == null) return bitmap;
            
            ExifInterface exif = new ExifInterface(input);
            int orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            input.close();
            
            switch (orientation) {
                case ExifInterface.ORIENTATION_ROTATE_90:
                    return rotateImage(bitmap, 90);
                case ExifInterface.ORIENTATION_ROTATE_180:
                    return rotateImage(bitmap, 180);
                case ExifInterface.ORIENTATION_ROTATE_270:
                    return rotateImage(bitmap, 270);
                default:
                    return bitmap;
            }
        } catch (IOException e) {
            return bitmap;
        }
    }

    private Bitmap rotateImage(Bitmap bitmap, float degrees) {
        Matrix matrix = new Matrix();
        matrix.postRotate(degrees);
        Bitmap rotatedBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
        if (rotatedBitmap != bitmap) {
            bitmap.recycle();
        }
        return rotatedBitmap;
    }

    private Bitmap resizeImage(Bitmap bitmap, int maxWidth, int maxHeight) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        
        // Calculate scaling factor
        float scaleWidth = (float) maxWidth / width;
        float scaleHeight = (float) maxHeight / height;
        float scale = Math.min(scaleWidth, scaleHeight);
        
        // If no scaling needed, return original bitmap
        if (scale >= 1.0f) {
            return bitmap;
        }
        
        // Calculate new dimensions
        int newWidth = Math.round(width * scale);
        int newHeight = Math.round(height * scale);
        
        // Create scaled bitmap
        Bitmap scaledBitmap = Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true);
        
        // Recycle original bitmap if it's different from the scaled one
        if (scaledBitmap != bitmap) {
            bitmap.recycle();
        }
        
        return scaledBitmap;
    }
}
