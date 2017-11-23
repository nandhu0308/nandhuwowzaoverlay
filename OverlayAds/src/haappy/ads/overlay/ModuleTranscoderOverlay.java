package haappy.ads.overlay;

import java.awt.Color;
import java.awt.Font;
import java.lang.reflect.Type;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;

import org.apache.http.client.HttpClient;
import org.json.JSONObject;

import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.core.util.MultivaluedMapImpl;
import com.wowza.util.SystemUtils;
import com.wowza.wms.application.*;
import com.wowza.wms.amf.*;
import com.wowza.wms.client.*;
import com.wowza.wms.module.*;
import com.wowza.wms.request.*;
import com.wowza.wms.stream.*;
import com.wowza.wms.stream.livetranscoder.ILiveStreamTranscoder;
import com.wowza.wms.stream.livetranscoder.ILiveStreamTranscoderNotify;
import com.wowza.wms.transcoder.model.LiveStreamTranscoder;
import com.wowza.wms.transcoder.model.LiveStreamTranscoderActionNotifyBase;
import com.wowza.wms.transcoder.model.TranscoderSession;
import com.wowza.wms.transcoder.model.TranscoderSessionVideo;
import com.wowza.wms.transcoder.model.TranscoderSessionVideoEncode;
import com.wowza.wms.transcoder.model.TranscoderStream;
import com.wowza.wms.transcoder.model.TranscoderStreamDestination;
import com.wowza.wms.transcoder.model.TranscoderStreamDestinationVideo;
import com.wowza.wms.transcoder.model.TranscoderStreamSourceVideo;
import com.wowza.wms.transcoder.model.TranscoderVideoDecoderNotifyBase;
import com.wowza.wms.transcoder.model.TranscoderVideoOverlayFrame;

public class ModuleTranscoderOverlay extends ModuleBase {

	String graphicName = "logo.png";
	String secondGraphName = "wowzalogo.png";
	LiveStreamTranscoder liveStreamTranscoder;
	TranscoderVideoDecoderNotifyExample transcoderVideo;
	private List<EventModel> eventList = new ArrayList<>();
	private int eventPosition = 0;
	
	private String headerStr = "NDA5MC57InJvbGUiOiJjdXN0b21lciIsInZhbHVlIjoiOWE3OWVmNTM3YmFmYWYwMDRhZDAxNjc3Y2RiM2U4NGFiNTUyNGIzNThiZGQ5Nzk2MTE3ZGVhZmE0MTMxMzBhNiIsImtleSI6MTAwMTE3fQ==";
	int overlayIndex = 1;
	private IApplicationInstance appInstance = null;
	/**
	 * full path to the content directory where the graphics are kept
	 */
	private String basePath = null;
	private Object lock = new Object();

	public void onAppStart(IApplicationInstance appInstance) {
		String fullname = appInstance.getApplication().getName() + "/" + appInstance.getName();
		getLogger().info("onAppStart: " + fullname);
		this.appInstance = appInstance;
		String artworkPath = "${com.wowza.wms.context.VHostConfigHome}/content/"
				+ appInstance.getApplication().getName();
		Map<String, String> envMap = new HashMap<String, String>();
		if (appInstance.getVHost() != null) {
			envMap.put("com.wowza.wms.context.VHost", appInstance.getVHost().getName());
			envMap.put("com.wowza.wms.context.VHostConfigHome", appInstance.getVHost().getHomePath());
		}
		envMap.put("com.wowza.wms.context.Application", appInstance.getApplication().getName());
		if (this != null)
			envMap.put("com.wowza.wms.context.ApplicationInstance", appInstance.getName());
		this.basePath = SystemUtils.expandEnvironmentVariables(artworkPath, envMap);
		this.basePath = this.basePath.replace("\\", "/");
		if (!this.basePath.endsWith("/"))
			this.basePath = this.basePath + "/";
		this.appInstance.addLiveStreamTranscoderListener(new TranscoderCreateNotifierExample());

	}

	class EncoderInfo {
		public String encodeName;
		public TranscoderSessionVideoEncode sessionVideoEncode = null;
		public TranscoderStreamDestinationVideo destinationVideo = null;
		/**
		 * array on ints that defines number of pixels to pad the video by.
		 * Left,Top,Right,Bottom
		 */
		public int[] videoPadding = new int[4];

		public EncoderInfo(String name, TranscoderSessionVideoEncode sessionVideoEncode,
				TranscoderStreamDestinationVideo destinationVideo) {
			this.encodeName = name;
			this.sessionVideoEncode = sessionVideoEncode;
			this.destinationVideo = destinationVideo;
		}
	}

	public void onAppStop(IApplicationInstance appInstance) {
		String fullname = appInstance.getApplication().getName() + "/" + appInstance.getName();
		getLogger().info("onAppStop: " + fullname);
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

	class TranscoderCreateNotifierExample implements ILiveStreamTranscoderNotify {
		public void onLiveStreamTranscoderCreate(ILiveStreamTranscoder liveStreamTranscoder, IMediaStream stream) {
			getLogger()
					.info("ModuleTranscoderOverlayExample#TranscoderCreateNotifierExample.onLiveStreamTranscoderCreate["
							+ appInstance.getContextStr() + "]: " + stream.getName());

			((LiveStreamTranscoder) liveStreamTranscoder).addActionListener(new TranscoderActionNotifierExample());
		}

		public void onLiveStreamTranscoderDestroy(ILiveStreamTranscoder liveStreamTranscoder, IMediaStream stream) {
			getLogger().info("Destroy: " + stream.getName());

		}

		public void onLiveStreamTranscoderInit(ILiveStreamTranscoder liveStreamTranscoder, IMediaStream stream) {
		}
	}

	class TranscoderActionNotifierExample extends LiveStreamTranscoderActionNotifyBase {
		TranscoderVideoDecoderNotifyExample transcoder = null;

		public void onSessionVideoEncodeSetup(LiveStreamTranscoder liveStreamTranscoder,
				TranscoderSessionVideoEncode sessionVideoEncode) {
			getLogger().info("ModuleTranscoderOverlayExample#TranscoderActionNotifierExample.onSessionVideoEncodeSetup["
					+ appInstance.getContextStr() + "]");
			TranscoderStream transcoderStream = liveStreamTranscoder.getTranscodingStream();
			boolean chekc = transcoderStream != null && transcoder == null;
			getLogger().info("checking if condition-" + chekc);
			if (chekc) {
				TranscoderSession transcoderSession = liveStreamTranscoder.getTranscodingSession();
				TranscoderSessionVideo transcoderVideoSession = transcoderSession.getSessionVideo();
				List<TranscoderStreamDestination> alltrans = transcoderStream.getDestinations();
				int w = transcoderVideoSession.getDecoderWidth();
				int h = transcoderVideoSession.getDecoderHeight();
				getLogger().info("Changed graphic path- " + graphicName);
				getLogger().info("Creating new Overlay:" + "width-" + w + ", height-" + h);
				transcoder = new TranscoderVideoDecoderNotifyExample(w, h);
				transcoderVideoSession.addFrameListener(transcoder);
				// transcoderVideoSession.removeFrameListener(transcoder);

				// apply an overlay to all outputs
				for (TranscoderStreamDestination destination : alltrans) {
					// TranscoderSessionVideoEncode sessionVideoEncode =
					// transcoderVideoSession.getEncode(destination.getName());
					TranscoderStreamDestinationVideo videoDestination = destination.getVideo();
					System.out.println("sessionVideoEncode:" + sessionVideoEncode);
					System.out.println("videoDestination:" + videoDestination);
					if (sessionVideoEncode != null && videoDestination != null) {
						transcoder.addEncoder(destination.getName(), sessionVideoEncode, videoDestination);
					}
				}

			}
			return;
		}

	}

	public float getOverlayPositionX(int screenWidth, String position) {
		float positionX = screenWidth;
		position = position.split("_")[1];
		switch (position) {
		case "CENTER":
			positionX = screenWidth / 2;
			break;
		case "RIGHT":
			positionX = 0;
			break;
		}
		return positionX;
	}

	public float getOverlayPositionY(int screenHeight, String position) {
		float positionY = 0;
		position = position.split("_")[0];
		switch (position) {
		case "TOP":
			positionY = screenHeight;
			break;
		case "MIDDLE":
			positionY = screenHeight / 2;
			break;
		}
		return positionY;
	}

	class TranscoderVideoDecoderNotifyExample extends TranscoderVideoDecoderNotifyBase {
		private volatile OverlayImage mainImage = null;
		private volatile OverlayImage wowzaImage = null;
		private OverlayImage wowzaText = null;
		private OverlayImage wowzaTextShadow = null;
		List<EncoderInfo> encoderInfoList = new ArrayList<EncoderInfo>();
		AnimationEvents videoBottomPadding = new AnimationEvents();
		private boolean imageTime = false;
		private OverlayImage secondImage = null;
		private OverlayImage holderImage = null;
		private String bottomImageName = "bottom_image.png";
		private String leftIamgeName = "haappy_lBand_vertical.png";
		// private String bottomImageName = "haappyapp-lband.png";
		private String fullOverlayImage = "full_mage.png";
		int srcWidth,srcHeight;
		int childPosition;
		int overlayScreenHeight;
		int overlayWidth;
		private String overlayText = "Haappy app overlay example transcoder with bottom text";
		String firstPosition = "CENTER_CENTER";
		String secondPosition = "RIGHT_TOP";
		// String thirdPosition = "LEFT_BOTTOM";
		 int calculatedWidth,calculatedHeight; 
		
		
		
		public TranscoderVideoDecoderNotifyExample(int srcWidth, int srcHeight) {
			this.srcWidth = srcWidth;
			this.srcHeight = srcHeight;
			getAppId();
//			wowzaImage = new OverlayImage(basePath + graphicName, 100);
//			 if (calculatedWidth == 0)
//			 calculatedWidth = wowzaImage.GetWidth(1.0);
//			 else if (calculatedWidth != srcWidth) {
//			 calculatedWidth += wowzaImage.GetWidth(0.5);
//			 }
//			 if (calculatedHeight != srcHeight && calculatedHeight != 0) {
//			 calculatedHeight += wowzaImage.GetHeight(0.5);
//			 }

			// Create the Wowza logo image
			getLogger().info("screen width=" + srcWidth + " height = " + srcHeight);
//			secondImage = new OverlayImage(basePath + secondGraphName, 100);
//			holderImage = new OverlayImage(basePath + bottomImageName, 100);
//
//			// mainImage.addOverlayImage(secondImage,0,0);
//
//			// Add Text with a drop shadow
//			wowzaText = new OverlayImage(overlayText, 12, "SansSerif", Font.BOLD, Color.white, srcWidth, 15, 100);
//			wowzaTextShadow = new OverlayImage(overlayText, 12, "SansSerif", Font.BOLD, Color.darkGray, srcWidth, 15,
//					100);
//			// create a transparent container for the bottom third of the
//			// screen.
//			overlayScreenHeight = holderImage.GetHeight(1.0) + wowzaText.GetHeight(1.0);
//			mainImage = new OverlayImage(0, srcHeight - overlayScreenHeight, srcWidth, overlayScreenHeight, 100);
//			mainImage.addOverlayImage(holderImage, srcWidth - holderImage.GetWidth(1.0), 0);
//			mainImage.addOverlayImage(wowzaText, wowzaImage.GetxPos(1.0) + 12,
//					overlayScreenHeight - wowzaText.GetHeight(1.0));
//			wowzaText.addOverlayImage(wowzaTextShadow, 1, 1);
//
//			// do nothing for a bit
//			mainImage.addFadingStep(50);
//			wowzaImage.addImageStep(50);
//			secondImage.addImageStep(50);
//			holderImage.addImageStep(50);
//			wowzaText.addImageStep(50);
//			// Fade the logo and text
//			// mainImage.addFadingStep(0,100,100);
//
//			// hold everything for a bit
//			mainImage.addFadingStep(50);
//			wowzaImage.addImageStep(50);
//			secondImage.addImageStep(50);
//			holderImage.addImageStep(50);
//			wowzaText.addImageStep(50);
//
//			// Fade out
//			// mainImage.addFadingStep(100,0,50);
//			// wowzaImage.addImageStep(50);
//			// secondImage.addImageStep(50);
//			// wowzaImage.addFadingStep(50);
//			// holderImage.addFadingStep(50);
//			// secondImage.addFadingStep(50);
//
//			// Pinch back video
//			videoBottomPadding.addAnimationStep(0, overlayScreenHeight, 50);
//			videoBottomPadding.addAnimationStep(200);
			// unpinch the video
			// videoBottomPadding.addAnimationStep(130, 0, 50);
			// mainImage.addFadingStep(150);
			// wowzaImage.addImageStep(150);
			// secondImage.addImageStep(150);
			// holderImage.addImageStep(150);
			// videoBottomPadding.addAnimationStep(100);
			// holderImage = wowzaImage;
			// startImageTimer();
		}
		
		private void getAppId(){
			Client client;
			WebResource webResource;
			try {
				client = Client.create();
				webResource = client.resource("http://localhost:8080/LLCWeb/engage/broadcast/application/channel");
				JSONObject requestJson = new JSONObject();
				requestJson.put("applicationName", appInstance.getApplication().getName());
				getLogger().info("Get api Response");
				String payload = requestJson.toString();
				ClientResponse response = webResource.accept(MediaType.APPLICATION_JSON)
						.type(MediaType.APPLICATION_JSON).header(HttpHeaders.AUTHORIZATION, headerStr)
						.post(ClientResponse.class,payload);
				int responseStatus = response.getStatus();
				String responseStr = response.getEntity(String.class);
				if (responseStatus != ClientResponse.Status.OK.getStatusCode()) {
					getLogger().error("api call error " + responseStatus + "-->" + responseStr);
				} else {
					JSONObject responseObject = new JSONObject(responseStr);
					String id = responseObject.getString("channelId");
					getLogger().info("app id response "+id);
					getScheduledAds(id);
				}
			} catch (Exception e) {
				getLogger().info("api error " + e.getMessage());
			}
		}

		private void getScheduledAds(String id) {
			Client client;
			WebResource webResource;
			try {
				client = Client.create();
				String url = "http://localhost:8080/LLCWeb/engage/ads/get/event/channel/"+id;
				webResource = client.resource(url);

				ClientResponse response = webResource.accept(MediaType.APPLICATION_JSON)
						.type(MediaType.APPLICATION_JSON).header(HttpHeaders.AUTHORIZATION, headerStr)
						.get(ClientResponse.class);
				int responseStatus = response.getStatus();
				String responseStr = response.getEntity(String.class);
				if (responseStatus != ClientResponse.Status.OK.getStatusCode()) {
					getLogger().error("api call error " + responseStatus + "-->" + responseStr);
				} else {
					Gson gson = new Gson();
					Type type = new TypeToken<EventModel[]>() {
					}.getRawType();
					EventModel[] eventsArray = gson.fromJson(responseStr, type);
					eventList.addAll(Arrays.asList(eventsArray));
					if(!eventList.isEmpty()){
						startEventTimer();
					}
				}
			} catch (Exception e) {
				getLogger().info("api error " + e.getMessage());
			}

		}

		private void startEventTimer() {
			Timer timer = new Timer();
			timer.schedule(new TimerTask() {

				@Override
				public void run() {
					getEventsAds();
				}
			}, 0,TimeUnit.MINUTES.toMillis(eventList.get(0).getAdWindowTime()));

		}


		protected void getEventsAds() {
			Client client;
			WebResource webResource;
			try {
				client = Client.create();
				String url = "http://localhost:8080/LLCWeb/engage/ads/get/logo/event/"+eventList.get(0).getId();
				webResource = client.resource(url);			
				ClientResponse response = webResource.accept(MediaType.APPLICATION_JSON)
						.type(MediaType.APPLICATION_JSON).header(HttpHeaders.AUTHORIZATION, headerStr)
						.get(ClientResponse.class);
				int responseStatus = response.getStatus();
				String responseStr = response.getEntity(String.class);
				if (responseStatus != ClientResponse.Status.OK.getStatusCode()) {
				} else {
					Gson gson = new Gson();
					Type type = new TypeToken<AdsModel[]>() {
					}.getRawType();
					
					AdsModel[] ads = gson.fromJson(responseStr, type);
					getLogger().info("received ads - "+ ads[0].getAdPlacement());
					setupadsImages(ads[0].getLogoFtpPath(),ads[0].getAdPlacement());
				}
			} 
			catch (Exception e) {
				getLogger().info("api error " + e.getMessage());
			}
			
		}
		
		private void setupadsImages(String imagePath, String placement){
			calculatedWidth = Math.round(getOverlayPositionX(srcWidth,placement));
			calculatedHeight = Math.round(getOverlayPositionY(srcHeight,placement));
			getLogger().info("placement-"+placement);
//			wowzaImage = new OverlayImage(imagePath, 100);
			wowzaImage = new OverlayImage(basePath+secondGraphName, 100);
			if (calculatedWidth == 0)
				calculatedWidth = wowzaImage.GetWidth(1.0);
			else if (calculatedWidth != srcWidth) {
				calculatedWidth += wowzaImage.GetWidth(0.5);
			}
			if (calculatedHeight != srcHeight && calculatedHeight != 0) {
				calculatedHeight += wowzaImage.GetHeight(0.5);
			}
			//create a transparent container for the bottom third of the screen.
			mainImage = new OverlayImage(0,srcHeight-calculatedHeight,srcWidth,wowzaImage.GetHeight(1.0),100);
			//Create the Wowza logo image
			getLogger().info("screen width="+srcWidth+" calculatedWidth = "+calculatedWidth);
			secondImage = new OverlayImage(basePath+graphicName,100);
			getLogger().info("Image path "+basePath+graphicName);
			overlayScreenHeight = 0;
			mainImage.addOverlayImage(wowzaImage,srcWidth-calculatedWidth,0);
			imageTime = true;
		}
		

		public void addEncoder(String name, TranscoderSessionVideoEncode sessionVideoEncode,
				TranscoderStreamDestinationVideo destinationVideo) {
			encoderInfoList.add(new EncoderInfo(name, sessionVideoEncode, destinationVideo));
		}

		public void onBeforeScaleFrame(TranscoderSessionVideo sessionVideo, TranscoderStreamSourceVideo sourceVideo,
				long frameCount) {
			boolean encodeSource = false;
			boolean showTime = false;
			double scalingFactor = 1.0;
			synchronized (lock) {

				if (mainImage != null) {

					if (imageTime) {
						imageTime = false;
						mainImage.removeChild(holderImage);
						if (childPosition == 0) {
							holderImage = wowzaImage;
							childPosition = 1;
						} else {
							holderImage = secondImage;
							childPosition = 0;
						}
						mainImage.addOverlayImage(holderImage, srcWidth - calculatedWidth, 0);
					}

					// does not need to be done for a static graphic, but left
					// here to build on (transparency/animation)
					// videoBottomPadding.step();
					// mainImage.step();
					int sourceHeight = sessionVideo.getDecoderHeight();
					int sourceWidth = sessionVideo.getDecoderWidth();
					// getLogger().info("Showtime = "+showTime);
					 if(showTime)
					 {
					 Date dNow = new Date( );
					 SimpleDateFormat ft = new SimpleDateFormat("hh:mm:ss");
					// wowzaText.SetText(ft.format(dNow));
					// wowzaTextShadow.SetText(ft.format(dNow));
					 }
					if (encodeSource) {
						// put the image onto the source
						scalingFactor = 1.0;
						TranscoderVideoOverlayFrame overlay = new TranscoderVideoOverlayFrame(
								mainImage.GetWidth(scalingFactor), mainImage.GetHeight(scalingFactor),
								mainImage.GetBuffer(scalingFactor));
						overlay.setDstX(mainImage.GetxPos(scalingFactor));
						overlay.setDstY(mainImage.GetyPos(scalingFactor));
						sourceVideo.addOverlay(overlayIndex, overlay);
					} else {
						/// put the image onto each destination but scaled to
						/// fit
						for (EncoderInfo encoderInfo : encoderInfoList) {
							if (!encoderInfo.destinationVideo.isPassThrough()) {
								int destinationHeight = encoderInfo.destinationVideo.getFrameSizeHeight();
								scalingFactor = (double) destinationHeight / (double) sourceHeight;
								TranscoderVideoOverlayFrame overlay = new TranscoderVideoOverlayFrame(
										mainImage.GetWidth(scalingFactor), mainImage.GetHeight(scalingFactor),
										mainImage.GetBuffer(scalingFactor));
								overlay.setDstX(mainImage.GetxPos(scalingFactor));
								overlay.setDstY(mainImage.GetyPos(scalingFactor));
								encoderInfo.destinationVideo.addOverlay(overlayIndex, overlay);
								// Add padding to the destination video i.e.
								// pinch
								encoderInfo.videoPadding[0] = 0;// (int)(((double)videoLeftPadding.getStepValue())*scalingFactor);;
																// // left
								encoderInfo.videoPadding[1] = 0; // top
								encoderInfo.videoPadding[2] = 0; // right
								encoderInfo.videoPadding[3] = (int) (overlayScreenHeight * scalingFactor);// bottom
								encoderInfo.destinationVideo.setPadding(encoderInfo.videoPadding);
							}
						}
					}
				}
			}
			return;
		}
	}

}