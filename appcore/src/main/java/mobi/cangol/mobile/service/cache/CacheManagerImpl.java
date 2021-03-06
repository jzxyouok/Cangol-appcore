/** 
 * Copyright (c) 2013 Cangol
 * 
 * Licensed under the Apache License, Version 2.0 (the "License")
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
package mobi.cangol.mobile.service.cache;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Iterator;

import mobi.cangol.mobile.logging.Log;
import mobi.cangol.mobile.service.Service;
import mobi.cangol.mobile.service.ServiceProperty;
import mobi.cangol.mobile.utils.Object2FileUtils;
import android.annotation.TargetApi;
import android.content.Context;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Environment;
import android.os.StatFs;
import android.text.TextUtils;

/**
 * @author Cangol
 */

@Service("CacheManager")
public class CacheManagerImpl implements CacheManager {
	private static final String TAG = "CacheManager";
	private static final int DISK_CACHE_INDEX = 0;
	private static final long DEFAULT_DISK_CACHE_SIZE = 1024 * 1024 * 20; // 20MB
	private boolean mDebug;
	private DiskLruCache mDiskLruCache;
	private HashMap<String, HashMap<String, Serializable>> mContextMaps = new HashMap<String, HashMap<String, Serializable>>();
	private final Object mDiskCacheLock = new Object();
	private boolean mDiskCacheStarting = true;
	private File mDiskCacheDir;
	private long mDiskCacheSize;
	private ServiceProperty mServiceProperty;
	private Context mContext;
	@Override
	public void onCreate(Context context) {
		this.mContext = context;
	}
	@Override
	public void init(ServiceProperty serviceProperty) {
		this.mServiceProperty = serviceProperty;
		String dir = mServiceProperty.getString(CacheManager.CACHE_DIR);
		long size = mServiceProperty.getLong(CacheManager.CACHE_SIZE);
		setDiskCache(
				!TextUtils.isEmpty(dir) ? getDiskCacheDir(mContext, dir) : getDiskCacheDir(mContext, "ContentCache"),
				size > 0 ? size : DEFAULT_DISK_CACHE_SIZE);
	}

	/**
	 * 设置磁盘缓存位置，并初始化
	 * @param cacheDir
	 * @param cacheSize
	 */
	private void setDiskCache(File cacheDir, long cacheSize) {
		this.mDiskCacheDir = cacheDir;
		this.mDiskCacheSize = cacheSize;
		mDiskCacheStarting = true;
		Log.i(TAG, "mDiskCacheDir:" + mDiskCacheDir);
		initDiskCache(mDiskCacheDir, mDiskCacheSize);
	}

	/**
	 * 初始化磁盘缓存
	 * @param diskCacheDir
	 * @param diskCacheSize
	 */
	private void initDiskCache(File diskCacheDir, long diskCacheSize) {
		// Set up disk cache
		synchronized (mDiskCacheLock) {
			if (mDiskLruCache == null || mDiskLruCache.isClosed()) {
				if (diskCacheDir != null) {
					if (!diskCacheDir.exists()) {
						diskCacheDir.mkdirs();
					}
					if (getUsableSpace(diskCacheDir) > diskCacheSize) {
						try {
							mDiskLruCache = DiskLruCache.open(diskCacheDir, 1, 1, diskCacheSize);
							if (mDebug) {
								Log.d(TAG, "Disk cache initialized");
							}
						} catch (final IOException e) {
							Log.e(TAG, "initDiskCache - " + e);
						}
					}
				}
			}
			mDiskCacheStarting = false;
			mDiskCacheLock.notifyAll();
		}
	}

	@Override
	public Serializable getContent(String context, String id) {
		HashMap<String, Serializable> contextMap = mContextMaps.get(context);
		if (null == contextMap) {
			contextMap = new HashMap<String, Serializable>();
			mContextMaps.put(context, contextMap);
		}
        Serializable obj = contextMap.get(id);
		if (obj == null) {
			obj = getContentFromDiskCache(id);
			if (obj != null)
				contextMap.put(id, obj);
		}
		return obj;
	}

	@Override
	public void getContent(final String context, final String id, final CacheLoader cacheLoader) {
		HashMap<String, Serializable> contextMap = mContextMaps.get(context);
		if (null == contextMap) {
			contextMap = new HashMap<String, Serializable>();
			mContextMaps.put(context, contextMap);
		}
		Object obj = contextMap.get(id);
		if (obj == null) {
			new AsyncTask<String, Void, Serializable>() {
				@Override
				protected void onPreExecute() {
					super.onPreExecute();
					if (cacheLoader != null)
						cacheLoader.loading();
				}

				@Override
				protected Serializable doInBackground(String... params) {
					return getContentFromDiskCache(params[0]);
				}

				@Override
				protected void onPostExecute(Serializable result) {
					super.onPostExecute(result);
					if (result != null)
						addContentToMem(context, id, result);
					if (cacheLoader != null)
						cacheLoader.returnContent(result);
				}
			}.execute(id);

		} else if (cacheLoader != null)
			cacheLoader.returnContent(obj);
	}

	@Override
	public boolean hasContent(String context, String id) {
		HashMap<String, Serializable> contextMap = mContextMaps.get(context);
		if (null == contextMap) {
			contextMap = new HashMap<String, Serializable>();
			mContextMaps.put(context, contextMap);
		}
		Object obj = contextMap.get(id);
		if (obj == null) {
			return hasContentFromDiskCache(id);
		} else {
			return true;
		}
	}

	/**
	 * 判断磁盘缓存是否含有
	 * @param id
	 * @return
	 */
	private boolean hasContentFromDiskCache(String id) {
		final String key = hashKeyForDisk(id);
		synchronized (mDiskCacheLock) {
			while (mDiskCacheStarting) {
				try {
					mDiskCacheLock.wait();
				} catch (InterruptedException e) {
				}
			}
			if (mDiskLruCache != null) {
				InputStream inputStream = null;
				try {
					final DiskLruCache.Snapshot snapshot = mDiskLruCache.get(key);
					if (snapshot != null) {
						inputStream = snapshot.getInputStream(DISK_CACHE_INDEX);
						if (inputStream != null) {
							return true;
						}
					}
				} catch (final IOException e) {
					Log.e(TAG, "getContentFromDiskCache - " + e);
				} finally {
					try {
						if (inputStream != null) {
							inputStream.close();
						}
					} catch (IOException e) {
					}
				}
			}
			return false;
		}
	}

	/**
	 * 从磁盘缓存获取
	 * @param id
	 * @return
	 */
	private Serializable getContentFromDiskCache(String id) {
		final String key = hashKeyForDisk(id);
		synchronized (mDiskCacheLock) {
			while (mDiskCacheStarting) {
				try {
					mDiskCacheLock.wait();
				} catch (InterruptedException e) {
				}
			}
			if (mDiskLruCache != null) {
				InputStream inputStream = null;
				try {
					final DiskLruCache.Snapshot snapshot = mDiskLruCache.get(key);
					if (snapshot != null) {
						inputStream = snapshot.getInputStream(DISK_CACHE_INDEX);
						if (inputStream != null) {
							return Object2FileUtils.readObject(inputStream);
						}
					}
				} catch (final IOException e) {
					Log.e(TAG, "getContentFromDiskCache - " + e);
				} finally {
					try {
						if (inputStream != null) {
							inputStream.close();
						}
					} catch (IOException e) {
					}
				}
			}
			return null;
		}

	}

	/**
	 * 添加到内存缓存
	 * @param context
	 * @param id
	 * @param data
	 */
	private void addContentToMem(String context, String id, Serializable data) {
		HashMap<String, Serializable> contextMap = mContextMaps.get(context);
		if (null == contextMap) {
			contextMap = new HashMap<String, Serializable>();
		}
		contextMap.put(id, data);
		mContextMaps.put(context, contextMap);
	}

	/**
	 * 添加到磁盘缓存（也添加到内存缓存）
	 */
	@Override
	public void addContent(String context, String id, Serializable data) {
		Log.i(TAG, "addContent:" + id + "," + data);
		removeContent(context, id);
		addContentToMem(context, id, data);
		// addContentToDiskCache(id,data);
		asyncAddContentToDiskCache(id, data);
	}

	/**
	 * context暂停或退出时，持久化context关联的缓存（持久化到磁盘）
	 * @param context
	 */
	private void moveContentToDiskCache(String context) {
		HashMap<String, Serializable> contextMap = mContextMaps.get(context);
		if (null == contextMap || contextMap.isEmpty())
			return;
		Iterator<String> iterator = contextMap.keySet().iterator();
		String id = null;
		while (iterator.hasNext()) {
			id = iterator.next();
			// addContentToDiskCache(key,contextMap.get(key));
			asyncAddContentToDiskCache(id, contextMap.get(id));
		}
		contextMap.clear();
		mContextMaps.remove(context);
	}

	/**
	 * 异步添加到磁盘缓存
	 * @param id
	 * @param data
	 */
	private void asyncAddContentToDiskCache(final String id, final Serializable data) {
		new AsyncTask<Void, Void, Void>() {
			@Override
			protected Void doInBackground(Void... params) {
				addContentToDiskCache(id, data);
				return null;
			}
		}.execute();
	}

	/**
	 * 添加到磁盘缓存
	 * @param id
	 * @param data
	 */
	private void addContentToDiskCache(String id, Serializable data) {

		synchronized (mDiskCacheLock) {
			// Add to disk cache
			if (mDiskLruCache != null) {
				final String key = hashKeyForDisk(id);
				OutputStream out = null;
				try {
					DiskLruCache.Snapshot snapshot = mDiskLruCache.get(key);
					if (snapshot == null) {
						final DiskLruCache.Editor editor = mDiskLruCache.edit(key);
						if (editor != null) {
							out = editor.newOutputStream(DISK_CACHE_INDEX);
							// 写入out流
							Object2FileUtils.writeObject(data, out);
							editor.commit();
							out.close();
							flush();
						}
					} else {
						snapshot.getInputStream(DISK_CACHE_INDEX).close();
					}
				} catch (final IOException e) {
					Log.e(TAG, "addContentToCache - " + e);
				} catch (Exception e) {
					Log.e(TAG, "addContentToCache - " + e);
				} finally {
					try {
						if (out != null) {
							out.close();
						}
					} catch (IOException e) {
					}
				}
			}
		}
	}

	@Override
	public void removeContext(String context) {
		HashMap<String, Serializable> contextMap = mContextMaps.get(context);
		if (null == contextMap || contextMap.isEmpty())
			return;
		Iterator<String> iterator = contextMap.keySet().iterator();
		String id = null;
		while (iterator.hasNext()) {
			id = iterator.next();
			String key = hashKeyForDisk(id);
			try {
				if (mDiskLruCache != null) {
					mDiskLruCache.remove(key);
				}
			} catch (IOException e) {
				if (mDebug)
					Log.d(TAG, "cache remove" + key, e);
			}
		}
		contextMap.clear();
		mContextMaps.remove(context);
	}
	@Override
	public void removeContent(String context, String id) {
		HashMap<String, Serializable> contextMap = mContextMaps.get(context);
		if (null == contextMap || contextMap.isEmpty())
			return;
		contextMap.remove(id);
		String key = hashKeyForDisk(id);
		try {
			if (mDiskLruCache != null) {
				mDiskLruCache.remove(key);
			}
		} catch (IOException e) {
			if (mDebug)
				Log.d(TAG, "cache remove" + key, e);
		}
	}
	@Override
	public long size() {
		long size = 0;
		synchronized (mDiskCacheLock) {
			if (mDiskLruCache != null) {
				size = mDiskLruCache.size();
			}
		}
		return size;
	}
	@Override
	public void clearCache() {
		if (mContextMaps != null) {
			mContextMaps.clear();
			if (mDebug) {
				Log.d(TAG, "Memory cache cleared");
			}
		}

		synchronized (mDiskCacheLock) {
			mDiskCacheStarting = true;
			if (mDiskLruCache != null && !mDiskLruCache.isClosed()) {
				try {
					mDiskLruCache.delete();
					if (mDebug) {
						Log.d(TAG, "Disk cache cleared");
					}
				} catch (IOException e) {
					Log.e(TAG, "clearCache - " + e);
				}
				mDiskLruCache = null;
				initDiskCache(mDiskCacheDir, mDiskCacheSize);
			}
		}
	}
	@Override
	public void flush() {
		synchronized (mDiskCacheLock) {
			if (mDiskLruCache != null) {
				try {
					mDiskLruCache.flush();
					if (mDebug) {
						Log.d(TAG, "Disk cache flushed");
					}
				} catch (IOException e) {
					Log.e(TAG, "flush - " + e);
				}
			}
		}
	}
	@Override
	public void close() {
		synchronized (mDiskCacheLock) {
			if (mDiskLruCache != null) {
				try {
					if (!mDiskLruCache.isClosed()) {
						mDiskLruCache.close();
						mDiskLruCache = null;
						if (mDebug) {
							Log.d(TAG, "Disk cache closed");
						}
					}
				} catch (IOException e) {
					Log.e(TAG, "close - " + e);
				}
			}
		}
	}

	private String hashKeyForDisk(String key) {
		String cacheKey;
		try {
			final MessageDigest mDigest = MessageDigest.getInstance("MD5");
			mDigest.update(key.getBytes());
			cacheKey = bytesToHexString(mDigest.digest());
		} catch (NoSuchAlgorithmException e) {
			cacheKey = String.valueOf(key.hashCode());
		}
		return cacheKey;
	}

	private String bytesToHexString(byte[] bytes) {
		// http://stackoverflow.com/questions/332079
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < bytes.length; i++) {
			String hex = Integer.toHexString(0xFF & bytes[i]);
			if (hex.length() == 1) {
				sb.append('0');
			}
			sb.append(hex);
		}
		return sb.toString();
	}

	private File getDiskCacheDir(Context context, String uniqueName) {
		// Check if media is mounted or storage is built-in, if so, try and use
		// external cache dir
		// otherwise use internal cache dir
		String cachePath = null;
		if (Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState()) || !isExternalStorageRemovable()) {
			cachePath = getExternalCacheDir(context).getPath();
		} else {
			cachePath = context.getCacheDir().getPath();
		}

		return new File(cachePath + File.separator + uniqueName);
	}

	@TargetApi(9)
	private boolean isExternalStorageRemovable() {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD) {
			return Environment.isExternalStorageRemovable();
		}
		return true;
	}

	@TargetApi(8)
	private File getExternalCacheDir(Context context) {
		File file = null;
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.FROYO) {
			file = context.getExternalCacheDir();
		}
		if (file == null) {
			// Before Froyo we need to construct the external cache dir
			// ourselves
			final String cacheDir = "/Android/data/" + context.getPackageName() + "/cache/";
			file = new File(Environment.getExternalStorageDirectory().getPath() + cacheDir);
			file.mkdirs();
			return file;
		}
		return file;
	}

	@TargetApi(9)
	private long getUsableSpace(File path) {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD) {
			return path.getUsableSpace();
		}
		final StatFs stats = new StatFs(path.getPath());
		return (long) stats.getBlockSize() * (long) stats.getAvailableBlocks();
	}

	@Override
	public String getName() {
		return TAG;
	}

	@Override
	public void onDestroy() {
		this.close();
	}

	@Override
	public void setDebug(boolean debug) {
		this.mDebug = debug;
	}

	@Override
	public ServiceProperty getServiceProperty() {
		return mServiceProperty;
	}
	@Override
	public ServiceProperty defaultServiceProperty() {
		ServiceProperty sp=new ServiceProperty(TAG);
		sp.putString(CACHE_DIR, "contentCache");
		sp.putInt(CACHE_SIZE, 20971520);
		return sp;
	}

}
