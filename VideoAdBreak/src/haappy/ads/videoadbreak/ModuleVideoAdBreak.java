package haappy.ads.videoadbreak;

import com.wowza.wms.application.*;
import com.wowza.wms.amf.*;
import com.wowza.wms.client.*;
import com.wowza.wms.module.*;
import com.wowza.wms.request.*;
import com.wowza.wms.stream.*;


import com.wowza.wms.rtp.model.*;
import com.wowza.wms.httpstreamer.model.*;
import com.wowza.wms.httpstreamer.cupertinostreaming.httpstreamer.*;
import com.wowza.wms.httpstreamer.smoothstreaming.httpstreamer.*;
import com.wowza.wms.logging.WMSLogger;

public class ModuleVideoAdBreak extends ModuleBase {

	ServerPublisherWorker worker = null;
	public final static String logPrefix = "$$$$$$ ModuleVideoAdBreak $$$$$$--> ";
	private WMSLogger logger;
	
	public ModuleVideoAdBreak()
	{
		if (logger == null)
			logger = getLogger();
	}
	
	
	private void logInfo(String info) {
		logger.info(logPrefix + info);
	}

	private void logDebug(String msg) {
		logger.debug(logPrefix + msg);
	}

	private void logError(String error) {
		logger.error(logPrefix + error);
	}


	public void onAppStart(IApplicationInstance appInstance) {
		String fullname = appInstance.getApplication().getName() + "/" + appInstance.getName();
		getLogger().info("onAppStart: " + fullname);
		if (worker == null) {
			logInfo("starting ServerPublisherWorker");
			worker = new ServerPublisherWorker();
			worker.start();
		}
	}

	public void onAppStop(IApplicationInstance appInstance) {
		String fullname = appInstance.getApplication().getName() + "/" + appInstance.getName();
		getLogger().info("onAppStop: " + fullname);
		if (worker != null) {
			logInfo("stopping ServerPublisherWorker");
			worker.quit();
		}
		worker = null;
	}

	public void onConnect(IClient client, RequestFunction function, AMFDataList params) {
		getLogger().info("onConnect: " + client.getClientId());
	}

	public void onConnectAccept(IClient client) {
		getLogger().info("onConnectAccept: " + client.getClientId());
	}

	public void onConnectReject(IClient client) {
		getLogger().info("onConnectReject: " + client.getClientId());
	}

	public void onDisconnect(IClient client) {
		getLogger().info("onDisconnect: " + client.getClientId());
	}

	public void onStreamCreate(IMediaStream stream) {
		getLogger().info("onStreamCreate: " + stream.getSrc());
	}

	public void onStreamDestroy(IMediaStream stream) {
		getLogger().info("onStreamDestroy: " + stream.getSrc());
	}

	public void onHTTPSessionCreate(IHTTPStreamerSession httpSession) {
		getLogger().info("onHTTPSessionCreate: " + httpSession.getSessionId());
	}

	public void onHTTPSessionDestroy(IHTTPStreamerSession httpSession) {
		getLogger().info("onHTTPSessionDestroy: " + httpSession.getSessionId());
	}

	public void onHTTPCupertinoStreamingSessionCreate(HTTPStreamerSessionCupertino httpSession) {
		getLogger().info("onHTTPCupertinoStreamingSessionCreate: " + httpSession.getSessionId());
	}

	public void onHTTPCupertinoStreamingSessionDestroy(HTTPStreamerSessionCupertino httpSession) {
		getLogger().info("onHTTPCupertinoStreamingSessionDestroy: " + httpSession.getSessionId());
	}

	public void onHTTPSmoothStreamingSessionCreate(HTTPStreamerSessionSmoothStreamer httpSession) {
		getLogger().info("onHTTPSmoothStreamingSessionCreate: " + httpSession.getSessionId());
	}

	public void onHTTPSmoothStreamingSessionDestroy(HTTPStreamerSessionSmoothStreamer httpSession) {
		getLogger().info("onHTTPSmoothStreamingSessionDestroy: " + httpSession.getSessionId());
	}

	public void onRTPSessionCreate(RTPSession rtpSession) {
		getLogger().info("onRTPSessionCreate: " + rtpSession.getSessionId());
	}

	public void onRTPSessionDestroy(RTPSession rtpSession) {
		getLogger().info("onRTPSessionDestroy: " + rtpSession.getSessionId());
	}

	public void onCall(String handlerName, IClient client, RequestFunction function, AMFDataList params) {
		getLogger().info("onCall: " + handlerName);
	}

}
