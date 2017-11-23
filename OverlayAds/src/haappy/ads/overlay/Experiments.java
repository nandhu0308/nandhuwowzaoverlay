package haappy.ads.overlay;

import java.awt.Color;
import java.awt.Font;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

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


public class Experiments extends ModuleBase {

	String graphicName = "logo.png";
	String secondGraphName = "wowzalogo.png";
	LiveStreamTranscoder liveStreamTranscoder;
	TranscoderVideoDecoderNotifyExample transcoderVideo;
	int overlayIndex = 1;
	private IApplicationInstance appInstance = null;
	/**
	 * full path to the content directory where the graphics are kept
	 */
	private String basePath = null;
	private Object lock = new Object();

	public void onAppStart(IApplicationInstance appInstance)
	{
		String fullname = appInstance.getApplication().getName() + "/" + appInstance.getName();
		getLogger().info("onAppStart: " + fullname);
		this.appInstance = appInstance;
		String artworkPath = "${com.wowza.wms.context.VHostConfigHome}/content/" + appInstance.getApplication().getName();
		Map<String, String> envMap = new HashMap<String, String>();
		if (appInstance.getVHost() != null)
		{
			envMap.put("com.wowza.wms.context.VHost", appInstance.getVHost().getName());
			envMap.put("com.wowza.wms.context.VHostConfigHome", appInstance.getVHost().getHomePath());
		}
		envMap.put("com.wowza.wms.context.Application", appInstance.getApplication().getName());
		if (this != null)
			envMap.put("com.wowza.wms.context.ApplicationInstance", appInstance.getName());
		this.basePath =  SystemUtils.expandEnvironmentVariables(artworkPath, envMap);
		this.basePath = this.basePath.replace("\\", "/");
		if (!this.basePath.endsWith("/"))
			this.basePath = this.basePath+"/";
		this.appInstance.addLiveStreamTranscoderListener(new TranscoderCreateNotifierExample());
	
	}
	
	

	class EncoderInfo
	{
		public String encodeName;
		public TranscoderSessionVideoEncode sessionVideoEncode = null;
		public TranscoderStreamDestinationVideo destinationVideo = null;
		/**
		 * array on ints that defines number of pixels to pad the video by.  Left,Top,Right,Bottom
		 */
		public int[] videoPadding = new int[4];
		public EncoderInfo(String name, TranscoderSessionVideoEncode sessionVideoEncode, TranscoderStreamDestinationVideo destinationVideo)
		{
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
		getLogger().info("client URI "+client.getUri());
		getLogger().info("client ID "+client.getClientId());
		getLogger().info("client IP "+client.getIp());
		getLogger().info("client HOST "+client.getVHost());
		
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

	class TranscoderCreateNotifierExample implements ILiveStreamTranscoderNotify
	{
		public void onLiveStreamTranscoderCreate(ILiveStreamTranscoder liveStreamTranscoder, IMediaStream stream)
		{
			getLogger().info("ModuleTranscoderOverlayExample#TranscoderCreateNotifierExample.onLiveStreamTranscoderCreate["+appInstance.getContextStr()+"]: "+stream.getName());
			
			((LiveStreamTranscoder)liveStreamTranscoder).addActionListener(new TranscoderActionNotifierExample());
		}

		public void onLiveStreamTranscoderDestroy(ILiveStreamTranscoder liveStreamTranscoder, IMediaStream stream)
		{
			getLogger().info("Destroy: " + stream.getName());
		
		}

		public void onLiveStreamTranscoderInit(ILiveStreamTranscoder liveStreamTranscoder, IMediaStream stream)
		{
		}	
	}


	class TranscoderActionNotifierExample extends LiveStreamTranscoderActionNotifyBase
	{
		TranscoderVideoDecoderNotifyExample transcoder=null;

		public void onSessionVideoEncodeSetup(LiveStreamTranscoder liveStreamTranscoder, TranscoderSessionVideoEncode sessionVideoEncode)
		{
			getLogger().info("ModuleTranscoderOverlayExample#TranscoderActionNotifierExample.onSessionVideoEncodeSetup["+appInstance.getContextStr()+"]");
			TranscoderStream transcoderStream = liveStreamTranscoder.getTranscodingStream();
			boolean chekc = transcoderStream != null && transcoder==null;
			getLogger().info("checking if condition-"+chekc);
			if (chekc)
			{
				TranscoderSession transcoderSession = liveStreamTranscoder.getTranscodingSession();
				TranscoderSessionVideo transcoderVideoSession = transcoderSession.getSessionVideo();
				List<TranscoderStreamDestination> alltrans = transcoderStream.getDestinations();
				int w = transcoderVideoSession.getDecoderWidth();
				int h = transcoderVideoSession.getDecoderHeight();
				getLogger().info("Changed graphic path- "+graphicName);
				getLogger().info("Creating new Overlay:"+"width-"+w+", height-"+h);
				transcoder = new TranscoderVideoDecoderNotifyExample(w,h);
				transcoderVideoSession.addFrameListener(transcoder);
//				transcoderVideoSession.removeFrameListener(transcoder);

				//apply an overlay to all outputs
				for(TranscoderStreamDestination destination:alltrans)
				{
					//TranscoderSessionVideoEncode sessionVideoEncode = transcoderVideoSession.getEncode(destination.getName());
					TranscoderStreamDestinationVideo videoDestination = destination.getVideo();
					System.out.println("sessionVideoEncode:"+sessionVideoEncode);
					System.out.println("videoDestination:"+videoDestination);
					if (sessionVideoEncode != null && videoDestination !=null)
					{
						transcoder.addEncoder(destination.getName(),sessionVideoEncode,videoDestination);
					} 
				}

			}
			return;
		}
		
	}
	
	class TranscoderVideoDecoderNotifyExample extends TranscoderVideoDecoderNotifyBase
	{
		private volatile OverlayImage mainImage=null;private volatile OverlayImage wowzaImage=null;
		private OverlayImage wowzaText = null;
		private OverlayImage wowzaTextShadow = null;
		List<EncoderInfo> encoderInfoList = new ArrayList<EncoderInfo>();
		AnimationEvents videoBottomPadding = new AnimationEvents();
		private boolean imageTime = false;
		private OverlayImage secondImage = null;
		private OverlayImage holderImage = null;
		int srcWidth;
		int childPosition;

		public TranscoderVideoDecoderNotifyExample (int srcWidth, int srcHeight)
		{
			int lowerThirdHeight = 70;
			this.srcWidth = srcWidth;
			//create a transparent container for the bottom third of the screen.
			mainImage = new OverlayImage(0,srcHeight-lowerThirdHeight,srcWidth,lowerThirdHeight,100);
			//Create the Wowza logo image
			getLogger().info("TranscoderVideoDecoderNotify Called");
			wowzaImage = new OverlayImage(basePath+graphicName,100);
			secondImage = new OverlayImage(basePath+secondGraphName,100);
			getLogger().info("Image path "+basePath+graphicName);
			mainImage.addOverlayImage(wowzaImage,srcWidth-wowzaImage.GetWidth(1.0),0);
			//Add Text with a drop shadow
			wowzaText = new OverlayImage("Haappy", 12, "SansSerif", Font.BOLD, Color.white, 66,15,100);
			wowzaTextShadow = new OverlayImage("Haappy", 12, "SansSerif", Font.BOLD, Color.darkGray, 66,15,100);
			mainImage.addOverlayImage(wowzaText, wowzaImage.GetxPos(1.0)+12, 54);
			wowzaText.addOverlayImage(wowzaTextShadow, 1, 1);

			//Fade the Logo and text independently
			//wowzaImage.addFadingStep(100,0,25);
			//wowzaImage.addFadingStep(0,100,25);
			//wowzaText.addFadingStep(0,100,25);
			//wowzaText.addFadingStep(100,0,25);

			//do nothing for a bit
			mainImage.addFadingStep(50);
			wowzaImage.addImageStep(50);
			wowzaText.addMovementStep(50);

			//Fade the logo and text
			mainImage.addFadingStep(0,100,100);


			//Rotate the image while fading
			wowzaImage.addImageStep(1,37,25);
			wowzaImage.addImageStep(1,37,25);
			wowzaImage.addImageStep(1,37,25);
			wowzaImage.addImageStep(1,37,25);
			//Animate the text off screen to original location
			wowzaText.addMovementStep(-75, 0, wowzaText.GetxPos(1.0), 54, 100);

			//hold everything for a bit
			mainImage.addFadingStep(50);
			wowzaImage.addImageStep(50);
			wowzaText.addMovementStep(50);

			//Fade out
			mainImage.addFadingStep(100,0,50);
			wowzaImage.addImageStep(50);
			wowzaText.addMovementStep(50);

			//Pinch back video
			videoBottomPadding.addAnimationStep(0, 60, 50);
			videoBottomPadding.addAnimationStep(100);
			
			
			secondImage.addImageStep(1,37,25);
			secondImage.addImageStep(1,37,25);
			secondImage.addImageStep(1,37,25);
			secondImage.addImageStep(1,37,25);
			secondImage.addImageStep(50);
			secondImage.addImageStep(50);
			secondImage.addImageStep(50);

        	
			//unpinch the video
			videoBottomPadding.addAnimationStep(60, 0, 50);
			mainImage.addFadingStep(50);
			wowzaImage.addImageStep(50);
			wowzaText.addMovementStep(50);
			videoBottomPadding.addAnimationStep(100);
			holderImage = wowzaImage;
			startImageTimer();
		}	
		
		private void startImageTimer(){
			Timer timer = new Timer();
	        timer.schedule(new TimerTask() {

	            @Override
	            public void run() {
	         
	    			imageTime = true;
	            }
	        }, 30000,30000);
	      
		}
		

		public void addEncoder(String name, TranscoderSessionVideoEncode sessionVideoEncode, TranscoderStreamDestinationVideo destinationVideo)
		{
			encoderInfoList.add(new EncoderInfo(name,sessionVideoEncode,destinationVideo));
		}

		public void onBeforeScaleFrame(TranscoderSessionVideo sessionVideo, TranscoderStreamSourceVideo sourceVideo, long frameCount)
		{
			boolean encodeSource=false;
			boolean showTime=true;
			double scalingFactor=1.0;
			synchronized(lock) 
			{
			
				if (mainImage != null)
				{
					
					if(imageTime){
						imageTime=false;
						mainImage.removeChild(holderImage);
						if(childPosition==0){
							holderImage = wowzaImage;
							childPosition = 1;
						} else { 
							holderImage = secondImage;
							childPosition = 0;
						}			
						mainImage.addOverlayImage(holderImage,srcWidth-holderImage.GetWidth(1.0),0);
					}
					
					//does not need to be done for a static graphic, but left here to build on (transparency/animation)
					videoBottomPadding.step();
					mainImage.step();
					int sourceHeight = sessionVideo.getDecoderHeight();
					int sourceWidth = sessionVideo.getDecoderWidth();
					getLogger().info("Showtime = "+showTime);
					if(showTime)
					{
						Date dNow = new Date( );
						SimpleDateFormat ft = new SimpleDateFormat("hh:mm:ss");
						getLogger().info("Time Text-"+ft.format(dNow));
						wowzaText.SetText(ft.format(dNow));
						wowzaTextShadow.SetText(ft.format(dNow));
					}
					if(encodeSource)
					{
						//put the image onto the source
						scalingFactor = 1.0;
						TranscoderVideoOverlayFrame overlay = new TranscoderVideoOverlayFrame(mainImage.GetWidth(scalingFactor),
								mainImage.GetHeight(scalingFactor), mainImage.GetBuffer(scalingFactor));
						overlay.setDstX(mainImage.GetxPos(scalingFactor));
						overlay.setDstY(mainImage.GetyPos(scalingFactor));
						sourceVideo.addOverlay(overlayIndex, overlay);
					} 
					else	
					{
						///put the image onto each destination but scaled to fit
						for(EncoderInfo encoderInfo: encoderInfoList)
						{
							if (!encoderInfo.destinationVideo.isPassThrough())
							{
								int destinationHeight = encoderInfo.destinationVideo.getFrameSizeHeight();
								scalingFactor = (double)destinationHeight/(double)sourceHeight;
								TranscoderVideoOverlayFrame overlay = new TranscoderVideoOverlayFrame(mainImage.GetWidth(scalingFactor),
										mainImage.GetHeight(scalingFactor), mainImage.GetBuffer(scalingFactor));
								overlay.setDstX(mainImage.GetxPos(scalingFactor));
								overlay.setDstY(mainImage.GetyPos(scalingFactor));
								encoderInfo.destinationVideo.addOverlay(overlayIndex,	overlay);
								//Add padding to the destination video i.e. pinch
								encoderInfo.videoPadding[0] = 0; // left
								encoderInfo.videoPadding[1] = 0; // top
								encoderInfo.videoPadding[2] = 0; // right
								encoderInfo.videoPadding[3] = 0;//(int)(((double)videoBottomPadding.getStepValue())*scalingFactor); // bottom
								encoderInfo.destinationVideo.setPadding(encoderInfo.videoPadding);
							}
						}
					}
				} 
			}
			return; 
		}
	}
	
/*//			calculatedWidth = Math.round(getOverlayPositionX(srcWidth, thirdPosition));
//			int calculatedHeight = Math.round(getOverlayPositionY(srcHeight, thirdPosition));
			this.srcWidth = srcWidth;
			wowzaImage = new OverlayImage(basePath+graphicName,100);
//			if (calculatedWidth == 0)
//				calculatedWidth = wowzaImage.GetWidth(1.0);
//			else if (calculatedWidth != srcWidth) {
//				calculatedWidth += wowzaImage.GetWidth(0.5);
//			}
//			if (calculatedHeight != srcHeight && calculatedHeight != 0) {
//				calculatedHeight += wowzaImage.GetHeight(0.5);
//			}
			
			//Create the Wowza logo image
			getLogger().info("screen width="+srcWidth+" height = "+srcHeight);
			secondImage = new OverlayImage(basePath+lBandImagePath,100);
			holderImage = new OverlayImage(basePath+lBandBottomImagePath, 100);
			getLogger().info("Image path "+basePath+graphicName);
			//create a transparent container for the bottom third of the screen.
			mainImage = new OverlayImage(0,0,srcWidth,srcHeight,100);
//			mainImage.addOverlayImage(secondImage,0,0);
			mainImage.addOverlayImage(holderImage, 0,srcHeight-holderImage.GetHeight(1.0));
			//Add Text with a drop shadow
//			wowzaText = new OverlayImage("Haappy", 12, "SansSerif", Font.BOLD, Color.white, 66,15,100);
//			wowzaTextShadow = new OverlayImage("Haappy", 12, "SansSerif", Font.BOLD, Color.darkGray, 66,15,100);
//			mainImage.addOverlayImage(wowzaText, wowzaImage.GetxPos(1.0)+12, 54);
//			wowzaText.addOverlayImage(wowzaTextShadow, 1, 1);

			//Fade the Logo and text independently
			//wowzaImage.addFadingStep(100,0,25);
			//wowzaImage.addFadingStep(0,100,25);
			//wowzaText.addFadingStep(0,100,25);
			//wowzaText.addFadingStep(100,0,25);

			//do nothing for a bit
			mainImage.addFadingStep(50);
			wowzaImage.addImageStep(50);
			secondImage.addImageStep(50);
			holderImage.addImageStep(50);
//			wowzaText.addMovementStep(50);

			//Fade the logo and text
			mainImage.addFadingStep(0,100,100);


			//Rotate the image while fading
			wowzaImage.addImageStep(1,37,25);
			wowzaImage.addImageStep(1,37,25);
			wowzaImage.addImageStep(1,37,25);
			wowzaImage.addImageStep(1,37,25);
			//Animate the text off screen to original location
//			wowzaText.addMovementStep(-75, 0, wowzaText.GetxPos(1.0), 54, 100);

			//hold everything for a bit
			mainImage.addFadingStep(50);
			wowzaImage.addImageStep(50);
			secondImage.addImageStep(50);
			holderImage.addImageStep(50);
//			wowzaText.addMovementStep(50);

			//Fade out
			mainImage.addFadingStep(100,0,50);
			wowzaImage.addImageStep(50);
			secondImage.addImageStep(50);
			wowzaImage.addFadingStep(50);
			holderImage.addFadingStep(50);
			secondImage.addFadingStep(50); 
//			wowzaText.addMovementStep(50);

			//Pinch back video
			videoBottomPadding.addAnimationStep(0, holderImage.GetHeight(1.0), 50);
			videoBottomPadding.addAnimationStep(200);
			videoLeftPadding.addAnimationStep(0,secondImage.GetWidth(1.0),50);
			videoLeftPadding.addAnimationStep(200);

			//unpinch the video
			videoBottomPadding.addAnimationStep(holderImage.GetHeight(1.0), 0, 50);
			videoLeftPadding.addAnimationStep(secondImage.GetWidth(1.0),0,50);
			mainImage.addFadingStep(150);
			wowzaImage.addImageStep(150);
			secondImage.addImageStep(150);
			holderImage.addImageStep(150);
//			wowzaText.addMovementStep(50);
			videoBottomPadding.addAnimationStep(100);
			videoLeftPadding.addAnimationStep(100);
***********************************************************************************************
*full screen overlay setup-
*getLogger().info("screen width="+srcWidth+" height = "+srcHeight);
			secondImage = new OverlayImage(basePath+secondGraphName,100);
			getLogger().info("getting bottom image");
			holderImage = new OverlayImage(basePath+bottomImageName, 100);
			getLogger().info("bottom image created");
			//create a transparent container for the bottom third of the screen.
			mainImage =  new OverlayImage(0,0,srcWidth,srcHeight,100);
//			mainImage.addOverlayImage(secondImage,0,0);
			mainImage.addOverlayImage(holderImage, srcWidth-holderImage.GetWidth(1.0),0);
			
**************************************************************************************************
*L band by separate images
*Create the Wowza logo image
			getLogger().info("screen width="+srcWidth+" height = "+srcHeight);
			secondImage = new OverlayImage(basePath+leftIamgeName,100);
			overlayScreenWidth = secondImage.GetWidth(1.0);
			getLogger().info("getting bottom image");
			holderImage = new OverlayImage(basePath+bottomImageName, 100);
			overlayScreenHeight = holderImage.GetHeight(1.0);
			getLogger().info("bottom image created");
			//create a transparent container for the bottom third of the screen.
			mainImage =  new OverlayImage(0,srcHeight,srcWidth,srcHeight,100);
			mainImage.addOverlayImage(secondImage,0,0);
			mainImage.addOverlayImage(holderImage, srcWidth-holderImage.GetWidth(1.0),srcHeight-overlayScreenHeight);
			
********************************************************************************************************
*show only bottom image
*		getLogger().info("screen width="+srcWidth+" height = "+srcHeight);
			secondImage = new OverlayImage(basePath+secondGraphName,100);
			getLogger().info("getting bottom image");
			holderImage = new OverlayImage(basePath+bottomImageName, 100);
			overlayScreenHeight = holderImage.GetHeight(1.0);
			getLogger().info("bottom image created");
			//create a transparent container for the bottom third of the screen.
			mainImage =  new OverlayImage(0,srcHeight-overlayScreenHeight,srcWidth,overlayScreenHeight,100);
//			mainImage.addOverlayImage(secondImage,0,0);
			mainImage.addOverlayImage(holderImage, srcWidth-holderImage.GetWidth(1.0),0);
			
*********************************************************************************************************
*	getLogger().info("screen width="+srcWidth+" height = "+srcHeight);
			secondImage = new OverlayImage(basePath+leftIamgeName,100);
			overlayWidth = secondImage.GetWidth(1.0);
			getLogger().info("getting bottom image");
			holderImage = new OverlayImage(basePath+bottomImageName, 100);
			overlayScreenHeight = holderImage.GetHeight(1.0);
			getLogger().info("bottom image created");
			//create a transparent container for the bottom third of the screen.
			mainImage =  new OverlayImage(0,srcHeight,srcWidth,srcHeight,100);
			mainImage.addOverlayImage(secondImage,0,0);
			mainImage.addOverlayImage(holderImage, srcWidth-holderImage.GetWidth(1.0),srcHeight-holderImage.GetHeight(1.0));
			
**************************************************************************************************************
*show bottom image with text at the bottom
*getLogger().info("screen width="+srcWidth+" height = "+srcHeight);
			secondImage = new OverlayImage(basePath+secondGraphName,100);
			getLogger().info("getting bottom image");
			holderImage = new OverlayImage(basePath+bottomImageName, 100);
			getLogger().info("bottom image created");
		
//			mainImage.addOverlayImage(secondImage,0,0);
			
			//Add Text with a drop shadow
			wowzaText = new OverlayImage(overlayText, 12, "SansSerif", Font.BOLD, Color.white, srcWidth,15,100);
			wowzaTextShadow = new OverlayImage(overlayText, 12, "SansSerif", Font.BOLD, Color.darkGray, srcWidth,15,100);
			//create a transparent container for the bottom third of the screen.
			overlayScreenHeight = holderImage.GetHeight(1.0)+wowzaText.GetHeight(1.0);
			mainImage =  new OverlayImage(0,srcHeight-overlayScreenHeight,srcWidth,overlayScreenHeight,100);
			mainImage.addOverlayImage(holderImage, srcWidth-holderImage.GetWidth(1.0),0);
			mainImage.addOverlayImage(wowzaText, wowzaImage.GetxPos(1.0)+12, overlayScreenHeight-wowzaText.GetHeight(1.0));
			wowzaText.addOverlayImage(wowzaTextShadow, 1, 1);
			
****************************************************************************************************************
*Bottom band setup
*secondImage = new OverlayImage(basePath + secondGraphName, 100);
 holderImage = new OverlayImage(basePath + bottomImageName, 100);
*wowzaText = new OverlayImage(overlayText, 12, "SansSerif", Font.BOLD, Color.white, srcWidth, 15, 100);
			wowzaTextShadow = new OverlayImage(overlayText, 12, "SansSerif", Font.BOLD, Color.darkGray, srcWidth, 15,
					100);
			// create a transparent container for the bottom third of the
			// screen.
			overlayScreenHeight = holderImage.GetHeight(1.0) + wowzaText.GetHeight(1.0);
			mainImage = new OverlayImage(0, srcHeight - overlayScreenHeight, srcWidth, overlayScreenHeight, 100);
			mainImage.addOverlayImage(holderImage, srcWidth - holderImage.GetWidth(1.0), 0);
			mainImage.addOverlayImage(wowzaText, wowzaImage.GetxPos(1.0) + 12,
					overlayScreenHeight - wowzaText.GetHeight(1.0));
			wowzaText.addOverlayImage(wowzaTextShadow, 1, 1);

			// do nothing for a bit
			mainImage.addFadingStep(50);
			wowzaImage.addImageStep(50);
			secondImage.addImageStep(50);
			holderImage.addImageStep(50);
			wowzaText.addImageStep(50);

			// hold everything for a bit
			mainImage.addFadingStep(50);
			wowzaImage.addImageStep(50);
			secondImage.addImageStep(50);
			holderImage.addImageStep(50);
			wowzaText.addImageStep(50);

			

			// Pinch back video
			videoBottomPadding.addAnimationStep(0, overlayScreenHeight, 50);
			videoBottomPadding.addAnimationStep(200);
			*
			*
			*/

}
