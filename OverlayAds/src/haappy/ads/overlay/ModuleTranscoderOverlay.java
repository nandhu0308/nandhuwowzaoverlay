package haappy.ads.overlay;

import java.awt.Color;
import java.awt.Font;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;

import org.json.JSONObject;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;
import com.wowza.util.SystemUtils;
import com.wowza.wms.amf.AMFDataList;
import com.wowza.wms.application.IApplicationInstance;
import com.wowza.wms.client.IClient;
import com.wowza.wms.logging.WMSLogger;
import com.wowza.wms.module.ModuleBase;
import com.wowza.wms.request.RequestFunction;
import com.wowza.wms.stream.IMediaStream;
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

import haappy.ads.overlay.ModuleTranscoderOverlay.EncoderInfo;

public class ModuleTranscoderOverlay extends ModuleBase {

	String graphicName = "logo.png";
	String secondGraphName = "wowzalogo.png";

	LiveStreamTranscoder liveStreamTranscoder;

	ConcurrentHashMap<String, StreamOverlayImageDetail> targetImageMap = new ConcurrentHashMap<>();
	ConcurrentHashMap<String, String> liveTargetImageMap = new ConcurrentHashMap<>();
	// ConcurrentHashMap<String, AdsModel> expiringAdModel = new
	// ConcurrentHashMap<>();

	private WMSLogger logger;
	Map<String, String> envMap;
	private String headerStr = "NDcueyJyb2xlIjoiY3VzdG9tZXIiLCJ2YWx1ZSI6IjJlNjJhMjI0YjQxNDRkZDFiZjdmZWU3YTJlM2M1NjliMzI1MzQyYTIwODE4NjU4ZTdlMjMyNmRlMWM4YzZlZWEiLCJrZXkiOjEwMDAwMH0=";
	private long pollingFrequency = 1000;
	private long eventPollingFrequency = 1000;
	private boolean enableOverlayDebugLog = false;
	private IApplicationInstance appInstance = null;
	/**
	 * full path to the content directory where the graphics are kept
	 */
	private String basePath = null;
	private Object lock = new Object();
	TranscoderCreateNotifier trancoderNotifier = null;
	public final static String logPrefix = "######## ModuleTranscoderOverlay ########--> ";

	public ModuleTranscoderOverlay() {

		if (logger == null)
			logger = getLogger();
	}

	private void logInfo(String info) {
		logger.info(logPrefix + info);
	}

	private void logDebug(String msg) {
		if (!enableOverlayDebugLog)
			logger.debug(logPrefix + msg);
		else
			logInfo(msg);
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
		if (envMap != null) {
			envMap.clear();
		} else {
			envMap = new HashMap<String, String>();
		}
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
		pollingFrequency = appInstance.getProperties().getPropertyLong("pollingFrequency", 1000);
		eventPollingFrequency = appInstance.getProperties().getPropertyLong("eventPollingFrequency", 1000);
		enableOverlayDebugLog = appInstance.getProperties().getPropertyBoolean("enableOverlayDebugLog", false);
		if (trancoderNotifier == null) {
			trancoderNotifier = new TranscoderCreateNotifier();
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

	class TranscoderCreateNotifier implements ILiveStreamTranscoderNotify {
		TranscoderActionNotifier transcoderActionNotifier;

		@Override
		public void onLiveStreamTranscoderCreate(ILiveStreamTranscoder liveStreamTranscoder, IMediaStream stream) {
			logInfo("ModuleTranscoderOverlay#TranscoderCreateNotifier.onLiveStreamTranscoderCreate["
					+ appInstance.getContextStr() + "]: " + stream.getName());
			transcoderActionNotifier = new TranscoderActionNotifier();
			((LiveStreamTranscoder) liveStreamTranscoder).addActionListener(transcoderActionNotifier);
		}

		@Override
		public void onLiveStreamTranscoderDestroy(ILiveStreamTranscoder liveStreamTranscoder, IMediaStream stream) {
			logInfo("Destroy: " + stream.getName());
			destroy();
		}

		private void destroy() {
			liveTargetImageMap.clear();
			List<EncoderInfo> encoderInfoList = null;
			if (transcoderActionNotifier != null && transcoderActionNotifier.transcoder != null) {
				encoderInfoList = transcoderActionNotifier.transcoder.encoderInfoList;
			} else {
				encoderInfoList = new ArrayList<EncoderInfo>();
			}

			for (AdType adType : AdType.getavailableTypes()) {
				int overlayLogoIndex = AdType.getOverlayIndex(adType);

				for (EncoderInfo encoderInfo : encoderInfoList) {
					logInfo("Encoder: " + encoderInfo.encodeName + " overlayindex: " + overlayLogoIndex);
					encoderInfo.destinationVideo.clearOverlay(overlayLogoIndex);
					VideoPadding padding = VideoPadding.getVideoPadding(AdType.NONE);
					encoderInfo.videoPadding[0] = padding.getLeft(1);
					encoderInfo.videoPadding[1] = padding.getTop(1);
					encoderInfo.videoPadding[2] = padding.getRight(1);
					encoderInfo.videoPadding[3] = padding.getBottom(1);
					encoderInfo.destinationVideo.setPadding(encoderInfo.videoPadding);

				}
			}
		}

		@Override
		public void onLiveStreamTranscoderInit(ILiveStreamTranscoder liveStreamTranscoder, IMediaStream stream) {
		}
	}

	class TranscoderActionNotifier extends LiveStreamTranscoderActionNotifyBase {
		public TranscoderVideoDecoderNotify transcoder = null;

		@Override
		public void onSessionVideoEncodeSetup(LiveStreamTranscoder liveStreamTranscoder,
				TranscoderSessionVideoEncode sessionVideoEncode) {
			logInfo("ModuleTranscoderOverlay#TranscoderActionNotifier.onSessionVideoEncodeSetup["
					+ appInstance.getContextStr() + "]");
			TranscoderStream transcoderStream = liveStreamTranscoder.getTranscodingStream();
			boolean chekc = transcoderStream != null && transcoder == null;

			if (chekc) {
				TranscoderSession transcoderSession = liveStreamTranscoder.getTranscodingSession();
				TranscoderSessionVideo transcoderVideoSession = transcoderSession.getSessionVideo();
				List<TranscoderStreamDestination> alltrans = transcoderStream.getDestinations();
				int w = transcoderVideoSession.getDecoderWidth();
				int h = transcoderVideoSession.getDecoderHeight();
				logDebug("Changed graphic path- " + graphicName);
				logInfo("TranscoderVideoSession :" + "width-" + w + ", height-" + h);
				transcoder = new TranscoderVideoDecoderNotify(w, h);
				transcoderVideoSession.addFrameListener(transcoder);
				// transcoderVideoSession.removeFrameListener(transcoder);

				// apply an overlay to all outputs
				for (TranscoderStreamDestination destination : alltrans) {
					// TranscoderSessionVideoEncode sessionVideoEncode =
					// transcoderVideoSession.getEncode(destination.getName());
					TranscoderStreamDestinationVideo videoDestination = destination.getVideo();
					logDebug("sessionVideoEncode:" + sessionVideoEncode.getName());
					logDebug("videoDestination:" + destination.getName());
					logDebug("DisplayWidth: " + videoDestination.getDisplayWidth() + " DisplayHeight: "
							+ videoDestination.getDisplayHeight());
					;
					logDebug("FrameSizeWidth: " + videoDestination.getFrameSizeWidth() + " FrameSizeHeight: "
							+ videoDestination.getFrameSizeHeight());
					logDebug("FrameWidth: " + videoDestination.getFrameWidth() + "  FrameHeight: "
							+ videoDestination.getFrameHeight());
					if (sessionVideoEncode != null && videoDestination != null) {
						transcoder.addEncoder(destination.getName(), sessionVideoEncode, videoDestination);
					}
				}

			}
			return;
		}

	}

	public float getOverlayPositionX(int screenWidth, String position) {
		float positionX = 0;
		position = position.split("_")[1];
		switch (position) {
		case "CENTER":
			positionX = screenWidth / 2;
			break;
		case "RIGHT":
			positionX = screenWidth;
			break;
		}
		return positionX;
	}

	public float getOverlayPositionY(int screenHeight, String position) {
		float positionY = screenHeight;
		position = position.split("_")[0];
		switch (position) {
		case "TOP":
			positionY = 0;
			break;
		case "MIDDLE":
			positionY = screenHeight / 2;
			break;
		}
		return positionY;
	}

	class TranscoderVideoDecoderNotify extends TranscoderVideoDecoderNotifyBase {

		public List<EncoderInfo> encoderInfoList = new ArrayList<EncoderInfo>();
		AnimationEvents videoBottomPadding = new AnimationEvents();
		int srcWidth, srcHeight;
		int childPosition;

		int overlayWidth;
		String firstPosition = "CENTER_CENTER";
		String secondPosition = "RIGHT_TOP";
		// String thirdPosition = "LEFT_BOTTOM";
		// int calculatedWidth, calculatedHeight;

		public TranscoderVideoDecoderNotify(int srcWidth, int srcHeight) {
			logInfo("Creating TranscoderVideoDecoderNotify");
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

		private EventState eventState;

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
					logInfo("No Channel configured for the application name" + appInstance.getApplication().getName());
				} else {
					JSONObject responseObject = new JSONObject(responseStr);
					String id = responseObject.getString("channelId");
					logInfo("ChannelId API response " + id);
					getScheduledAds(id);
				}
			} catch (Exception e) {
				logError("api error " + e.getMessage());
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
				logDebug("calling api getChannelEventApi");
				ClientResponse response = webResource.accept(MediaType.APPLICATION_JSON)
						.type(MediaType.APPLICATION_JSON).header(HttpHeaders.AUTHORIZATION, headerStr)
						.get(ClientResponse.class);
				logDebug("completed api getChannelEventApi");
				int responseStatus = response.getStatus();
				String responseStr = response.getEntity(String.class);
				if (responseStatus != ClientResponse.Status.OK.getStatusCode()) {

					logDebug("No Events available. scheduling for request in next " + eventPollingFrequency
							+ " milliseconds");
					eventTimer.schedule(new TimerTask() {
						@Override
						public void run() {
							getScheduledAds(id);

						}
					}, eventPollingFrequency); // TODO: ANANDH how to find the optimal ping
												// time?
				} else {
					logDebug("Event API response success ");
					Gson gson = new Gson();
					Type type = new TypeToken<EventModel>() {
					}.getType();
					EventModel event = gson.fromJson(responseStr, type);
					if (event != null) {
						eventState = EventState.started;
						getEventsAds(event);
						eventTimer.schedule(new TimerTask() {

							@Override
							public void run() {
								eventState = EventState.ended;
								getScheduledAds(id);
							}
						}, CalendarHelper.getEventEndTimeForTimerSchedule(event));

					}
				}
			} catch (Exception e) {

				logError("Get event schedule api error " + e.getMessage());
			}

		}

		Timer adsScheduler = new Timer();

		protected void getEventsAds(EventModel eventModel) {
			Client client;
			WebResource webResource;
			try {
				client = Client.create();
				String url = ApiManager.getInstance().getEventAdsApi(eventModel.getId());
				webResource = client.resource(url);
				logDebug("calling api getAds");
				ClientResponse response = webResource.accept(MediaType.APPLICATION_JSON)
						.type(MediaType.APPLICATION_JSON).header(HttpHeaders.AUTHORIZATION, headerStr)
						.get(ClientResponse.class);
				logDebug("completed api getAds");
				int responseStatus = response.getStatus();
				String responseStr = response.getEntity(String.class);
				if (responseStatus != ClientResponse.Status.OK.getStatusCode()) {
					logError("ADS API response ERROR ");
				} else {
					logDebug("ADS API response success ");
					Gson gson = new Gson();
					Type type = new TypeToken<AdsModel[]>() {
					}.getRawType();

					final AdsModel[] ads = gson.fromJson(responseStr, type);
					long timerFrequencyInMinutes = 0;
					final HashMap<Integer, AdsModel> expiringAdModel = new HashMap<>();

					if (ads != null && ads.length > 0) {
						logDebug("TotalAds: " + ads.length);
						int counter = 0;
						for (AdsModel adModel : ads) {
							if (!liveTargetImageMap.containsKey(adModel.getHashMapKey())) {
								logInfo("received ads - " + adModel.getAdPlacement());
								switch (adModel.getEventAdType()) {
								case BOTTOM_BAR:
									setupBottomImage(adModel);
									break;
								case L_BAND:
									setupFullscreenOverlay(adModel);
								default:
									setupadsImages(adModel);
									break;
								}
							} else {
								logDebug("Skipping ad: " + adModel.getHashMapKey());
							}
							// Find the least possible timer frequency
							long tempTimerFrequencyInMinutes = calculateFrequency(adModel);
							if (counter == 0) {
								// assume the first frequency as the smallest
								timerFrequencyInMinutes = tempTimerFrequencyInMinutes;
							}
							if (tempTimerFrequencyInMinutes <= timerFrequencyInMinutes) {
								// clear previous values only if current time is less than previous. Ignore if
								// it is equal
								if (tempTimerFrequencyInMinutes < timerFrequencyInMinutes) {
									logDebug("clearing expiring model: " + adModel.getHashMapKey());
									expiringAdModel.clear();
								}
								logDebug("Adding to expiring model: " + adModel.getHashMapKey());
								expiringAdModel.put(adModel.getId(), adModel);
								timerFrequencyInMinutes = tempTimerFrequencyInMinutes;
							}

							counter++;
						}

					}
					boolean startPolling = false;
					if (timerFrequencyInMinutes == 0) {
						Calendar calendar = CalendarHelper.getCalendarForIndianTimeZone();
						Date currentTime = calendar.getTime();
						Date eventEndTime = CalendarHelper.getEventEndTime(eventModel, calendar);
						if (currentTime.getTime() < eventEndTime.getTime()) {
							timerFrequencyInMinutes = pollingFrequency;
							startPolling = true;
						}
					}
					logDebug("timer frequency: " + timerFrequencyInMinutes);
					if (timerFrequencyInMinutes > 0) {
						long frequencyInMs = !startPolling ? TimeUnit.MINUTES.toMillis(timerFrequencyInMinutes)
								: pollingFrequency;
						logDebug("Scheduling with frequency: " + frequencyInMs + " milliseconds");

						adsScheduler.schedule(new TimerTask() {
							@Override
							public void run() {
								try {
									// Clear the overlay for only expired ad model
									logDebug("Total expiring Ads: " + expiringAdModel.values().size());
									clearOverlay(expiringAdModel.values());
									getEventsAds(eventModel);
								} catch (Exception ex) {
									logError(ex.toString());
								}
							}

						}, frequencyInMs);

					} else {
						// Clear and Reset all the overlays
						clearOverlay(Arrays.asList(ads));
					}

				}
			} catch (Exception e) {
				logError("Get Ads api error " + e.getMessage());
			}

		}

		private void clearOverlay(final Collection<AdsModel> ads) {
			if (ads != null && ads.size() > 0) {
				List<String> keys = new ArrayList<>();
				for (AdsModel adModel : ads) {
					String key = adModel.getHashMapKey();
					// Clear the previous overlay and then add the new overlay
					int overlayLogoIndex = adModel.getOverlayIndex();
					boolean clearPadding = adModel.getEventAdType() == AdType.L_BAND;
					for (EncoderInfo encoderInfo : encoderInfoList) {
						logInfo("Encoder: " + encoderInfo.encodeName + ": clearing overlay " + key + " overlayindex: "
								+ overlayLogoIndex);
						encoderInfo.destinationVideo.clearOverlay(overlayLogoIndex);
						if (clearPadding) {
							VideoPadding padding = VideoPadding.getVideoPadding(AdType.NONE);
							encoderInfo.videoPadding[0] = padding.getLeft(1);
							encoderInfo.videoPadding[1] = padding.getTop(1);
							encoderInfo.videoPadding[2] = padding.getRight(1);
							encoderInfo.videoPadding[3] = padding.getBottom(1);
							encoderInfo.destinationVideo.setPadding(encoderInfo.videoPadding);
						}
					}
					keys.add(key);

				}

				keys.forEach((key) -> {
					if (liveTargetImageMap.containsKey(key))
						liveTargetImageMap.remove(key);
				});
			}
		}

		private long calculateFrequency(AdsModel adModel) {
			logDebug("calculating frequency for admodel: " + adModel.getId() + " " + adModel.getAdEventId());
			TimeZone indianTimeZone = TimeZone.getTimeZone("Asia/Kolkata");
			if (indianTimeZone == null)
				indianTimeZone = TimeZone.getTimeZone("Asia/Calcutta");
			Calendar calendar = Calendar.getInstance(indianTimeZone);
			String[] split = adModel.getTimeSlotEnd().split(":");
			Date currentTime = calendar.getTime();
			calendar.set(Calendar.HOUR_OF_DAY, Integer.parseInt(split[0].trim()));
			calendar.set(Calendar.MINUTE, Integer.parseInt(split[1].trim()));
			Date endTime = calendar.getTime();
			logDebug("EndTime: " + endTime + " : CurrentTime: " + currentTime);
			long difference = endTime.getTime() - currentTime.getTime();
			return difference > 0 ? TimeUnit.MILLISECONDS.toMinutes(difference) : 0;

		}

		// call this method to show bottom overlay only
		private void setupBottomImage(AdsModel adModel) {
			String imagePath = adModel.getLogoFtpPath();
			logInfo("Executing for admodel: " + adModel.getId() + " " + adModel.getAdEventId());
			OverlayImage wowzaImage;
			int posX = Math.round(getOverlayPositionX(srcWidth, "BOTTOM_LEFT"));
			int posY = Math.round(getOverlayPositionY(srcHeight, "BOTTOM_LEFT"));

			logInfo("Image Path: " + imagePath);
			// Create the Wowza logo image
			wowzaImage = new OverlayImage(imagePath, 100, logger, envMap);
			logInfo("update OverlayImage for admodel: " + adModel.getId() + " " + adModel.getAdEventId());

			String lowerText = adModel.getLowerText();
			boolean isTextAvailable = lowerText != null && !lowerText.isEmpty();
			OverlayImage mainImage, wowzaText = null;
			// add text bellow overlay Image
			if (isTextAvailable) {
				// Add Text with a drop shadow
				wowzaText = new OverlayImage(lowerText, 20, "SansSerif", Font.BOLD, Color.red, srcWidth, srcHeight,
						100);
				// wowzaTextShadow = new OverlayImage(lowerText, 12, "SansSerif", Font.BOLD,
				// Color.DARK_GRAY, srcWidth, 15,
				// 100);

				logInfo("Overlay text - " + lowerText);

			}
			mainImage = new OverlayImage(posX, posY, srcWidth, wowzaImage.GetHeight(1.0), 100);
			mainImage.addOverlayImage(wowzaImage, 0, 0);

			if (isTextAvailable) {
				// TODO: ANANDH Set the proper X and Y Pos
				mainImage.addOverlayImage(wowzaText, srcWidth / 2, srcHeight / 2);
			}

			StreamOverlayImageDetail mainImageDetails = new StreamOverlayImageDetail(mainImage, adModel.getAdTarget(),
					imagePath, adModel.getEventAdType(), srcWidth, srcHeight);
			logInfo("updated images for target: " + mainImageDetails.getTarget());
			targetImageMap.put(mainImageDetails.getHashMapKey(), mainImageDetails);
		}

		// show full image overlay
		private void setupFullscreenOverlay(AdsModel adModel) {
			String imagePath = adModel.getLogoFtpPath();
			OverlayImage wowzaImage, mainImage;
			logInfo("Image Path: " + imagePath);
			wowzaImage = new OverlayImage(imagePath, 100, logger, envMap);
			logInfo("update OverlayImage for admodel: " + adModel.getId() + " " + adModel.getAdEventId());
			mainImage = new OverlayImage(0, 0, srcWidth, srcHeight, 100);
			mainImage.addOverlayImage(wowzaImage, 0, 0);
			StreamOverlayImageDetail mainImageDetails = new StreamOverlayImageDetail(mainImage, adModel.getAdTarget(),
					imagePath, adModel.getEventAdType(), srcWidth, srcHeight);
			logInfo("updated images for target: " + mainImageDetails.getTarget());
			targetImageMap.put(mainImageDetails.getHashMapKey(), mainImageDetails);
		}

		private void setupadsImages(AdsModel adModel) {
			String imagePath = adModel.getLogoFtpPath();
			String placement = adModel.getAdPlacement();
			logInfo("Executing for admodel: " + adModel.getId() + " " + adModel.getAdEventId());
			int posX = Math.round(getOverlayPositionX(srcWidth, placement));
			int posY = Math.round(getOverlayPositionY(srcHeight, placement));
			logInfo("placement-" + placement);
			OverlayImage wowzaImage;
			logInfo("Image Path: " + imagePath);
			wowzaImage = new OverlayImage(imagePath, 100, logger, envMap);
			// Calculate center Points
			String x_placement = placement.split("_")[1];
			String y_placement = placement.split("_")[0];
			if (x_placement.equalsIgnoreCase("CENTER"))
				posX = posX - (wowzaImage.GetWidth(0.5));
			if (y_placement.equalsIgnoreCase("MIDDLE"))
				posY = posY - (wowzaImage.GetHeight(0.5));

			logInfo("update OverlayImage for admodel: " + adModel.getId() + " " + adModel.getAdEventId());

			OverlayImage mainImage;

			mainImage = new OverlayImage(posX, posY, wowzaImage.GetWidth(1.0), wowzaImage.GetHeight(1.0), 100);
			mainImage.addOverlayImage(wowzaImage, 0, 0);
			StreamOverlayImageDetail mainImageDetails = new StreamOverlayImageDetail(mainImage, adModel.getAdTarget(),
					imagePath, adModel.getEventAdType(), srcWidth, srcHeight);
			logInfo("updated images for target: " + mainImageDetails.getTarget());
			targetImageMap.put(mainImageDetails.getHashMapKey(), mainImageDetails);
		}

		public void addEncoder(String name, TranscoderSessionVideoEncode sessionVideoEncode,
				TranscoderStreamDestinationVideo destinationVideo) {
			encoderInfoList.add(new EncoderInfo(name, sessionVideoEncode, destinationVideo));
		}

		@Override
		public void onBeforeScaleFrame(TranscoderSessionVideo sessionVideo, TranscoderStreamSourceVideo sourceVideo,
				long frameCount) {
			synchronized (lock) {

				int sourceHeight = sessionVideo.getDecoderHeight();
				sessionVideo.getDecoderWidth();
				for (EncoderInfo encoderInfo : encoderInfoList) {
					if (!encoderInfo.destinationVideo.isPassThrough()) {
						if (eventState == EventState.started) {
							StreamTarget key = StreamManager.getInstance().getStreamTarget(encoderInfo.encodeName);
							for (AdType adType : AdType.getavailableTypes()) {
								applyOverlay(sourceHeight, encoderInfo,
										StreamManager.getInstance().createHashMapKey(key, adType), adType);
							}
						} else if (eventState == EventState.ended) {
							for (AdType adType : AdType.getavailableTypes()) {
								logInfo("clearing all the overlays");
								encoderInfo.destinationVideo.clearOverlay(AdType.getOverlayIndex(adType));
							}
							eventState = EventState.idle;
						}
					}
				}

			}
		}

		private void applyOverlay(int sourceHeight, EncoderInfo encoderInfo, String key, AdType adType) {
			double scalingFactor;
			if (targetImageMap.containsKey(key)) {
				// Clear the previous overlay and then add the new overlay
				StreamOverlayImageDetail imageDetails = targetImageMap.get(key);
				int overlayLogoIndex = AdType.getOverlayIndex(imageDetails.getAdType());
				OverlayImage mainImage = imageDetails.getMainImage();
				logInfo("overlaying for : " + key + " with the image: " + imageDetails.getImagePath());

				TranscoderVideoOverlayFrame overlay = new TranscoderVideoOverlayFrame(mainImage.GetWidth(1),
						mainImage.GetHeight(1), mainImage.GetBuffer(1));
				if (adType == AdType.BOTTOM_BAR) {
					overlay.setDstY(srcHeight);
				} else if (adType == AdType.L_BAND) {
					overlay.setDstX(0);
					overlay.setDstY(0);
				}

				else {
					overlay.setDstX(mainImage.GetxPos(1));
					overlay.setDstY(mainImage.GetyPos(1));
				}

				targetImageMap.remove(key);
				liveTargetImageMap.put(key, key);
				// Add padding to the destination video i.e.
				// pinch

				VideoPadding padding = VideoPadding.getVideoPadding(imageDetails.getAdType());
				logDebug("Left Top Right Bottom " + padding.getLeft(1) + " : " + padding.getTop(1) + " : "
						+ padding.getRight(1) + " : " + padding.getBottom(1));
				encoderInfo.videoPadding[0] = padding.getLeft(1);
				encoderInfo.videoPadding[1] = padding.getTop(1);
				encoderInfo.videoPadding[2] = padding.getRight(1);
				encoderInfo.videoPadding[3] = padding.getBottom(1);
				encoderInfo.destinationVideo.setPadding(encoderInfo.videoPadding);
				encoderInfo.destinationVideo.addOverlay(overlayLogoIndex, overlay);
			}
		}

	}

	class StreamOverlayImageDetail {
		private StreamTarget target = StreamTarget.None;
		OverlayImage mainImage;
		private String imagePath;
		private AdType adType;
		private String key = "";
		private int srcHeight, srcWidth;

		public int getSrcHeight() {
			return srcHeight;
		}

		public void setSrcHeight(int srcHeight) {
			this.srcHeight = srcHeight;
		}

		public int getSrcWidth() {
			return srcWidth;
		}

		public void setSrcWidth(int srcWidth) {
			this.srcWidth = srcWidth;
		}

		public String getHashMapKey() {
			if (key != null && key.isEmpty()) {
				key = StreamManager.getInstance().createHashMapKey(target, adType);
			}
			return key;
		}

		public AdType getAdType() {
			return adType;
		}

		public void setAdType(AdType adType) {
			this.adType = adType;
		}

		public String getImagePath() {
			return imagePath;
		}

		public void setImagePath(String imagePath) {
			this.imagePath = imagePath;
		}

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

		public StreamOverlayImageDetail(OverlayImage image, StreamTarget target, String imagePath, AdType adType,
				int srcWidth, int srcHeight) {
			this.mainImage = image;
			this.target = target;
			this.imagePath = imagePath;
			this.adType = adType;
			this.srcWidth = srcWidth;
			this.srcHeight = srcHeight;

		}

		public StreamOverlayImageDetail(OverlayImage image, String target, String imagePath, AdType adType,
				int srcWidth, int srcHeight) {
			this(image, StreamTarget.valueOf(target), imagePath, adType, srcWidth, srcHeight);

		}

	}

}