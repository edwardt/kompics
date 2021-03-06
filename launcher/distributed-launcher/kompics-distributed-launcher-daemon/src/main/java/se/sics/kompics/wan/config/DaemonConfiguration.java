package se.sics.kompics.wan.config;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;

import org.apache.commons.cli.Option;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import se.sics.kompics.address.Address;

public class DaemonConfiguration extends MasterAddressConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(DaemonConfiguration.class);
    public final static String PROP_DAEMON_CONFIG_PROPS_FILE = "config/daemon.properties";
    public final static String PROP_DAEMON_ID = "daemon.id";
    public final static String PROP_DAEMON_RETRY_PERIOD = "daemon.retry.period";
    public final static String PROP_DAEMON_RETRY_COUNT = "daemon.retry.count";
    public final static String PROP_DAEMON_INDEXING_PERIOD = "daemon.indexing.period";
    public final int DAEMON_ID;
    public static final long DEFAULT_DAEMON_INDEXING_PERIOD = 30 * 1000;
    public static final long DEFAULT_DAEMON_RETRY_PERIOD = 30 * 1000;
    public static final int DEFAULT_DAEMON_RETRY_COUNT = 5;
    /********* Apache Commons Helper fields ********************************/
    protected Option daemonIdOption;
    protected Option daemonMasterOption;
    protected Option daemonRetryPeriodOption;
    protected Option daemonRetryCountOption;
    protected Option daemonIndexingPeriodOption;
    protected PropertiesConfiguration daemonConfig;
    protected static boolean daemonInitialized = false;

    /**
     *
     * @param args
     * @throws IOException
     */
    public DaemonConfiguration(String[] args) throws IOException, ConfigurationException {
        super(args);

        daemonInitialized = true;
        try {
            // generate DAEMON_ID using the local host address.
            // can only set DEFAULT_DAEMON_ID after local peer address is set.
            InetAddress inet = InetAddress.getByName(compositeConfig.getString(PROP_IP, DEFAULT_IP));
            compositeConfig.setProperty(PROP_PORT, DEFAULT_DAEMON_PORT);
            int p = compositeConfig.getInt(PROP_PORT, DEFAULT_MASTER_PORT);
            int id = DEFAULT_DAEMON_ID;
            Address localAddr = new Address(inet, p, id);
            DAEMON_ID = localAddr.hashCode();

        } catch (UnknownHostException e) {
            throw new ConfigurationException("Couldn't get IP address for local host: " + compositeConfig.getString(PROP_IP, DEFAULT_IP));
        }
    }

    @Override
    protected void parseAdditionalOptions(String[] args) throws IOException {
        super.parseAdditionalOptions(args);

        daemonIdOption = new Option("id", true, "Daemon id");
        daemonIdOption.setArgName(VAL_NUMBER);
        options.addOption(daemonIdOption);

        daemonRetryPeriodOption = new Option("drp", true, "Daemon retry period");
        daemonRetryPeriodOption.setArgName(VAL_PERIOD_SECS);
        options.addOption(daemonRetryPeriodOption);

        daemonRetryCountOption = new Option("drc", true, "Daemon retry count");
        daemonRetryCountOption.setArgName(VAL_NUMBER);
        options.addOption(daemonRetryCountOption);


        daemonIndexingPeriodOption = new Option("indexingPeriod", true, "Time period between Daemon indexing events");
        daemonIndexingPeriodOption.setArgName(VAL_PERIOD_SECS);
        options.addOption(daemonIndexingPeriodOption);

    }

    @Override
    protected void processAdditionalOptions() throws IOException {
        super.processAdditionalOptions();
        try {
            daemonConfig = new PropertiesConfiguration(PROP_DAEMON_CONFIG_PROPS_FILE);
            compositeConfig.addConfiguration(daemonConfig);
        } catch (ConfigurationException e) {
            logger.info("Configuration file for daemon not found, using default values: " + PROP_DAEMON_CONFIG_PROPS_FILE);
        }

        if (line.hasOption(daemonIdOption.getOpt())) {
            int daemonId = new Integer(line.getOptionValue(daemonIdOption.getOpt()));
            compositeConfig.setProperty(PROP_DAEMON_ID, daemonId);
        }
        if (line.hasOption(daemonRetryPeriodOption.getOpt())) {
            int retryPeriod = new Integer(line.getOptionValue(daemonRetryPeriodOption.getOpt()));
            compositeConfig.setProperty(PROP_DAEMON_RETRY_PERIOD, retryPeriod);
        }
        if (line.hasOption(daemonRetryCountOption.getOpt())) {
            int retryCount = new Integer(line.getOptionValue(daemonRetryCountOption.getOpt()));
            compositeConfig.setProperty(PROP_DAEMON_RETRY_COUNT, retryCount);
        }
        if (line.hasOption(daemonIndexingPeriodOption.getOpt())) {
            int period = new Integer(line.getOptionValue(daemonIndexingPeriodOption.getOpt()));
            compositeConfig.setProperty(PROP_DAEMON_INDEXING_PERIOD, period);
        }

    }

    @Override
    protected int getMonitorId() {
        daemonInitialized();
        return DEFAULT_MONITOR_ID;
    }

    @Override
    protected Address getMonitorServerAddress() {
        daemonInitialized();
        return new Address(getIp(), getPort(), getMonitorId());
    }

    public static int getDaemonId() {
        daemonInitialized();
        int daemonId = ((DaemonConfiguration) configuration).DEFAULT_DAEMON_ID;
        return configuration.compositeConfig.getInt(PROP_DAEMON_ID, daemonId);
    }

    public static long getDaemonRetryPeriod() {
        daemonInitialized();
        return configuration.compositeConfig.getLong(PROP_DAEMON_RETRY_PERIOD, DEFAULT_DAEMON_RETRY_PERIOD);
    }

    public static int getDaemonRetryCount() {
        daemonInitialized();
        return configuration.compositeConfig.getInt(PROP_DAEMON_RETRY_COUNT, DEFAULT_DAEMON_RETRY_COUNT);
    }

    public static long getDaemonIndexingPeriod() {
        daemonInitialized();
        return configuration.compositeConfig.getLong(PROP_DAEMON_INDEXING_PERIOD,
                DEFAULT_DAEMON_INDEXING_PERIOD);
    }

    static protected void daemonInitialized() {
        baseInitialized();
        if (daemonInitialized == false) {
            throw new IllegalStateException("DaemonConfiguration not initialized  before use.");
        }
    }
}
