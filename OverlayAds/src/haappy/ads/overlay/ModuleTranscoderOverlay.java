package haappy.ads.overlay;

import java.awt.Color;
import java.awt.Font;
import java.lang.reflect.Type;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.spi.TimeZoneNameProvider;

import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;

import org.apache.http.client.HttpClient;
import org.joda.time.DateTime;
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
import com.wowza.wms.logging.WMSLogger;
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
	ConcurrentHashMap<StreamTarget, StreamOverlayImageDetail> targetImageMap = new ConcurrentHashMap<>();
	private int eventPosition = 0;
	private WMSLogger logger;

	private String headerStr = "NDcueyJyb2xlIjoiY3VzdG9tZXIiLCJ2YWx1ZSI6IjJlNjJhMjI0YjQxNDRkZDFiZjdmZWU3YTJlM2M1NjliMzI1MzQyYTIwODE4NjU4ZTdlMjMyNmRlMWM4YzZlZWEiLCJrZXkiOjEwMDAwMH0=";
	int overlayIndex = 1;
	private IApplicationInstance appInstance = null;
	/**
	 * full path to the content directory where the graphics are kept
	 */
	private String basePath = null;
	private Object lock = new Object();
	TranscoderCreateNotifierExample trancoderNotifier = null;
	private final String logPrefix = "######## ModuleTranscoderOverlay ########--> ";

	public ModuleTranscoderOverlay() {

		if (logger == null)
			logger = getLogger();
	}

	private void logInfo(String info) {
		logger.info(logPrefix + info);
	}

	private void logError(String error) {
		logger.error(logPrefix + error);
	}

	public void onAppStart(IApplicationInstance appInstance) {
		String fullname = appInstance.getApplication().getName() + "/" + appInstance.getName();
		logInfo("onAppStart: " + fullname);
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
		if (trancoderNotifier == null) {
			trancoderNotifier = new TranscoderCreateNotifierExample();
			this.appInstance.addLiveStreamTranscoderListener(trancoderNotifier);
		}

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
		logInfo("onAppStop: " + fullname);
	}

	public void onConnect(IClient client, RequestFunction function, AMFDataList params) {
		logInfo("onConnect: " + client.getClientId());
	}

	public void onConnectAccept(IClient client) {
		logInfo("onConnectAccept: " + client.getClientId());
	}

	public void onConnectReject(IClient client) {
		logInfo("onConnectReject: " + client.getClientId());

	}

	public void onDisconnect(IClient client) {
		logInfo("onDisconnect: " + client.getClientId());
	}

	class TranscoderCreateNotifierExample implements ILiveStreamTranscoderNotify {
		public void onLiveStreamTranscoderCreate(ILiveStreamTranscoder liveStreamTranscoder, IMediaStream stream) {
			logInfo("ModuleTranscoderOverlayExample#TranscoderCreateNotifierExample.onLiveStreamTranscoderCreate["
					+ appInstance.getContextStr() + "]: " + stream.getName());

			((LiveStreamTranscoder) liveStreamTranscoder).addActionListener(new TranscoderActionNotifierExample());
		}

		public void onLiveStreamTranscoderDestroy(ILiveStreamTranscoder liveStreamTranscoder, IMediaStream stream) {
			logInfo("Destroy: " + stream.getName());

		}

		public void onLiveStreamTranscoderInit(ILiveStreamTranscoder liveStreamTranscoder, IMediaStream stream) {
		}
	}

	class TranscoderActionNotifierExample extends LiveStreamTranscoderActionNotifyBase {
		TranscoderVideoDecoderNotifyExample transcoder = null;

		public void onSessionVideoEncodeSetup(LiveStreamTranscoder liveStreamTranscoder,
				TranscoderSessionVideoEncode sessionVideoEncode) {
			logInfo("ModuleTranscoderOverlayExample#TranscoderActionNotifierExample.onSessionVideoEncodeSetup["
					+ appInstance.getContextStr() + "]");
			TranscoderStream transcoderStream = liveStreamTranscoder.getTranscodingStream();
			boolean chekc = transcoderStream != null && transcoder == null;
			logInfo("checking if condition-" + chekc);
			if (chekc) {
				TranscoderSession transcoderSession = liveStreamTranscoder.getTranscodingSession();
				TranscoderSessionVideo transcoderVideoSession = transcoderSession.getSessionVideo();
				List<TranscoderStreamDestination> alltrans = transcoderStream.getDestinations();
				int w = transcoderVideoSession.getDecoderWidth();
				int h = transcoderVideoSession.getDecoderHeight();
				logInfo("Changed graphic path- " + graphicName);
				logInfo("Creating new Overlay:" + "width-" + w + ", height-" + h);
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
		int srcWidth, srcHeight;
		int childPosition;
		int overlayScreenHeight;
		int overlayWidth;
		private String overlayText = "Haappy app overlay example transcoder with bottom text";
		String firstPosition = "CENTER_CENTER";
		String secondPosition = "RIGHT_TOP";
		// String thirdPosition = "LEFT_BOTTOM";
		int calculatedWidth, calculatedHeight;

		public TranscoderVideoDecoderNotifyExample(int srcWidth, int srcHeight) {
			logInfo("Creating TranscoderVideoDecoderNotifyExample");
			this.srcWidth = srcWidth;
			this.srcHeight = srcHeight;
			getAppId();
			// wowzaImage = new OverlayImage(basePath + graphicName, 100);
			// if (calculatedWidth == 0)
			// calculatedWidth = wowzaImage.GetWidth(1.0);
			// else if (calculatedWidth != srcWidth) {
			// calculatedWidth += wowzaImage.GetWidth(0.5);
			// }
			// if (calculatedHeight != srcHeight && calculatedHeight != 0) {
			// calculatedHeight += wowzaImage.GetHeight(0.5);
			// }

			// Create the Wowza logo image
			logInfo("screen width=" + srcWidth + " height = " + srcHeight);
			// secondImage = new OverlayImage(basePath + secondGraphName, 100);
			// holderImage = new OverlayImage(basePath + bottomImageName, 100);
			//
			// // mainImage.addOverlayImage(secondImage,0,0);
			//
			// // Add Text with a drop shadow
			// wowzaText = new OverlayImage(overlayText, 12, "SansSerif", Font.BOLD,
			// Color.white, srcWidth, 15, 100);
			// wowzaTextShadow = new OverlayImage(overlayText, 12, "SansSerif", Font.BOLD,
			// Color.darkGray, srcWidth, 15,
			// 100);
			// // create a transparent container for the bottom third of the
			// // screen.
			// overlayScreenHeight = holderImage.GetHeight(1.0) + wowzaText.GetHeight(1.0);
			// mainImage = new OverlayImage(0, srcHeight - overlayScreenHeight, srcWidth,
			// overlayScreenHeight, 100);
			// mainImage.addOverlayImage(holderImage, srcWidth - holderImage.GetWidth(1.0),
			// 0);
			// mainImage.addOverlayImage(wowzaText, wowzaImage.GetxPos(1.0) + 12,
			// overlayScreenHeight - wowzaText.GetHeight(1.0));
			// wowzaText.addOverlayImage(wowzaTextShadow, 1, 1);
			//
			// // do nothing for a bit
			// mainImage.addFadingStep(50);
			// wowzaImage.addImageStep(50);
			// secondImage.addImageStep(50);
			// holderImage.addImageStep(50);
			// wowzaText.addImageStep(50);
			// // Fade the logo and text
			// // mainImage.addFadingStep(0,100,100);
			//
			// // hold everything for a bit
			// mainImage.addFadingStep(50);
			// wowzaImage.addImageStep(50);
			// secondImage.addImageStep(50);
			// holderImage.addImageStep(50);
			// wowzaText.addImageStep(50);
			//
			// // Fade out
			// // mainImage.addFadingStep(100,0,50);
			// // wowzaImage.addImageStep(50);
			// // secondImage.addImageStep(50);
			// // wowzaImage.addFadingStep(50);
			// // holderImage.addFadingStep(50);
			// // secondImage.addFadingStep(50);
			//
			// // Pinch back video
			// videoBottomPadding.addAnimationStep(0, overlayScreenHeight, 50);
			// videoBottomPadding.addAnimationStep(200);
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

		Timer eventTimer = new Timer();

		private void getAppId() {
			logInfo("Starting getAppId()");
			Client client;
			WebResource webResource;
			try {
				client = Client.create();
				webResource = client.resource(ApiManager.getInstance().getChannelIdApi());
				JSONObject requestJson = new JSONObject();
				requestJson.put("applicationName", appInstance.getApplication().getName());
				String payload = requestJson.toString();
				logInfo("calling api getChannelId");
				ClientResponse response = webResource.accept(MediaType.APPLICATION_JSON)
						.type(MediaType.APPLICATION_JSON).header(HttpHeaders.AUTHORIZATION, headerStr)
						.post(ClientResponse.class, payload);
				logInfo("completed api getChannelId");
				int responseStatus = response.getStatus();
				String responseStr = response.getEntity(String.class);
				if (responseStatus != ClientResponse.Status.OK.getStatusCode()) {
					logError("Channel Id api call error " + responseStatus + "-->" + responseStr);
				} else {
					JSONObject responseObject = new JSONObject(responseStr);
					String id = responseObject.getString("channelId");
					logInfo("ChannelId API response " + id);
					getScheduledAds(id);
				}
			} catch (Exception e) {
				logInfo("api error " + e.getMessage());
			}
		}

		private void getScheduledAds(String id) {
			Client client;
			WebResource webResource;
			try {
				client = Client.create();
				// String url = "http://localhost:8080/LLCWeb/engage/ads/get/event/channel/"+id;
				String url = ApiManager.getInstance().getChannelEventApi(id);
				webResource = client.resource(url);
				logInfo("calling api getChannelEventApi");
				ClientResponse response = webResource.accept(MediaType.APPLICATION_JSON)
						.type(MediaType.APPLICATION_JSON).header(HttpHeaders.AUTHORIZATION, headerStr)
						.get(ClientResponse.class);
				logInfo("completed api getChannelEventApi");
				int responseStatus = response.getStatus();
				String responseStr = response.getEntity(String.class);
				if (responseStatus != ClientResponse.Status.OK.getStatusCode()) {
					logError("Event api call error " + responseStatus + "-->" + responseStr);
				} else {
					logInfo("Event API response success ");
					Gson gson = new Gson();
					Type type = new TypeToken<EventModel[]>() {
					}.getRawType();
					EventModel[] eventsArray = gson.fromJson(responseStr, type);
					List<EventModel> eventList = new ArrayList<>();
					eventList.addAll(Arrays.asList(eventsArray));
					if (!eventList.isEmpty()) {

						for (EventModel eventItem : eventList) {
							getEventsAds(eventItem);
						}

					} else {
						eventTimer.schedule(new TimerTask() {

							@Override
							public void run() {
								getScheduledAds(id);

							}
						}, TimeUnit.MINUTES.toMillis(1));
					}
				}
			} catch (Exception e) {

				logInfo("api error " + e.getMessage());
			}

		}

		// TODO:ANANDH
		// private void startEventTimer(EventModel eventModel) {
		// Timer timer = new Timer();
		// timer.schedule(new TimerTask() {
		//
		// @Override
		// public void run() {
		// getEventsAds(eventModel);
		// }
		// }, 0, TimeUnit.HOURS.toMillis(eventModel.getDuration()) + 1000);
		//
		// }
		Timer adsScheduler = new Timer();

		protected void getEventsAds(EventModel eventModel) {
			Client client;
			WebResource webResource;
			try {
				client = Client.create();
				// String url = "http://localhost:8080/LLCWeb/engage/ads/get/logo/event/" +
				// eventList.get(0).getId();
				String url = ApiManager.getInstance().getEventAdsApi(eventModel.getId());
				webResource = client.resource(url);
				logInfo("calling api getAds");
				ClientResponse response = webResource.accept(MediaType.APPLICATION_JSON)
						.type(MediaType.APPLICATION_JSON).header(HttpHeaders.AUTHORIZATION, headerStr)
						.get(ClientResponse.class);
				logInfo("completed api getAds");
				int responseStatus = response.getStatus();
				String responseStr = response.getEntity(String.class);
				if (responseStatus != ClientResponse.Status.OK.getStatusCode()) {
					logError("ADS API response ERROR ");
				} else {
					logInfo("ADS API response success ");
					Gson gson = new Gson();
					Type type = new TypeToken<AdsModel[]>() {
					}.getRawType();

					AdsModel[] ads = gson.fromJson(responseStr, type);
					long timerFrequencyInMinutes = 0;
					if (ads != null && ads.length > 0) {
						for (AdsModel adModel : ads) {
							logInfo("received ads - " + adModel.getAdPlacement());
							setupadsImages(adModel);
						}

						timerFrequencyInMinutes = calculateFrequency(ads[0]);
					}

					if (timerFrequencyInMinutes == 0) {
						targetImageMap.clear();
						TimeZone indianTimeZone = TimeZone.getTimeZone("Asia/Kolkata");
						if (indianTimeZone == null)
							indianTimeZone = TimeZone.getTimeZone("Asia/Calcutta");
						Calendar calendar = Calendar.getInstance(indianTimeZone);
						Date currentTime = calendar.getTime();
						String[] split = eventModel.getStartTime().split(":");
						calendar.set(Calendar.HOUR_OF_DAY, Integer.parseInt(split[0].trim()));
						calendar.set(Calendar.MINUTE, Integer.parseInt(split[1].trim()));
						calendar.add(Calendar.HOUR, eventModel.getDuration()); // get the end time
						Date eventEndTime = calendar.getTime();

						if (currentTime.getTime() < eventEndTime.getTime()) {
							Calendar calendarNew = Calendar.getInstance(indianTimeZone);
							Date newCurrentTime = calendarNew.getTime();
							calendarNew.add(Calendar.MINUTE, eventModel.getAdWindowTime());
							Date adWindowEndTime = calendarNew.getTime();
							long timerFrequencyInMilli = adWindowEndTime.getTime() - newCurrentTime.getTime();
							timerFrequencyInMinutes = TimeUnit.MILLISECONDS.toMinutes(timerFrequencyInMilli);
						}
					}
					logInfo("timer frequency: " + timerFrequencyInMinutes);
					if (timerFrequencyInMinutes > 0) {
						logInfo("Scheduling with frequency: " + timerFrequencyInMinutes);
						adsScheduler.schedule(new TimerTask() {
							@Override
							public void run() {
								getEventsAds(eventModel);
							}
						}, TimeUnit.MINUTES.toMillis(timerFrequencyInMinutes));

					} else {
						targetImageMap.clear();
					}

				}
			} catch (Exception e) {
				logInfo("api error " + e.getMessage());
			}

		}

		private long calculateFrequency(AdsModel adModel) {
			logInfo("calculating frequency for admodel: " + adModel.getId() + " " + adModel.getAdEventId());
			TimeZone indianTimeZone = TimeZone.getTimeZone("Asia/Kolkata");
			if (indianTimeZone == null)
				indianTimeZone = TimeZone.getTimeZone("Asia/Calcutta");
			Calendar calendar = Calendar.getInstance(indianTimeZone);
			String[] split = adModel.getTimeSlotEnd().split(":");
			Date currentTime = calendar.getTime();
			calendar.set(Calendar.HOUR_OF_DAY, Integer.parseInt(split[0].trim()));
			calendar.set(Calendar.MINUTE, Integer.parseInt(split[1].trim()));
			Date endTime = calendar.getTime();
			long difference = endTime.getTime() - currentTime.getTime();
			return difference > 0 ? TimeUnit.MILLISECONDS.toMinutes(difference) : 0;

		}

		private void setupadsImages(AdsModel adModel) {
			String imagePath = adModel.getLogoFtpPath();
			String placement = adModel.getAdPlacement();
			logInfo("Executing for admodel: " + adModel.getId() + " " + adModel.getAdEventId());
			calculatedWidth = Math.round(getOverlayPositionX(srcWidth, placement));
			calculatedHeight = Math.round(getOverlayPositionY(srcHeight, placement));
			logInfo("placement-" + placement);
			OverlayImage wowzaImage;
			if (Environment.isDebugMode()) {
				StreamTarget tar = StreamTarget.valueOf(adModel.getAdTarget().toLowerCase());
				wowzaImage = new OverlayImage(basePath + (tar == StreamTarget.facebook ? graphicName : secondGraphName),
						100);
				logInfo("Image Path: " + basePath + secondGraphName);
			} else {
				wowzaImage = new OverlayImage(imagePath, 100);
				logInfo(imagePath);
			}

			logInfo("update OverlayImage for admodel: " + adModel.getId() + " " + adModel.getAdEventId());

			if (calculatedWidth == 0)
				calculatedWidth = wowzaImage.GetWidth(1.0);
			else if (calculatedWidth != srcWidth) {
				calculatedWidth += wowzaImage.GetWidth(0.5);
			}
			if (calculatedHeight != srcHeight && calculatedHeight != 0) {
				calculatedHeight += wowzaImage.GetHeight(0.5);
			}
			// create a transparent container for the bottom third of the screen.
			OverlayImage mainImage = new OverlayImage(0, srcHeight - calculatedHeight, srcWidth,
					wowzaImage.GetHeight(1.0), 100);
			// Create the Wowza logo image
			logInfo("screen width=" + srcWidth + " calculatedWidth = " + calculatedWidth);
			// secondImage = new OverlayImage(basePath+graphicName,100);
			// logInfo("Image path "+basePath+graphicName);
			overlayScreenHeight = 0;
			mainImage.addOverlayImage(wowzaImage, srcWidth - calculatedWidth, 0);
			StreamOverlayImageDetail mainImageDetails = new StreamOverlayImageDetail(mainImage, adModel.getAdTarget());
			logInfo("updated images for target: " + mainImageDetails.getTarget());
			targetImageMap.put(mainImageDetails.getTarget(), mainImageDetails);
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

				int sourceHeight = sessionVideo.getDecoderHeight();
				int sourceWidth = sessionVideo.getDecoderWidth();
				for (EncoderInfo encoderInfo : encoderInfoList) {
					if (!encoderInfo.destinationVideo.isPassThrough()) {
						StreamTarget key = StreamManager.getInstance().getStreamTarget(encoderInfo.encodeName);
						if (targetImageMap.containsKey(key)) {

							StreamOverlayImageDetail imageDetails = targetImageMap.get(key);
							OverlayImage mainImage = imageDetails.getMainImage();
							// logger.debug("overlaying for : " + key);
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
	}

	class StreamOverlayImageDetail {
		private StreamTarget target = StreamTarget.None;
		OverlayImage mainImage;

		public StreamTarget getTarget() {
			return target;
		}

		public void setTarget(StreamTarget target) {
			this.target = target;
		}

		public OverlayImage getMainImage() {
			return mainImage;
		}

		public void setMainImage(OverlayImage mainImage) {
			this.mainImage = mainImage;
		}

		public StreamOverlayImageDetail(OverlayImage image, StreamTarget target) {
			this.mainImage = image;
			this.target = target;
		}

		public StreamOverlayImageDetail(OverlayImage image, String target) {
			this(image, StreamTarget.valueOf(target.toLowerCase()));

		}

	}

}