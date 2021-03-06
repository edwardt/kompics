package se.sics.kompics.wan.plab;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Vector;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.apache.ws.commons.util.NamespaceContextImpl;
import org.apache.xmlrpc.XmlRpcException;
import org.apache.xmlrpc.client.XmlRpcClient;
import org.apache.xmlrpc.client.XmlRpcClientConfigImpl;
import org.apache.xmlrpc.common.TypeFactoryImpl;
import org.apache.xmlrpc.common.XmlRpcController;
import org.apache.xmlrpc.common.XmlRpcStreamConfig;
import org.apache.xmlrpc.parser.NullParser;
import org.apache.xmlrpc.parser.TypeParser;
import org.apache.xmlrpc.serializer.NullSerializer;
import org.apache.xmlrpc.serializer.TypeSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;

import se.sics.kompics.ComponentDefinition;
import se.sics.kompics.Handler;
import se.sics.kompics.Negative;
import se.sics.kompics.wan.config.PlanetLabConfiguration;
import se.sics.kompics.wan.hosts.HostsPort;
import se.sics.kompics.wan.hosts.events.GetNodesRequest;
import se.sics.kompics.wan.hosts.events.GetNodesResponse;
import se.sics.kompics.wan.plab.events.AddHostsToSliceRequest;
import se.sics.kompics.wan.plab.events.AddHostsToSliceResponse;
import se.sics.kompics.wan.plab.events.GetHostsInSliceRequest;
import se.sics.kompics.wan.plab.events.GetHostsInSliceResponse;
import se.sics.kompics.wan.plab.events.GetHostsNotInSliceRequest;
import se.sics.kompics.wan.plab.events.GetHostsNotInSliceResponse;
import se.sics.kompics.wan.plab.events.GetNodesWithCoMonStatsRequest;
import se.sics.kompics.wan.plab.events.GetNodesWithCoMonStatsResponse;
import se.sics.kompics.wan.plab.events.GetPLabNodesRequest;
import se.sics.kompics.wan.plab.events.GetPLabNodesResponse;
import se.sics.kompics.wan.plab.events.GetProgressRequest;
import se.sics.kompics.wan.plab.events.GetProgressResponse;
import se.sics.kompics.wan.plab.events.PLabInit;
import se.sics.kompics.wan.plab.events.QueryPLabSitesRequest;
import se.sics.kompics.wan.plab.events.QueryPLabSitesResponse;
import se.sics.kompics.wan.plab.events.RankHostsUsingCoMonRequest;
import se.sics.kompics.wan.plab.events.RankHostsUsingCoMonResponse;
import se.sics.kompics.wan.plab.events.UpdateCoMonStats;
import se.sics.kompics.wan.plab.events.UpdateHostsAndSites;
import se.sics.kompics.wan.ssh.Host;

/**
 * The PLabComponent is used to access
 * <ul>
 * <li>the planetlab API and a cache of that data stored in a local DB.</li>
 * <li>CoMon statistics for planetlab hosts. </li>
 * </ul>
 * <br/>
 * (1) The planetlab API consists of operations to get the set of nodes in
 *     planetlab, add nodes to your slice, get the nodes associated with your slice, 
 *     get the nodes not associated with your slice, get the boot state of nodes,
 *     etc. 
 *     For performance, a local cache of the planetlab hosts associated with your
 *     slice are stored in a local DB. These are loaded when the component is
 *     initialized. 
 * <br/>
 * (2) CoMon are statistics about the runtime state of planetlab
 *     nodes. They include information such as response time for hosts,
 *     number of active slices on the host, boot state of the host, 
 *     CPU activity on the host, etc.
 * 
 * @author jdowling
 *
 */
public class PLabComponent extends ComponentDefinition {

	private Negative<PLabPort> pLabPort = negative(PLabPort.class);

	private Negative<HostsPort> hostsPort = negative(HostsPort.class);

	
//	private Component dns;

	private final Logger logger = LoggerFactory.getLogger(PLabComponent.class);

	public final static double MAX_PLC_CACHE_AGE = 24;

	private boolean retryingWithCertIgnore = false;

	private PLabService pLabService;

	private PlanetLabCredentials cred;

	private PLabStore store;

	private volatile double progress = 0.0;

	private Set<PLabHost> readyHosts = new HashSet<PLabHost>();

	private XmlRpcClientConfigImpl config = new XmlRpcClientConfigImpl();

	private XmlRpcClient client = new XmlRpcClient();

	public PLabComponent() {

//		dns = create(DnsResolverComponent.class);

		client.setTypeFactory(new MyTypeFactory(client));

		client.setConfig(config);

		subscribe(handleGetNodesInSliceRequest, pLabPort);
		subscribe(handleGetNodesNotInSliceRequest, pLabPort);
		subscribe(handleQueryPLabSitesRequest, pLabPort);
		subscribe(handleAddHostsToSliceRequest, pLabPort);

		subscribe(handleUpdateCoMonStats, pLabPort);
		subscribe(handleUpdateHostsAndSites, pLabPort);
		subscribe(handleGetProgressRequest, pLabPort);
		subscribe(handleRankHostsUsingCoMonRequest, pLabPort);
		subscribe(handleGetPLabNodesRequest, pLabPort);

		subscribe(handleGetNodesRequest, hostsPort);

		
//		subscribe(handleDnsResolverResponse, dns
//				.getPositive(DnsResolverPort.class));

		subscribe(handleInit, control);
	}

	public Handler<PLabInit> handleInit = new Handler<PLabInit>() {
		public void handle(PLabInit event) {

			try {
				config.setServerURL(new URL(event.getXmlRpcServerUrl()));
			} catch (MalformedURLException e) {
				System.err.println("There was an error when trying to connect to: "
						+ event.getXmlRpcServerUrl());
				e.printStackTrace();
				throw new IllegalArgumentException(e);
			}
			config.setEncoding("iso-8859-1");
			config.setEnabledForExtensions(true);

			
			cred = event.getCred();

			pLabService = (PLabService) PlanetLabConfiguration.getCtx()
					.getBean("PLabService");

			long startTime = System.currentTimeMillis();
			System.out.println("Loading hosts from filesystem");

			store = pLabService.load(cred.getSlice(), cred.getUsername());

			long time;
			if (store != null) {

				recoveryUpdate();

				updatePLabHostAttrs();

				remoteUpdateCoMonStats(false);

				// sort hosts after response-time, then number of active slices
				// Collections.sort(hosts, new
				// CoMonStatsComparator(CoMonStats.RESPTIME,
				// CoMonStats.LIVESLICES));

				System.out
						.println("Number of running hosts for this slice are: "
								+ readyHosts.size());
				for (PLabHost h : readyHosts) {
					System.out.println(h.toString());
				}

				time = System.currentTimeMillis() - startTime;
				System.out.println("Loading hosts done: " + time + "ms");
			} else {

				Set<Host> hosts = PlanetLabConfiguration.getHosts();
				for (Host h : hosts) {
					PLabHost plH = new PLabHost(h);
					readyHosts.add(plH);
				}
				if (hosts == null) { // no hosts supplied on command-line, then go to planetlab
					cleanRemoteUpdate();
				}
			}

		}
	};

	private void recoveryUpdate() {
		this.readyHosts.addAll(store.getRunningHostsForThisSlice());
		// Collections.sort(hosts,
		// new CoMonStatsComparator(CoMonStats.RESPTIME,
		// CoMonStats.LIVESLICES));

		if (this.readyHosts.size() <= 1) {
			System.out
					.println("Under 2 hosts found locally, contacting planetlab.org for a list of hosts");
			Set<PLabHost> setHosts = updateHosts(System.currentTimeMillis());
			store.setHosts(setHosts);
			logger.info("Number of hosts to save is: {}", setHosts.size());
			pLabService.saveOrUpdate(store);
			this.readyHosts = this.store.getRunningHostsForThisSlice();
			logger.info("Number of hosts now ready is: {}", this.readyHosts
					.size());
		}

	}

	
	private Handler<GetNodesWithCoMonStatsRequest> handleGetNodesWithCoMonStatsRequest = new Handler<GetNodesWithCoMonStatsRequest>() {
		public void handle(GetNodesWithCoMonStatsRequest event) {

			Set<PLabHost> hosts = getAllNodesPL();
			
			CoMonUpdater plCoMon = new CoMonUpdater(hosts);
			Thread t = new Thread(plCoMon);
			t.start();
			try {
				t.join();
			} catch (InterruptedException e) {
				e.printStackTrace();
				}
			
			trigger(new GetNodesWithCoMonStatsResponse(event, hosts), pLabPort);
		}
	};
	
	private Handler<UpdateCoMonStats> handleUpdateCoMonStats = new Handler<UpdateCoMonStats>() {
		public void handle(UpdateCoMonStats event) {

			remoteUpdateCoMonStats(true);
		}
	};

	private void remoteUpdateCoMonStats(boolean background) {
		System.out.println("Getting CoMon stats in background.");

		CoMonUpdater plCoMon = new CoMonUpdater(readyHosts);
		Thread t = new Thread(plCoMon);
		t.start();
		if (background == false) {
			try {
				t.join();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}

//	private Handler<DnsResolverResponse> handleDnsResolverResponse = new Handler<DnsResolverResponse>() {
//		public void handle(DnsResolverResponse event) {
//			Map<Integer, InetAddress> mapIpAddrs = event.getIpAddrs();
//			for (Integer nodeId : mapIpAddrs.keySet()) {
//				InetAddress addr = mapIpAddrs.get(nodeId);
//				for (PLabHost host : store.getHosts()) {
//					if (host.getNodeId() == nodeId) {
//						host.setIp(addr);
//					}
//				}
//			}
//			readyHosts = store.getRunningHostsForThisSlice();
//			pLabService.saveOrUpdate(store);
//			System.out.println("Updated the store with resolved IP addresses.");
//
//		}
//	};

	private Handler<AddHostsToSliceRequest> handleAddHostsToSliceRequest = new Handler<AddHostsToSliceRequest>() {
		public void handle(AddHostsToSliceRequest event) {

			boolean res = addHostsToSlicePL(event.getHosts());
			trigger(new AddHostsToSliceResponse(event, res), pLabPort);
		}
	};

	private boolean addHostsToSlicePL(Set<String> hosts) {

		boolean success = false;

		Vector<String> nodesToAdd = new Vector<String>();

		for (String host : hosts) {
			nodesToAdd.add(host);
			for (PLabHost h : store.getHosts()) {
				if (h.getHostname().compareToIgnoreCase(host) == 0) {
					h.setRegisteredForSlice(true);
				}
			}
		}
		Vector<Object> params = new Vector<Object>();

		params.add(cred.getAuthMap());
		params.add(cred.getSlice());
		params.add(nodesToAdd);
		Object res = this.executeRPC("SliceNodesAdd", params);
		// Object res = this.executeRPC("SliceNodesList", params);
		success = ((Integer) res) == 1 ? true : false;

		System.out.println(success + " : added " + nodesToAdd.size()
				+ " new hosts to the slice");

		pLabService.saveOrUpdate(store);
		readyHosts = store.getRunningHostsForThisSlice();

		return success;
	}

	private Set<PLabHost> getHostsRegisteredToSlice(boolean forceGetHosts) {

		Vector<Object> params = new Vector<Object>();
		params.add(cred.getAuthMap());
		params.add(cred.getSlice());
		Object res = this.executeRPC("SliceNodesList", params);
		Object[] results = (Object[]) res;

		Set<String> hostnames = new HashSet<String>();
		for (Object o : results) {
			String h = (String) o;
			hostnames.add(h);
		}
		System.out.println("Number of hosts registered to slice: "
				+ hostnames.size());

		Set<PLabHost> hosts;
		if (forceGetHosts) {
			hosts = store.getHosts();
		} else {
			hosts = getAllNodesPL();
		}
		Set<PLabHost> hostsInSlice = new HashSet<PLabHost>();
		for (PLabHost h : hosts) {
			for (String s : hostnames) {
				if (h.getHostname().compareToIgnoreCase(s) == 0) {
					hostsInSlice.add(h);
				}
			}
		}

		// pLabService.saveOrUpdate(store);

		return hostsInSlice;
	}

	private Handler<GetProgressRequest> handleGetProgressRequest = new Handler<GetProgressRequest>() {
		public void handle(GetProgressRequest event) {
			trigger(new GetProgressResponse(event, progress), pLabPort);
		}
	};

	private Handler<UpdateHostsAndSites> handleUpdateHostsAndSites = new Handler<UpdateHostsAndSites>() {
		public void handle(UpdateHostsAndSites event) {

			cleanRemoteUpdate();
		}
	};

	private void updatePLabHostAttrs() {
		Set<PLabHost> noIP = new HashSet<PLabHost>();
		for (PLabHost h : readyHosts) {
			if (h.getIp() == null) {
				noIP.add(h);
			}
		}

		Map<Integer, String> hostAddrs = new HashMap<Integer, String>();
		for (PLabHost h : noIP) {
			hostAddrs.put(h.getNodeId(), h.getHostname());
		}
//		DnsResolverRequest req = new DnsResolverRequest(hostAddrs);
//		trigger(req, dns.getPositive(DnsResolverPort.class));
	}

	private void updateSliceStatus(Set<Integer> sliceNodes, Set<PLabHost> hosts) {
		for (int i : sliceNodes) {
			for (PLabHost h : hosts) {
				if (h.getNodeId() == i) {
					h.setRegisteredForSlice(true);
				}
			}
		}
	}

	private void cleanRemoteUpdate() {
		long startTime = System.currentTimeMillis();
		long time;

		Set<PLabSite> sites = getSites();
		time = System.currentTimeMillis() - startTime;
		System.out.println("PLC site query done: " + time + "ms");

		Set<PLabHost> hosts = updateHosts(startTime);

		// HashMap<Integer, Integer> hostToSiteMapping =
		// queryPLCHostToSiteMapping();
		// time = System.currentTimeMillis() - startTime;
		// System.out.println("PLC all queries done: " + time + "ms");

		// for (PLabHost host : hosts) {
		// host.setSiteId(hostToSiteMapping.get(host.getNodeId()));
		// }

		store = new PLabStore(cred.getSlice(), cred.getUsername());
		store.setHosts(hosts);
		store.setSites(sites);

		pLabService.saveOrUpdate(store);
		this.readyHosts.addAll(store.getRunningHostsForThisSlice());
		// new CoMonStatsComparator(CoMonStats.RESPTIME)
	}

	private Set<PLabHost> updateHosts(long startTime) {
		long time = System.currentTimeMillis() - startTime;
		Set<PLabHost> hosts = getAllNodesPL();
		System.out.println("PLC host query done: " + time + "ms");

		Map<Integer, String> hostAddrs = new HashMap<Integer, String>();
		for (PLabHost h : hosts) {
			hostAddrs.put(h.getNodeId(), h.getHostname());
		}
//		DnsResolverRequest req = new DnsResolverRequest(hostAddrs);
//		trigger(req, dns.getPositive(DnsResolverPort.class));

		Set<Integer> sliceNodes = getHostIdsForSlicePL();
		time = System.currentTimeMillis() - startTime;
		System.out.println("PLC nodes for this slice=" + sliceNodes.size()
				+ ", time taken: " + time + "ms");

		updateSliceStatus(sliceNodes, hosts);

		System.out.println("resolving IP addresses for " + hosts.size()
				+ " hosts in background...");
		return hosts;
	}

	private Set<PLabHost> getPLabHostsFromHostnames(Set<String> hostnames)
	{
		Set<PLabHost> foundHosts = new HashSet<PLabHost>();
		for (String h : hostnames) {
			for (PLabHost host : readyHosts)
			{
				if (host.getHostname().compareToIgnoreCase(h)==0)
				{
					foundHosts.add(host);
				}
			}
		}
		return foundHosts;
	}
	
	
	private Handler<GetNodesRequest> handleGetNodesRequest = new Handler<GetNodesRequest>() {
		public void handle(GetNodesRequest event) {

			Set<PLabHost> pLabHosts = getAllNodesPL();
			Set<Host> hosts = new HashSet<Host>();
			hosts.addAll(pLabHosts);
			GetNodesResponse respEvent = new GetNodesResponse(event, hosts);
			trigger(respEvent, pLabPort);

		}
	};
	
	private Handler<GetPLabNodesRequest> handleGetPLabNodesRequest = new Handler<GetPLabNodesRequest>() {
		public void handle(GetPLabNodesRequest event) {

			Set<PLabHost> hosts = getAllNodesPL();
			GetPLabNodesResponse respEvent = new GetPLabNodesResponse(event, hosts);
			trigger(respEvent, pLabPort);

		}
	};

	private Handler<GetHostsInSliceRequest> handleGetNodesInSliceRequest = new Handler<GetHostsInSliceRequest>() {
		public void handle(GetHostsInSliceRequest event) {

			GetHostsInSliceResponse respEvent;
			if (event.isForceDownload() == true) {
				Set<PLabHost> hosts = getHostsRegisteredToSlice(true);
				respEvent = new GetHostsInSliceResponse(event, hosts);
				readyHosts = hosts;
			} else {
				respEvent = new GetHostsInSliceResponse(event, readyHosts);
			}

			trigger(respEvent, pLabPort);

		}
	};

	private Handler<GetHostsNotInSliceRequest> handleGetNodesNotInSliceRequest = new Handler<GetHostsNotInSliceRequest>() {
		public void handle(GetHostsNotInSliceRequest event) {

			Set<PLabHost> hosts = store.getHosts();
			hosts.removeAll(readyHosts);
			boolean runningHosts = event.isRunning();

			if (runningHosts == true) {
				Set<PLabHost> notRunningHosts = new HashSet<PLabHost>();
				for (PLabHost host : hosts) {
					if (host.getBootState() != "boot") {
						notRunningHosts.add(host);
					}
				}
				hosts.removeAll(notRunningHosts);
			}

			GetHostsNotInSliceResponse respEvent = new GetHostsNotInSliceResponse(
					event, hosts);

			trigger(respEvent, pLabPort);

		}
	};

	private Set<Integer> getHostIdsForSlicePL() {

		List<Object> params = new ArrayList<Object>();
		params.add(cred.getPlanetLabAuthMap());

		HashMap<String, Object> filter = new HashMap<String, Object>();
		filter.put("name", cred.getSlice());
		params.add(filter);

		Vector<Object> returnFields = new Vector<Object>();
		// for (PLabHost host : store.getHosts()) {
		// returnFields.add(Integer.toString(host.getNodeId()));
		// }
		returnFields.add("node_ids");
		params.add(returnFields);

		Object response = executeRPC("GetSlices", params);

		Set<Integer> sliceNodes = new HashSet<Integer>();
		if (response instanceof Object[]) {
			Object[] result = (Object[]) response;
			if (result.length > 0) {
				Map map = (Map) result[0];
				Object[] nodes = (Object[]) map.get("node_ids");
				for (Object node : nodes) {
					sliceNodes.add((Integer) node);
				}
			}

		}
		return sliceNodes;
	}

	/**
	 * XXX this 
	 */
	private Handler<RankHostsUsingCoMonRequest> handleRankHostsUsingCoMonRequest = new Handler<RankHostsUsingCoMonRequest>() {
		public void handle(RankHostsUsingCoMonRequest event) {

			String[] rankingCriteria = event.getCoMonRankingCriteria();

			List<PLabHost> hosts;
			if (event.isForceDownload() == true) {
				Set<PLabHost> setHosts = getHostsRegisteredToSlice(true);
				hosts = orderHostsUsingCoMon(setHosts, rankingCriteria);
			} else {
				hosts = orderHostsUsingCoMon(readyHosts, rankingCriteria);
			}

			RankHostsUsingCoMonResponse respEvent = new RankHostsUsingCoMonResponse(
					event, hosts, rankingCriteria);
			trigger(respEvent, pLabPort);
		}
	};

	private Set<PLabHost> getAllNodesPL() {
		List<String> fields = new ArrayList<String>();
		fields.add(PLabHost.NODE_ID);
		fields.add(PLabHost.HOSTNAME);
		fields.add(PLabHost.BOOT_STATE);
		fields.add(PLabSite.SITE_ID);

		List<Object> params = new ArrayList<Object>();
		params.add(cred.getPlanetLabAuthMap());
		// plc api change, an empty map will return all nodes
		params.add(new HashMap());
		params.add(fields);

		Set<PLabHost> hosts = new HashSet<PLabHost>();
		Object response = executeRPC("GetNodes", params);
		if (response instanceof Object[]) {
			Object[] result = (Object[]) response;
			for (Object obj : result) {
				Map map = (Map) obj;
				PLabHost host = new PLabHost(map);
				hosts.add(host);
			}
		} else {
			logger.warn("Nothing returned by GetNodes");
		}
		return hosts;
	}

	// public Handler<UpdateCoMonStats> handleGetBootStates = new
	// Handler<UpdateCoMonStats>() {
	// @SuppressWarnings("unchecked")
	// public void handle(UpdateCoMonStats event) {
	//			
	// Vector params = new Vector();
	// params.add(cred.getAuthMap());
	// executeRPC("GetBootStates", params);
	//			
	// }
	// };

	private Handler<QueryPLabSitesRequest> handleQueryPLabSitesRequest = new Handler<QueryPLabSitesRequest>() {
		public void handle(QueryPLabSitesRequest event) {

			Set<PLabSite> sites = getSites();

			QueryPLabSitesResponse respEvent = new QueryPLabSitesResponse(
					event, sites);

			trigger(respEvent, pLabPort);
		}
	};

	private Set<PLabSite> getSites() {
		List<Object> rpcParams = new ArrayList<Object>();
		rpcParams.add(cred.getPlanetLabAuthMap());

		HashMap<String, Object> filter = new HashMap<String, Object>();
		rpcParams.add(filter);

		ArrayList<String> returnValues = new ArrayList<String>();
		returnValues.add(PLabSite.NAME);
		returnValues.add(PLabSite.ABBREVIATED_NAME);
		returnValues.add(PLabSite.SITE_ID);
		returnValues.add(PLabSite.LOGIN_BASE);
		rpcParams.add(returnValues);

		Set<PLabSite> sites = new HashSet<PLabSite>();

		Object response = executeRPC("GetSites", rpcParams);
		if (response instanceof Object[]) {
			Object[] result = (Object[]) response;
			for (Object res : result) {
				Map map = (Map) res;
				PLabSite site = new PLabSite(map);
				sites.add(site);
			}
		}
		return sites;
	}

	private Object executeRPC(String command, List<Object> params) {
		Object res = new Object();
		try {
			res = (Object) client.execute(command, params);
		} catch (XmlRpcException e) {
			if (e.getMessage().contains(
					"java.security.cert.CertPathValidatorException")) {
				System.err
						.println("There is a problem with the PlanetLab Central certificate: ");
				System.err.println("Config says: IgnoreCertificateErrors="
						+ cred.isIgnoreCerificateErrors());
				if (cred.isIgnoreCerificateErrors()
						&& retryingWithCertIgnore == false) {

					System.out.println("trying again");

					this.ignoreCertificateErrors();
					return this.executeRPC(command, params);
				} else {
					System.err
							.println("You need to add IgnoreCertificateErrors=true to the config file to have PLC XML-RPC support");
					System.err.println("XML-RPC error: " + e.getMessage());
				}
			} else {
				System.err.println(e.getMessage());
				// e.printStackTrace();
			}
		}

		return res;
	}

	private void ignoreCertificateErrors() {
		retryingWithCertIgnore = true;
		// Create a trust manager that does not validate certificate chains
		TrustManager[] trustAllCerts = new TrustManager[] { new X509TrustManager() {
			public X509Certificate[] getAcceptedIssuers() {
				return null;
			}

			public void checkClientTrusted(X509Certificate[] certs,
					String authType) {
				// Trust always
			}

			public void checkServerTrusted(X509Certificate[] certs,
					String authType) {
				// Trust always
			}
		} };

		// Install the all-trusting trust manager
		SSLContext sc;
		try {
			sc = SSLContext.getInstance("SSL");

			// Create empty HostnameVerifier
			HostnameVerifier hv = new HostnameVerifier() {
				public boolean verify(String arg0, SSLSession arg1) {
					return true;
				}
			};

			sc.init(null, trustAllCerts, new java.security.SecureRandom());
			HttpsURLConnection
					.setDefaultSSLSocketFactory(sc.getSocketFactory());
			HttpsURLConnection.setDefaultHostnameVerifier(hv);
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		} catch (KeyManagementException e) {
			e.printStackTrace();
		}
	}

	private class MyTypeFactory extends TypeFactoryImpl {
		public MyTypeFactory(XmlRpcController pController) {
			super(pController);
		}

		public TypeParser getParser(XmlRpcStreamConfig pConfig,
				NamespaceContextImpl pContext, String pURI, String pLocalName) {

			// the plc api is using nil, but not reporting that they are using
			// extensions
			if (NullSerializer.NIL_TAG.equals(pLocalName)) {
				return new NullParser();
			}
			// System.err.println("pURI: " + pURI + " plocalName: " +
			// pLocalName);

			return super.getParser(pConfig, pContext, pURI, pLocalName);

		}

		public TypeSerializer getSerializer(XmlRpcStreamConfig pConfig,
				Object pObject) throws SAXException {
			return super.getSerializer(pConfig, pObject);
		}
	}

	// private class ParallelDNSLookup {
	// private final ExecutorService threadPool;
	//
	// private final Set<PLabHost> setHosts;
	//
	// private final ConcurrentLinkedQueue<Future<PLabHost>> tasks;
	//
	// public ParallelDNSLookup(int numThreads, Set<PLabHost> setHosts) {
	// this.setHosts = new HashSet<PLabHost>(setHosts);
	//
	// this.threadPool = Executors.newFixedThreadPool(numThreads);
	// this.tasks = new ConcurrentLinkedQueue<Future<PLabHost>>();
	// }
	//
	// public void performLookups() {
	// int i = 0;
	// for (PLabHost host : setHosts) {
	// Future<PLabHost> task = threadPool.submit(new ResolveIPHandler(host,
	// i++));
	// tasks.add(task);
	// }
	// }
	//
	// public void join() {
	// Future<PLabHost> task;
	// try {
	// // for all tasks
	// while ((task = tasks.poll()) != null) {
	// // block until task is done
	// PLabHost result = (PLabHost) task.get();
	// if (result.getIp() != null) {
	// System.err.println(result.getIp());
	// }
	// }
	// store.setHosts(readyHosts);
	// pLabService.saveOrUpdate(store);
	// threadPool.shutdown();
	// } catch (InterruptedException e) {
	// e.printStackTrace();
	// } catch (ExecutionException e) {
	// e.printStackTrace();
	// }
	//
	// }
	// }
	//
	// private class ResolveIPHandler implements Callable<PLabHost> {
	// private PLabHost host;
	//
	// int num;
	//
	// public ResolveIPHandler(PLabHost host, int num) {
	// this.host = host;
	// this.num = num;
	// }
	//
	// public PLabHost call() {
	//
	// try {
	// InetAddress addr = InetAddress.getByName(host.getHostname());
	// host.setIp(addr); // .getHostAddress()
	// return host;
	// } catch (UnknownHostException e) {
	// host.setIp(null);
	// return host;
	// }
	// }
	//
	// }

	public class CoMonUpdater implements Runnable {

		private static final String COMON_URL = "http://summer.cs.princeton.edu/status/tabulator.cgi?"
				+ "table=table_nodeviewshort&format=formatcsv";

		private int totalBytes = 0;

		private HashMap<String, PLabHost> plHostMap;

		public CoMonUpdater(Set<PLabHost> hosts) {
			plHostMap = new HashMap<String, PLabHost>();

			for (PLabHost host : hosts) {
				// store copy of PLabHost objects here. When we update them, we
				// replace the orginal PLabHost objects in the 'hosts' field
				// with the
				// new version including CoMon data.
				plHostMap.put(host.getHostname(), new PLabHost(host));
			}
		}

		public void run() {
			totalBytes = 0;
			progress = 0;
			BufferedReader in = null;
			try {
				URL url = new URL(COMON_URL);

				// HttpURLConnection connection = (HttpURLConnection) url
				// .openConnection();

				// ok, this IS an uggly hack :-)
				totalBytes = 240717;// connection.getContentLength();

				in = new BufferedReader(new InputStreamReader(url.openStream()));

				String titleLine = in.readLine();
				int downloadedBytes = titleLine.length();
				if (titleLine != null
						&& titleLine.split(",").length == CoMonStats.NUM_COMON_COLUMNS) {

					String inputLine = in.readLine();
					while ((inputLine = in.readLine()) != null) {
						CoMonStats statEntry = new CoMonStats(inputLine);
						String hostname = statEntry.getHostname();
						if (plHostMap.containsKey(hostname)) {
							PLabHost plHost = plHostMap.get(hostname);
							plHost.setCoMonStat(statEntry);
							System.out.println(plHost.getComMonStat()
									.getHostname()
									+ ": "
									+ plHost.getComMonStat().getStat(
											CoMonStats.RESPTIME));
							// assuming hosts is a concurrent collection, and
							// that
							// we replace the existing PLabHost object with this
							// new one.
							readyHosts.add(plHost);
						}

						downloadedBytes += inputLine.length();

						progress = ((double) downloadedBytes)
								/ (double) totalBytes;
					}
				} else if (titleLine.split(",").length != CoMonStats.NUM_COMON_COLUMNS) {
					System.err
							.println("New comon format, please upgrade your plman version (colnum="
									+ titleLine.split(",").length + ")");
				}

			} catch (MalformedURLException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			} finally {
				if (in != null) {
					try {
						in.close();
					} catch (IOException e) {
						System.err
								.println("Problem closing connection to CoMonManager URL");
					}
				}
			}
			// progress = 1.0;

		}

		public double getProgress() {
			return progress;
		}

	}

	/**
	 * @param hosts
	 * @param coMonStat
	 *            One of the parameters in CoMonStats.STATS
	 * @return
	 */
	public static List<PLabHost> orderHostsUsingCoMon(Set<PLabHost> hosts,
			String[] stats) {
		List<PLabHost> listHosts = new ArrayList<PLabHost>();
		listHosts.addAll(hosts);
		Collections.sort(listHosts, new CoMonStatsComparator(stats));

		return listHosts;
	}
}
