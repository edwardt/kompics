package se.sics.kompics.wan.master;

import java.net.MalformedURLException;
import java.net.URL;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Vector;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.apache.xmlrpc.XmlRpcException;
import org.apache.xmlrpc.client.XmlRpcClient;
import org.apache.xmlrpc.client.XmlRpcClientConfigImpl;

import se.sics.kompics.wan.plab.CoMon;
import se.sics.kompics.wan.plab.PLabHost;

public class GetNodes {

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		// TODO Auto-generated method stub
		new GetNodes(args[0]);
	}

	TrustManager[] trustAllCerts = new TrustManager[] { new X509TrustManager() {
		public java.security.cert.X509Certificate[] getAcceptedIssuers() {
			// XXX
			return null;
		}

		@Override
		public void checkClientTrusted(java.security.cert.X509Certificate[] arg0, String arg1)
				throws CertificateException {
		}

		@Override
		public void checkServerTrusted(java.security.cert.X509Certificate[] arg0, String arg1)
				throws CertificateException {

		}
	} };

	@SuppressWarnings("unchecked")
	public GetNodes(String myPlcPassWord) {

		try {
			// Install the all-trusting trust manager
			SSLContext sc = SSLContext.getInstance("SSL");
			// Create empty HostnameVerifier
			HostnameVerifier hv = new HostnameVerifier() {
				public boolean verify(String arg0, SSLSession arg1) {
					return true;
				}
			};
			sc.init(null, trustAllCerts, new java.security.SecureRandom());
			HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
			HttpsURLConnection.setDefaultHostnameVerifier(hv);

			XmlRpcClientConfigImpl config = new XmlRpcClientConfigImpl();

			config.setServerURL(new URL("https://www.planet-lab.org/PLCAPI/"));
			config.setEncoding("iso-8859-1");
			config.setEnabledForExtensions(true);
			XmlRpcClient client = new XmlRpcClient();

			client.setConfig(config);
			Map auth = new HashMap(); 
			auth.put("Username", "jdowling@sics.se");
			auth.put("AuthMethod", "password");
			auth.put("AuthString", myPlcPassWord);
			auth.put("Role", "user");
			auth.put("Slice", "sics_grid4all");

			Vector params = new Vector();
			List<String> fields = new ArrayList<String>();
			fields.add(PLabHost.NODE_ID);
			fields.add(PLabHost.HOSTNAME);
			fields.add(PLabHost.BOOT_STATE);
			fields.add(PLabHost.SITE_ID);

			
			params.add(auth);
			params.add(new HashMap());
			params.add(fields);
			
			List<PLabHost> listHosts = new ArrayList<PLabHost>();
			
			// GetBootStates
			Object[] res = (Object[]) client.execute("GetNodes", params);
			if (res.length == 0) {
				System.err.println("Warning: No boot-states found!");
			} else {
				for (int i = 0; i < res.length; i++) {
					HashMap m = (HashMap) res[i];
					for (Object obj : m.keySet()) {
						System.out.println("(" + obj.toString() + "," + m.get(obj).toString() + "), ");						
					}
					PLabHost host = new PLabHost(m);
					listHosts.add(host);
				}
			}
			
			CoMon cMan = new CoMon(listHosts);
			cMan.run();
			for (PLabHost h : listHosts) {
				System.out.println(h.toString());
			}
			

		} catch (XmlRpcException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			System.out.println(e.getMessage());
		} catch (MalformedURLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			System.out.println(e.getMessage());
		} catch (NoSuchAlgorithmException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (KeyManagementException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
}
