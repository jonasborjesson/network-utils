/**
 * 
 */
package com.jonasborjesson.network.udpsinkhole;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.jboss.netty.bootstrap.ConnectionlessBootstrap;
import org.jboss.netty.channel.ChannelFactory;
import org.jboss.netty.channel.ChannelHandler;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.channel.socket.nio.NioDatagramChannelFactory;
import org.jboss.netty.util.HashedWheelTimer;
import org.jboss.netty.util.Timeout;
import org.jboss.netty.util.Timer;
import org.jboss.netty.util.TimerTask;

/**
 * Super simple class mainly used for testing SIP related services (sending RTP
 * to this guy). This class will simply just listen to a particular UDP port and
 * consume anything that shows up.
 * 
 * @author jonas@jonasborjesson.com
 */
public final class UdpSinkHole extends SimpleChannelUpstreamHandler implements TimerTask {

    private final static Logger logger = Logger.getLogger(UdpSinkHole.class);

    /**
     * Different stat logger because you may want to get the statistics dumped
     * to a different file and/or with a different format than the normal
     * program logging.
     */
    private final static Logger statLogger = Logger.getLogger("com.jonasborjesson.network.udpsinkhole.stats");

    private ConnectionlessBootstrap bootstrap;

    /**
     * Our settings object that contains, amongst other things, our listening
     * point etc
     */
    private final Settings settings;

    /**
     * Counts all the packets we have received
     */
    private final AtomicLong packetsReceived = new AtomicLong();

    /**
     * Timer used to dump statistics every x seconds. How often we are going to
     * dump is controlled by the interval setting found within our Settings
     * object
     */
    private Timer timer;

    static {
        // TODO: use file instead
        BasicConfigurator.configure();
        Logger.getRootLogger().setLevel(Level.DEBUG);
    }

    /**
     * Constructor taking in the settings
     * 
     * @param settings
     */
    public UdpSinkHole(final Settings settings) {
        assert settings != null;
        this.settings = settings;
    }

    /**
     * Life-cycle method for settings up everything related to the sink hole,
     * such as configuring netty, setting up timers etc.
     */
    public void create() {
        final ChannelFactory channelFactory = new NioDatagramChannelFactory(Executors.newCachedThreadPool());
        this.bootstrap = new ConnectionlessBootstrap(channelFactory);
        final ChannelHandler handler = this;
        this.bootstrap.setPipelineFactory(new ChannelPipelineFactory() {
            @Override
            public ChannelPipeline getPipeline() throws Exception {
                return Channels.pipeline(handler);
            }
        });
        this.bootstrap.setOption("localAddress", this.settings.getListeningPoint());

        this.timer = new HashedWheelTimer();
    }

    /**
     * Start listening for incoming packets
     */
    public void start() {
        logger.info("Starting the UDP sink hole with settings:");
        logger.info(this.settings);
        this.bootstrap.bind();
        this.timer.newTimeout(this, this.settings.getInterval(), TimeUnit.SECONDS);
    }

    /**
     * @param args
     */
    public static void main(final String[] args) throws Exception {

        final Settings settings = UdpSinkHole.processArguments(args);
        if (settings == null) {
            return;
        }

        final UdpSinkHole sinkhole = new UdpSinkHole(settings);
        sinkhole.create();
        sinkhole.start();
    }

    private static Settings processArguments(final String[] args) throws ParseException {
        final Options options = buildOptions();

        final HelpFormatter formatter = new HelpFormatter();
        final CommandLineParser parser = new GnuParser();
        final CommandLine line = parser.parse(options, args);
        if (line.hasOption("h")) {
            formatter.printHelp("run.sh", options);
            return null;
        }

        final String listeningAddress = line.getOptionValue("l", "127.0.0.1:7655");
        final String[] hostport = listeningAddress.split(":");
        final String hostname = hostport[0];
        int port = 7655;
        if (hostport.length > 1) {
            port = Integer.parseInt(hostport[1]);
        }
        final SocketAddress listeningPoint = new InetSocketAddress(hostname, port);
        final Settings settings = new Settings(listeningPoint);

        try {
            final int interval = Integer.parseInt(line.getOptionValue("t", "1"));
            settings.setInterval(interval);
        } catch (final NumberFormatException e) {
            System.err.println("-t only takes integers");
            formatter.printHelp("run.sh", options);
            return null;
        }

        return settings;
    }

    private static Options buildOptions() {
        final Options options = new Options();
        options.addOption("h", false, "Print this help");

        // chaining them together usually leads to really ugly line breaks
        // plus the compiler will complain about accessing static methods
        // on an instance (and I don't like having suppress warnings unless
        // I really have to. Here I don't...)
        OptionBuilder.withArgName("ip:port");
        OptionBuilder.hasArg();
        OptionBuilder.withDescription("The listening point");
        options.addOption(OptionBuilder.create("l"));

        OptionBuilder.withArgName("interval");
        OptionBuilder.hasArg();
        OptionBuilder.withDescription("How frequently we are dumping statistics (in seconds)");
        options.addOption(OptionBuilder.create("t"));

        return options;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void exceptionCaught(final ChannelHandlerContext ctx, final ExceptionEvent e) throws Exception {
        logger.warn(e.getCause());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void messageReceived(final ChannelHandlerContext ctx, final MessageEvent e) throws Exception {
        this.packetsReceived.incrementAndGet();
        super.messageReceived(ctx, e);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void run(final Timeout timeout) throws Exception {
        statLogger.info("n=" + this.packetsReceived.get());
        this.timer.newTimeout(this, this.settings.getInterval(), TimeUnit.SECONDS);
    }

    /**
     * Basic class for keeping track of all the settings that are available for
     * the UdpSinkHole
     * 
     * @author jonas@jonasborjesson.com
     */
    private static final class Settings {

        private final SocketAddress listeningPoint;

        /**
         * Controls how frequently we will dump statistics (in seconds)
         */
        private int interval;

        /**
         * The listening point is the only mandatory setting.
         * 
         * @param listeningPoint
         */
        public Settings(final SocketAddress listeningPoint) {
            assert listeningPoint != null;
            this.listeningPoint = listeningPoint;
            this.interval = 1;
        }

        public SocketAddress getListeningPoint() {
            return this.listeningPoint;
        }

        public void setInterval(final int interval) {
            if (interval < 1) {
                this.interval = 1;
            } else {
                this.interval = interval;
            }
        }

        public int getInterval() {
            return this.interval;
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder();
            sb.append("Listening point: ").append(this.listeningPoint).append("\n");
            sb.append("Interval (s)   : ").append(this.interval).append("\n");
            return sb.toString();
        }
    }
}
