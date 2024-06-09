package name.modid;

import net.fabricmc.api.ModInitializer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Send_and_receive_msgs implements ModInitializer {
	public static final String MOD_ID = "send_and_receive";
    public static final Logger LOGGER = LoggerFactory.getLogger("send_and_receive_msgs");

	@Override
	public void onInitialize() {

		LOGGER.info("Hello Fabric world!");
	}
}