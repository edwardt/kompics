/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package se.sics.kompics.network.test;

import com.google.common.primitives.Ints;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.UnknownHostException;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import static org.junit.Assert.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.sics.kompics.Channel;
import se.sics.kompics.Component;
import se.sics.kompics.ComponentDefinition;
import se.sics.kompics.ControlPort;
import se.sics.kompics.Event;
import se.sics.kompics.Handler;
import se.sics.kompics.Init;
import se.sics.kompics.Init.None;
import se.sics.kompics.Kompics;
import se.sics.kompics.Negative;
import se.sics.kompics.Port;
import se.sics.kompics.PortType;
import se.sics.kompics.Positive;
import se.sics.kompics.Start;
import se.sics.kompics.address.Address;
import se.sics.kompics.network.Message;
import se.sics.kompics.network.MessageNotify;
import se.sics.kompics.network.Network;
import se.sics.kompics.network.Transport;
import se.sics.kompics.network.VirtualNetworkChannel;

/**
 *
 * @author Lars Kroll <lkroll@sics.se>
 */
public class NetworkTest {

    private static final Logger LOG = LoggerFactory.getLogger(NetworkTest.class);
    private static final int SEED = 0;
    //private static final String STARTED = "STARTED";
    private static final String STOPPED = "STOPPED";
    private static final String SENDING = "SENDING";
    private static final String RECEIVED = "RECEIVED";
    private static final String ACKED = "ACKED";
    private static final String SENT = "SENT";
    private static final String FAIL = "FAIL";
    private static final int NUM_MESSAGES = 100;
    private static final int BATCH_SIZE = 10;
    private static final AtomicInteger WAIT_FOR = new AtomicInteger(NUM_MESSAGES);
    private static NetworkGenerator nGen;
    private static int numNodes;
    private static AtomicInteger msgId = new AtomicInteger(0);
    private static ConcurrentMap<Integer, String> messageStatus = new ConcurrentSkipListMap<Integer, String>();
    //private static int startPort = 33000;
    private static Transport[] protos;

    public static synchronized void runTests(NetworkGenerator nGen, int numNodes, Transport[] protos) {
        LOG.info("******************** Running All Test ********************");
        NetworkTest.nGen = nGen;
        NetworkTest.numNodes = numNodes;
        NetworkTest.protos = protos;
        WAIT_FOR.set(NUM_MESSAGES);

        msgId.set(0);
        messageStatus.clear();
        TestUtil.reset(10000); //10 sec timeout for all the connections to be dropped properly

        Kompics.createAndStart(LauncherComponent.class, 8, 50);

        for (int i = 0; i < numNodes; i++) {
            LOG.info("Got {}/{} STOPPED.", i, numNodes);
            TestUtil.waitFor(STOPPED);
        }
        Kompics.shutdown();

        assertEquals(NUM_MESSAGES * numNodes, messageStatus.size());
        for (String s : messageStatus.values()) {
            assertEquals(ACKED, s);
        }
    }

    public static synchronized void runAtLeastTests(NetworkGenerator nGen, int numNodes, Transport[] protos) {
        LOG.info("******************** Running AT LEAST Test ********************");
        NetworkTest.nGen = nGen;
        NetworkTest.numNodes = numNodes;
        NetworkTest.protos = protos;
        WAIT_FOR.set(1);

        msgId.set(0);
        messageStatus.clear();
        TestUtil.reset(10000); //10 sec timeout for all the connections to be dropped properly

        Kompics.createAndStart(LauncherComponent.class, 8, 50);

        for (int i = 0; i < numNodes; i++) {
            LOG.info("Got {}/{} STOPPED.", i, numNodes);
            TestUtil.waitFor(STOPPED);
        }
        Kompics.shutdown();

        assertTrue(numNodes <= messageStatus.size());
    }

    public static class LauncherComponent extends ComponentDefinition {
        
        private Random rand = new Random(SEED);

        public LauncherComponent() {
            Address[] nodes = new Address[numNodes];
            Address[] fakeNodes = new Address[numNodes]; // these are used to test that the network doesn't crash on connection errors
            for (int i = 0; i < numNodes; i++) {
                try {
                    byte[] ipB = new byte[4];
                    rand.nextBytes(ipB);
                    InetAddress ip = InetAddress.getByAddress(ipB);
                    int port = rand.nextInt(65535-49152)+49152;
                    fakeNodes[i] = new Address(ip, port, Ints.toByteArray(i));
                } catch (UnknownHostException ex) {
                    LOG.error("Aborting test.", ex);
                    System.exit(1);
                }
            }
            InetAddress ip = null;
            try {
                ip = InetAddress.getByName("127.0.0.1");
            } catch (UnknownHostException ex) {
                LOG.error("Aborting test.", ex);
                System.exit(1);
            }
            List<ServerSocket> sockets = new LinkedList<ServerSocket>();
            for (int i = 0; i < numNodes; i++) {
                int port = -1;
                try {
                    ServerSocket s = new ServerSocket(0); // try to find a free port for each address
                    sockets.add(s);
                    port = s.getLocalPort();
                } catch (IOException ex) {
                    LOG.error("Could not find any free ports: {}", ex);
                    System.exit(1);
                }
                if (port < 0) {
                    LOG.error("Could not find enough free ports!");
                    System.exit(1);
                }
                nodes[i] = new Address(ip, port, Ints.toByteArray(i));
                Component net = nGen.generate(myProxy, nodes[i]);
                VirtualNetworkChannel vnc = VirtualNetworkChannel.connect(net.provided(Network.class));
                Component scen = create(ScenarioComponent.class, new ScenarioInit(nodes[i], nodes, fakeNodes));
                vnc.addConnection(Ints.toByteArray(i), scen.required(Network.class));
            }
            // check that all ports are unique
            Set<Integer> portSet = new TreeSet<Integer>();
            for (Address addr : nodes) {
                portSet.add(addr.getPort());
            }
            if (portSet.size() != nodes.length) {
                LOG.error("Some ports do not appear to be unique! \n {} \n");
                System.exit(1);
            }
            for (ServerSocket s : sockets) {
                try {
                    s.close();
                } catch (IOException ex) {
                    LOG.error("Could not close port: {}", ex);
                    System.exit(1);
                }
            }
        }
        private final ComponentProxy myProxy = new ComponentProxy() {
            @Override
            public <P extends PortType> void trigger(Event e, Port<P> p) {
                LauncherComponent.this.trigger(e, p);
            }

            @Override
            public <T extends ComponentDefinition> Component create(Class<T> definition, Init<T> initEvent) {
                return LauncherComponent.this.create(definition, initEvent);
            }

            @Override
            public <T extends ComponentDefinition> Component create(Class<T> definition, None initEvent) {
                return LauncherComponent.this.create(definition, initEvent);
            }

            @Override
            public void destroy(Component component) {
                LauncherComponent.this.destroy(component);
            }

            @Override
            public <P extends PortType> Channel<P> connect(Positive<P> positive, Negative<P> negative) {
                return LauncherComponent.this.connect(positive, negative);
            }

            @Override
            public <P extends PortType> Channel<P> connect(Negative<P> negative, Positive<P> positive) {
                return LauncherComponent.this.connect(negative, positive);
            }

            @Override
            public <P extends PortType> void disconnect(Negative<P> negative, Positive<P> positive) {
                LauncherComponent.this.disconnect(negative, positive);
            }

            @Override
            public <P extends PortType> void disconnect(Positive<P> positive, Negative<P> negative) {
                LauncherComponent.this.disconnect(positive, negative);
            }

            @Override
            public Negative<ControlPort> getControlPort() {
                return LauncherComponent.this.control;
            }
        };
    }

    public static class ScenarioComponent extends ComponentDefinition {

        public final Address self;
        public final Address[] nodes;
        public final Address[] fakeNodes;
        private final Positive<Network> net = requires(Network.class);
        private int msgCount = 0;
        private int ackCount = 0;
        private Random rand = new Random(SEED);
        private Map<UUID, Integer> pending = new TreeMap<UUID, Integer>();

        public ScenarioComponent(ScenarioInit init) {
            self = init.self;
            nodes = init.nodes;
            fakeNodes = init.fakeNodes;

            Handler<Start> startHandler = new Handler<Start>() {
                @Override
                public void handle(Start event) {
                    for (int i = 0; i < BATCH_SIZE; i++) {
                        sendMessage();
                    }
                }
            };
            subscribe(startHandler, control);

            Handler<Ack> ackHandler = new Handler<Ack>() {
                @Override
                public void handle(Ack event) {
                    LOG.debug("Got Ack {}", event);
                    messageStatus.put(event.msgId, ACKED);
                    ackCount++;

                    if (ackCount >= WAIT_FOR.get()) {
                        TestUtil.submit(STOPPED);
                        return;
                    }

                    if (msgCount < NUM_MESSAGES) {
                        for (int i = 0; i < BATCH_SIZE; i++) {
                            sendMessage();
                        }
                    }
                }
            };
            subscribe(ackHandler, net);

            Handler<TestMessage> msgHandler = new Handler<TestMessage>() {
                @Override
                public void handle(TestMessage event) {
                    LOG.debug("Got message {}", event);
                    messageStatus.put(event.msgId, RECEIVED);
                    trigger(event.ack(), net);
                }
            };
            subscribe(msgHandler, net);

            Handler<MessageNotify.Resp> notifyHandler = new Handler<MessageNotify.Resp>() {

                @Override
                public void handle(MessageNotify.Resp event) {
                    Integer msgId = pending.remove(event.msgId);
                    assertNotNull(msgId);
                    messageStatus.replace(msgId, SENDING, SENT);
                    LOG.debug("Message {} was sent.", msgId);
                }
            };
            subscribe(notifyHandler, net);
        }

        private void sendMessage() {
            int id = msgId.getAndIncrement();
            if (messageStatus.putIfAbsent(id, SENDING) != null) {
                LOG.error("Key {} was already present in messageStatus!", id);
                TestUtil.submit(FAIL);
            }
            Transport proto = NetworkTest.protos[rand.nextInt(NetworkTest.protos.length)];
            TestMessage msg = new TestMessage(self, nodes[rand.nextInt(nodes.length)], id, proto);
            TestMessage fakemsg = new TestMessage(self, fakeNodes[rand.nextInt(nodes.length)], id, proto); // send this as well
            MessageNotify.Req req = MessageNotify.create(msg);
            pending.put(req.getMsgId(), id);
            trigger(req, net);
            trigger(fakemsg, net); // see fakeNodes in LauncherComponent
            msgCount++;
        }
    }

    public static class ScenarioInit extends Init<ScenarioComponent> {

        public final Address self;
        public final Address[] nodes;
        public final Address[] fakeNodes;

        public ScenarioInit(Address self, Address[] nodes, Address[] fakeNodes) {
            this.self = self;
            this.nodes = nodes;
            this.fakeNodes = fakeNodes;
        }
    }

    public static class TestMessage extends Message {

        public final int msgId;

        public TestMessage(Address src, Address dst, int id, Transport p) {
            super(src, dst, p);
            this.msgId = id;
        }

        public Ack ack() {
            return new Ack(this.getDestination(), this.getSource(), msgId, this.getProtocol());
        }
    }

    public static class Ack extends Message {

        public final int msgId;

        public Ack(Address src, Address dst, int id, Transport p) {
            super(src, dst, p);
            this.msgId = id;
        }
    }
}
