/*
 *
 *  Copyright (c) 2013 Cangol
 *   <p/>
 *   Licensed under the Apache License, Version 2.0 (the "License")
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *  <p/>
 *  http://www.apache.org/licenses/LICENSE-2.0
 *  <p/>
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package mobi.cangol.mobile.service.download;

import java.io.File;
import java.util.Random;

import mobi.cangol.mobile.service.download.Download;
import mobi.cangol.mobile.utils.AppUtils;
import mobi.cangol.mobile.utils.FileUtils;
import mobi.cangol.mobile.utils.TimeUtils;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.support.v4.app.NotificationCompat;
import android.view.View;
import android.widget.RemoteViews;


public class DownloadNotification {
    private NotificationManager notificationManager;
    private int id;
    private String titleText, successText, failureText;
    private String savePath;
    private Context context;
    private Download.DownloadType downloadType;

    public DownloadNotification(Context context, String title, String savePath, Download.DownloadType downloadType,String successText, String failureText) {
        this.context = context;
        this.savePath = savePath;
        this.titleText = title;
        this.successText = successText;
        this.failureText = failureText;
        this.downloadType = downloadType;
        notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.cancelAll();

    }
    public DownloadNotification(Context context, String title, String savePath, Download.DownloadType downloadType) {
        this.context = context;
        this.savePath = savePath;
        this.titleText = title;
        this.successText = "下载成功!";
        this.failureText = "下载失败!";
        this.downloadType = downloadType;
        notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.cancelAll();

    }
    public int getId() {
        return id;
    }

    public void createNotification() {
        id = new Random().nextInt(10000);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context);
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, new Intent(), PendingIntent.FLAG_UPDATE_CURRENT);

        builder.setContentTitle(titleText)
                .setContentText("")
                .setContentInfo("")
                .setProgress(100, 0, false)
                .setContentIntent(pendingIntent)
                .setWhen(System.currentTimeMillis())
                .setAutoCancel(false)
                .setOngoing(true)
                .setSmallIcon(android.R.drawable.stat_sys_download);

        notificationManager.notify(id, builder.build());
    }

    public void updateNotification(int progress, int speed) {

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context);
        builder.setContentTitle(titleText)
                .setContentText(FileUtils.formatSize(speed) + "/s")
                .setContentInfo(progress + "%")
                .setProgress(100, progress, false)
                .setWhen(System.currentTimeMillis())
                .setAutoCancel(false)
                .setOngoing(true)
                .setSmallIcon(android.R.drawable.stat_sys_download);

        notificationManager.notify(id, builder.build());
    }

    public void finishNotification() {
        PendingIntent pendingIntent = null;
        if (Download.DownloadType.APK == downloadType) {
            Uri uri = Uri.fromFile(new File(savePath));
            Intent installIntent = new Intent(Intent.ACTION_VIEW);
            installIntent.setDataAndType(uri, "application/vnd.android.package-archive");
            pendingIntent = PendingIntent.getActivity(context, 0, installIntent, 0);
        } else {
            pendingIntent = null;
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context);
        builder.setContentTitle(titleText)
                .setContentText(successText)
                .setContentInfo("")
                .setContentIntent(pendingIntent)
                .setWhen(System.currentTimeMillis())
                .setAutoCancel(true)
                .setOngoing(false)
                .setSmallIcon(android.R.drawable.stat_sys_download);
        notificationManager.notify(id, builder.build());
    }

    public void failureNotification() {

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context);
        builder.setContentTitle(titleText)
                .setContentText(failureText)
                .setContentInfo("")
                .setWhen(System.currentTimeMillis())
                .setAutoCancel(true)
                .setOngoing(false)
                .setSmallIcon(android.R.drawable.stat_sys_download);

        notificationManager.notify(id, builder.build());
    }


    public void cancelNotification() {
        if (notificationManager != null)
            notificationManager.cancel(id);
    }
}