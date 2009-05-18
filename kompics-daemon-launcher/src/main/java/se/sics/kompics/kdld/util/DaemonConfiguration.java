package se.sics.kompics.kdld.util;

import java.io.IOException;
import java.net.InetAddress;

import org.apache.commons.cli.Option;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.apache.commons.configuration.reloading.FileChangedReloadingStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import se.sics.kompics.address.Address;

public class DaemonConfiguration extends Configuration {

	private static final Logger logger = LoggerFactory.getLogger(DaemonConfiguration.class);
	
	public final static String PROP_DAEMON_CONFIG_PROPS_FILE = "config/daemon.properties";
	public final static String PROP_DAEMON_ID  = "daemon.id";
	public final static String PROP_DAEMON_MASTER_ADDR  = "daemon.master.address";
	public final static String PROP_DAEMON_RETRY_PERIOD  = "daemon.retry.period";
	public final static String PROP_DAEMON_RETRY_COUNT  = "daemon.retry.count";
	public final static String PROP_DAEMON_INDEXING_PERIOD  = "daemon.indexing.period";

	protected static final int DEFAULT_DAEMON_ID = -1;
	protected static final long DEFAULT_DAEMON_INDEXING_PERIOD = 10*1000;
	protected static final String DEFAULT_DAEMON_MASTER_ADDRESS = "lucan.sics.se:2323:1";
	
	
	/********************************************************/
	/********* Helper fields ********************************/
	/********************************************************/
	protected Option daemonIdOption;
	protected Option daemonMasterOption;
	protected Option daemonRetryPeriodOption;	
	protected Option daemonRetryCountOption;

	protected PropertiesConfiguration daemonConfig;

	/**
	 * 
	 * @param args
	 * @throws IOException
	 */
	public DaemonConfiguration(String[] args) throws IOException, ConfigurationException {
		super(args);

	}

	@Override
	protected void parseAdditionalOptions(String[] args) throws IOException {
		daemonMasterOption = new Option("masteraddr", true, "Address of Master Server");
		daemonMasterOption.setArgName("masteraddr");
		options.addOption(daemonMasterOption);
		
		daemonIdOption = new Option("id", true, "Daemon id");
		daemonIdOption.setArgName("id");
		options.addOption(daemonIdOption);

		
		daemonRetryPeriodOption = new Option("drp", true, "Daemon retry period");
		daemonRetryPeriodOption.setArgName("drp");
		options.addOption(daemonRetryPeriodOption);

		daemonRetryCountOption = new Option("drc", true, "Daemon retry count");
		daemonRetryCountOption.setArgName("drc");
		options.addOption(daemonRetryCountOption);

	}

	@Override
	protected void processAdditionalOptions() throws IOException {

		try {
			daemonConfig = new PropertiesConfiguration(PROP_DAEMON_CONFIG_PROPS_FILE);
//			daemonConfig.setReloadingStrategy(new FileChangedReloadingStrategy());
			compositeConfig.addConfiguration(daemonConfig);
		}
		catch (ConfigurationException e)
		{
			logger.warn("Configuration file for cyclon not found, using default values: " 
					+ PROP_DAEMON_CONFIG_PROPS_FILE);
		}

		if (line.hasOption(daemonMasterOption.getOpt()))
		{
			String masterAddr = new String(line.getOptionValue(daemonMasterOption.getOpt()));
			configuration.compositeConfig.setProperty(PROP_DAEMON_MASTER_ADDR, masterAddr);
		}
		if (line.hasOption(daemonIdOption.getOpt()))
		{
			int daemonId = new Integer(line.getOptionValue(daemonIdOption.getOpt()));
			configuration.compositeConfig.setProperty(PROP_DAEMON_ID, daemonId);
		}
		if (line.hasOption(daemonRetryPeriodOption.getOpt()))
		{
			int retryPeriod = new Integer(line.getOptionValue(daemonRetryPeriodOption.getOpt()));
			configuration.compositeConfig.setProperty(PROP_DAEMON_RETRY_PERIOD, retryPeriod);
		}
		if (line.hasOption(daemonRetryCountOption.getOpt()))
		{
			int retryCount = new Integer(line.getOptionValue(daemonRetryCountOption.getOpt()));
			configuration.compositeConfig.setProperty(PROP_DAEMON_RETRY_COUNT, retryCount);
		}

	}

	@Override
	protected int getMonitorId() {
		testInitialized();
		return DEFAULT_MONITOR_ID;
	}

	@Override
	protected Address getMonitorServerAddress() {
		testInitialized();
		return new Address(getIp(), getPort(), getMonitorId());
	}

	public static int getDaemonId() {
		testInitialized();
		return configuration.compositeConfig.getInt(PROP_DAEMON_ID, DEFAULT_DAEMON_ID);
	}
	
	public static long getDaemonRetryPeriod() {
		testInitialized();
		return configuration.compositeConfig.getLong(PROP_DAEMON_RETRY_PERIOD, DEFAULT_RETRY_PERIOD);
	}
	
	public static int getDaemonRetryCount() {
		testInitialized();
		return configuration.compositeConfig.getInt(PROP_DAEMON_RETRY_COUNT, DEFAULT_RETRY_COUNT);
	}
	
	public static long getDaemonIndexingPeriod() {
		testInitialized();
		return configuration.compositeConfig.getLong(PROP_DAEMON_INDEXING_PERIOD, 
				DEFAULT_DAEMON_INDEXING_PERIOD);
	}
	
	/**
	 * Get address of master server.
	 * @return address of Master or null
	 */
	public static Address getMasterAddress() {
		testInitialized();

		String addr = configuration.compositeConfig.getString(PROP_DAEMON_MASTER_ADDR, 
				DEFAULT_DAEMON_MASTER_ADDRESS);
		return HostsParser.parseHost(addr);
	}

}
