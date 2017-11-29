package haappy.ads.videoadbreak;

import com.wowza.wms.logging.WMSLoggerFactory;
import com.wowza.wms.stream.publish.IPublishingProvider;
import com.wowza.wms.stream.publish.Publisher;
import com.wowza.wms.stream.publish.PublishingProviderLive;
import com.wowza.wms.stream.publish.PublishingProviderMediaReader;
import com.wowza.wms.vhost.IVHost;
import com.wowza.wms.vhost.VHost;
import com.wowza.wms.vhost.VHostSingleton;

public class ServerPublisherWorker extends Thread {
	private long sleepTime = 75;
	private boolean running = true;
	private Object lock = new Object();

	private String applicationName = "live";
	private String vodStreamName = "mp4:sample.mp4";
	private String liveStreamName = "youtube_source";
	private String publishStreamName = "publishstream";
	private int cycleTime = 10000;

	public synchronized void quit() {
		synchronized (lock) {
			running = false;
		}
	}

	public void run() {
		WMSLoggerFactory.getLogger(ServerPublisherWorker.class).info("ServerPublisherWorker.run: START");

		long startTime = System.currentTimeMillis();
		long playStartTime = startTime;

		try {
			IVHost vhost = VHostSingleton.getInstance(VHost.VHOST_DEFAULT);
			Publisher publisher = Publisher.createInstance(vhost, applicationName);

			publisher.publish(publishStreamName);
			WMSLoggerFactory.getLogger(ServerPublisherWorker.class).info("HOST NAME:"+ vhost.getName());
			long nextSwitch = playStartTime + cycleTime;
			long nextType = 0;
			IPublishingProvider provider = new PublishingProviderMediaReader(publisher, playStartTime, vodStreamName);
			// provider.seek(20000);
			provider.setRealTimeStartTime(startTime);

			WMSLoggerFactory.getLogger(ServerPublisherWorker.class)
					.info("ServerPublisherWorker.run: Start with vod stream: " + vodStreamName);

			while (true) {
				boolean moreInFile = provider != null ? provider.play(publisher) : false;

				long currentTime = System.currentTimeMillis();
				if (!moreInFile || currentTime > nextSwitch) {
					if (provider != null)
						provider.close();
					provider = null;

					if ((nextType % 2) == 0) {
						provider = new PublishingProviderLive(publisher, publisher.getMaxTimecode(), liveStreamName);
						// ((PublishingProviderLive)provider).setStartOnPreviousKeyFrame(false);
						provider.setRealTimeStartTime(currentTime);

						WMSLoggerFactory.getLogger(ServerPublisherWorker.class)
								.info("ServerPublisherWorker.run: Switch to live stream: " + liveStreamName);
					} else {
						provider = new PublishingProviderMediaReader(publisher, publisher.getMaxTimecode(),
								vodStreamName);
						// provider.seek(20000);
						provider.setRealTimeStartTime(currentTime);

						WMSLoggerFactory.getLogger(ServerPublisherWorker.class)
								.info("ServerPublisherWorker.run: Switch to vod stream: " + vodStreamName);
					}

					nextSwitch = currentTime + cycleTime;
					nextType++;

					if (nextType == 100)
						break;
				} else
					sleep(sleepTime);

				synchronized (lock) {
					if (!running)
						break;
				}
			}

			provider.close();

			publisher.publish(null);

			synchronized (lock) {
				running = false;
			}
		} catch (Exception e) {
			WMSLoggerFactory.getLogger(ServerPublisherWorker.class).error("ServerPublisherWorker.run: " + e.toString());
			e.printStackTrace();
		}

		WMSLoggerFactory.getLogger(ServerPublisherWorker.class).info("ServerPublisherWorker.run: STOP");
	}
}
