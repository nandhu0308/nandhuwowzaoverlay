package haappy.ads.overlay;

import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

public final class CalendarHelper {

	public static Calendar getCalendarForIndianTimeZone() {
		TimeZone indianTimeZone = TimeZone.getTimeZone("Asia/Kolkata");
		if (indianTimeZone == null)
			indianTimeZone = TimeZone.getTimeZone("Asia/Calcutta");
		return Calendar.getInstance(indianTimeZone);
	}

	public static Date getEventEndTime(EventModel eventModel, Calendar calendar) {
		String[] split = eventModel.getStartTime().split(":");
		calendar.set(Calendar.HOUR_OF_DAY, Integer.parseInt(split[0].trim()));
		calendar.set(Calendar.MINUTE, Integer.parseInt(split[1].trim()));
		calendar.add(Calendar.HOUR, eventModel.getDuration()); // get the end time
		return calendar.getTime();
	}

	public static long getEventEndTimeForTimerSchedule(EventModel eventModel) {
		Calendar calendar = getCalendarForIndianTimeZone();
		Date currentTime = calendar.getTime();
		Date endTime = getEventEndTime(eventModel, calendar);
		return endTime.getTime() - currentTime.getTime();
	}

}
