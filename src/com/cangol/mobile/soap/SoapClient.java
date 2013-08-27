/** 
 * Copyright (c) 2013 xuewu.wei.
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
package com.cangol.mobile.soap;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;

import org.ksoap2.SoapEnvelope;
import org.ksoap2.serialization.SoapObject;
import org.ksoap2.serialization.SoapSerializationEnvelope;
import org.ksoap2.transport.HttpTransportSE;
import org.kxml2.kdom.Element;
import org.kxml2.kdom.Node;

import android.content.Context;
import android.util.Log;

/**
 * @Description SoapRequest.java
 * @author xuewu.wei
 * @date 2013-8-14
 */
public class SoapClient {
	private static final String TAG = "SoapRequest";
	private SoapSerializationEnvelope envelope;
	private HttpTransportSE  ht;
	private ThreadPoolExecutor threadPool;
	private final Map<Context, List<WeakReference<Future<?>>>> requestMap;
	private final static int TIMEOUT=20*1000;
	public SoapClient() {
		threadPool = (ThreadPoolExecutor)Executors.newCachedThreadPool();
		requestMap = new WeakHashMap<Context, List<WeakReference<Future<?>>>>();
		envelope = new SoapSerializationEnvelope(SoapEnvelope.VER12);
		envelope.dotNet = true;
	}
	public void addHeader(String namespace,String authheader,HashMap<String, String> headers){
		if(authheader!=null&&headers!=null){
			envelope.headerOut = new Element[1];
			envelope.headerOut[0] = buildAuthHeader(namespace,authheader,headers);
		}
	}
	public void send(Context context,String url, String namespace, String action,HashMap<String, String> params,SoapResponseHandler responseHandler) {
		
		if (params != null) {
			StringBuilder paramsStr = new StringBuilder(url);
			paramsStr.append('/'+action+'?');
			SoapObject rpc = new SoapObject(namespace, action);
			for (Map.Entry<String, String> entry : params.entrySet()) {
				rpc.addProperty(entry.getKey(), entry.getValue());
				paramsStr.append(entry.getKey()).append("=").append(entry.getValue()).append("&");
			}
			Log.d(TAG, "sendRequest "+paramsStr.toString());
			envelope.bodyOut = rpc;
		}
		ht=new HttpTransportSE(url,TIMEOUT);
		sendRequest(ht,envelope,namespace,responseHandler,context);
	}
	private  Element buildAuthHeader(String namespace,String authheader,HashMap<String, String> params) {
		Element header = new Element().createElement(namespace, authheader);
		for (Map.Entry<String, String> entry : params.entrySet()) {
			Element element = new Element().createElement(namespace, entry.getKey());
			element.addChild(Node.TEXT, entry.getValue());
			header.addChild(Node.ELEMENT, element);
		}
		return header;
	}
	public void cancelRequests(Context context, boolean mayInterruptIfRunning) {
	        List<WeakReference<Future<?>>> requestList = requestMap.get(context);
	        if(requestList != null) {
	            for(WeakReference<Future<?>> requestRef : requestList) {
	                Future<?> request = requestRef.get();
	                if(request != null) {
	                    request.cancel(mayInterruptIfRunning);
	                }
	            }
	        }
	        requestMap.remove(context);
	 }
	protected void sendRequest(HttpTransportSE ht, SoapSerializationEnvelope envelope,
			String namespace, SoapResponseHandler responseHandler,Context context) {

        Future<?> request = threadPool.submit(new SoapRequest(ht, envelope, namespace, responseHandler));

        if(context != null) {
            // Add request to request map
            List<WeakReference<Future<?>>> requestList = requestMap.get(context);
            if(requestList == null) {
                requestList = new LinkedList<WeakReference<Future<?>>>();
                requestMap.put(context, requestList);
            }
            requestList.add(new WeakReference<Future<?>>(request));
        }
    }
}
