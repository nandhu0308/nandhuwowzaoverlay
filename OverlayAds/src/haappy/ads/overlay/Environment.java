package haappy.ads.overlay;

public final class Environment {

	public static boolean isDebugMode()
	{
		String mode = System.getenv("WOWZA_DEVELOPER_MODE");
		return mode != null && mode.equalsIgnoreCase("DEBUG");
	}

}
