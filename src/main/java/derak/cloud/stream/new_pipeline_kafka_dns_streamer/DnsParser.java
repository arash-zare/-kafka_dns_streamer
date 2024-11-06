package derak.cloud.stream.new_pipeline_kafka_dns_streamer;

import com.fasterxml.jackson.core.JsonProcessingException;
import derak.cloud.stream.new_pipeline_kafka_dns_streamer.avro.DnsLog;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.lang.String;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.Produced;
import org.apache.kafka.streams.kstream.KStream;
import io.confluent.kafka.serializers.AbstractKafkaAvroSerDeConfig;
import io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig;
import io.confluent.kafka.streams.serdes.avro.SpecificAvroSerde;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import lombok.extern.slf4j.Slf4j;
import derak.cloud.stream.new_pipeline_kafka_dns_streamer.model.GeoData;
import derak.cloud.stream.new_pipeline_kafka_dns_streamer.services.GeoIpProcessor;
import derak.cloud.stream.new_pipeline_kafka_dns_streamer.services.DerakServerInfoProcessor;
import derak.cloud.stream.new_pipeline_kafka_dns_streamer.services.NetworkProcessor;

//@Slf4j
public class DnsParser {

    private static final long RELOAD_INTERVAL = 5000; // Reload interval in milliseconds
    private static final Map<String, Long> fileLastModifiedTimes = new HashMap<>();
    private static final List<String> filePaths = new ArrayList<>();
    private static boolean fileChanged = false;
    private static KafkaStreams streams;


    private static void init(DnsLog dnsLog) {
        try {
            for (Field field : DnsLog.class.getDeclaredFields()) {
                field.setAccessible(true);
                if (field.getType().equals(String.class)) {
                    field.set(dnsLog, "");
                } else if (field.getType().equals(Boolean.class)) {
                    field.set(dnsLog, false);
                } else if (field.getType().equals(Long.class)) {
                    field.set(dnsLog, 0);
                } else if (field.getType().equals(Integer.class)) {
                    field.set(dnsLog, 0);
                } else if (field.getType().equals(Double.class)) {
                    field.set(dnsLog, 0.0);
                } else if (field.getType().equals(Float.class)) {
                    field.set(dnsLog, 0.0);
                }
            }
        } catch (IllegalAccessException e) {
//            log.error("cannot init dns log");
        }
    }

    public static void main(String[] args) throws IOException {
        Properties configProps = new Properties();
        String path = "/root/config.properties";
        FileInputStream file = new FileInputStream(path);
        configProps.load(file);

        file.close();
        filePaths.add(configProps.getProperty("derakinfo.file"));
        filePaths.add(configProps.getProperty("geoip.asnFilePath"));
        filePaths.add(configProps.getProperty("geoip.cityFilePath"));
        filePaths.add(configProps.getProperty("networkFilePath"));

        startReloadThread();

        // Wait for the initial load of the file contents
        synchronized (DnsParser.class) {
            while (fileLastModifiedTimes.size() < filePaths.size()) {
                try {
                    DnsParser.class.wait();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }

        // Start the streamer function in a separate thread
        Thread streamerThread = new Thread(() -> {
            while (true) {
                if (fileChanged) {
                    synchronized (DnsParser.class) {
                        fileChanged = false;
                        if (streams != null) {
                            streams.close();
                        }
                        streamerFunc(configProps);
                    }
                }
                try {
                    Thread.sleep(1000); // Check for file changes every 30 second
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        });

        streamerThread.setDaemon(true);
        streamerThread.start();

        // Keep the main thread alive
        try {
            streamerThread.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }



        System.exit(0);
    }

    private static void streamerFunc(Properties configProps) {
        System.out.println("StreamerFunc run.");
        final String bootstrapServers = configProps.getProperty("bootstrapServers");
        final String schemaRegistryUrl = configProps.getProperty("schemaRegistryUrl");
        final String applicationId = configProps.getProperty("applicationId");


        GeoIpProcessor processor = new GeoIpProcessor(
                Integer.parseInt(configProps.getProperty("geoip.cacheSize")),
                configProps.getProperty("geoip.asnFilePath"),
                configProps.getProperty("geoip.cityFilePath"),
                Boolean.parseBoolean(configProps.getProperty("geoip.onMemory"))
        );
        NetworkProcessor privateProcessor =
                new NetworkProcessor(configProps.getProperty("networkFilePath"));
        DerakServerInfoProcessor derakInfoProcessor =
                new DerakServerInfoProcessor(configProps.getProperty("derakinfo.file"));

        final String inputTopic = configProps.getProperty("kafka.input_topic");
        final String outputTopic = configProps.getProperty("kafka.output_topic");
        final String exceptionTopic = configProps.getProperty("kafka.exception_topic");
        final String shutDownHook = configProps.getProperty("kafka.shut_down_hook");

        final Properties streamsConfiguration = new Properties();


        streamsConfiguration.put(StreamsConfig.APPLICATION_ID_CONFIG, applicationId);
        streamsConfiguration.put(StreamsConfig.CLIENT_ID_CONFIG, "dns-process-client");
        streamsConfiguration.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        streamsConfiguration.put(AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG, schemaRegistryUrl);
        streamsConfiguration.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, SpecificAvroSerde.class);
        streamsConfiguration.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass().getName());
        streamsConfiguration.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        streamsConfiguration.put(StreamsConfig.COMMIT_INTERVAL_MS_CONFIG, 10 * 1000);


        final StreamsBuilder builder = new StreamsBuilder();

        PrimaryDnsParser primaryDnsParser = new PrimaryDnsParser();

        final Serde<String> stringSerde = Serdes.String();

        final KStream<String, String> rawDnsLogStream =
                builder.stream(inputTopic, Consumed.with(stringSerde, stringSerde));

        KStream<String, DnsLog> dnsKStream = rawDnsLogStream.mapValues(rawDnsLog -> {
            DnsLog dnsLog = new DnsLog();
            primaryDnsParser.primaryParse(rawDnsLog, dnsLog);
            String forwarder_ip = dnsLog.getForwarderAddr();
            String nsIp = dnsLog.getNsServerIp();
            String ns = dnsLog.getNs();
            String nsCountry = dnsLog.getNsCountry();
            derakInfoProcessor.enrichWithDerakInfo(dnsLog);

            // for ns
            GeoData geoData = privateProcessor.findIpInfo(
                    nsIp,
                    ns,
                    nsCountry);
            if (geoData == null) {
                geoData = processor.execute(nsIp);
            }

            if (geoData != null) {
                dnsLog.setNsAsn(geoData.getAsn());
                dnsLog.setNsOrg(geoData.getOrg());
                dnsLog.setNsContinent(geoData.getContinent());
                dnsLog.setNsCountry(geoData.getCountry());
                dnsLog.setNsCity(geoData.getCity());
                dnsLog.setNsLat(geoData.getLat());
                dnsLog.setNsLon(geoData.getLon());
            }

            // for forwarder
            geoData = privateProcessor.findIpInfo(
                    forwarder_ip,
                    ns,
                    nsCountry);
            if (geoData == null) {
                geoData = processor.execute(forwarder_ip);
            }
            if (geoData != null) {
                dnsLog.setForwarderAsn(geoData.getAsn());
                dnsLog.setForwarderCountry(geoData.getCountry());
                dnsLog.setForwarderContinent(geoData.getContinent());
                dnsLog.setForwarderOrg(geoData.getOrg());
                dnsLog.setForwarderCity(geoData.getCity());
                dnsLog.setForwarderLat(geoData.getLat());
                dnsLog.setForwarderLon(geoData.getLon());
            }

            // for client
            if (dnsLog.getEdnsSupport()) {
                geoData = privateProcessor.findIpInfo(dnsLog.getClientAddr(),
                        ns, nsCountry);
                if (geoData == null) {
                    geoData = processor.execute(forwarder_ip);
                }
            }

            if (geoData != null) {
                dnsLog.setClientAsn(geoData.getAsn());
                dnsLog.setClientCountry(geoData.getCountry());
                dnsLog.setClientContinent(geoData.getContinent());
                dnsLog.setClientOrg(geoData.getOrg());
                dnsLog.setClientCity(geoData.getCity());
                dnsLog.setClientLat(geoData.getLat());
                dnsLog.setClientLon(geoData.getLon());
            }


            return dnsLog;
        });


        KStream<String, DnsLog>[] branches = dnsKStream.branch((key, value) ->
                !"".equals(value.getHost()), (key, value) -> true);

        branches[1].to(exceptionTopic);
        branches[0].to(outputTopic);

        final Topology topology = builder.build();
        streams = new KafkaStreams(topology, streamsConfiguration);

        streams = new KafkaStreams(topology, streamsConfiguration);
        streams.cleanUp();
        streams.start();

        // Add shutdown hook to respond to SIGTERM and gracefully close Kafka Streams
        Runtime.getRuntime().addShutdownHook(new Thread(() -> streams.close()));


    }

    private static void startReloadThread() {
        Thread reloadThread = new Thread(() -> {
            while (true) {
                synchronized (DnsParser.class) {
                    for (String filePath : filePaths) {
                        try {
                            File file = new File(filePath);
                            long lastModified = file.lastModified();

                            if (!fileLastModifiedTimes.containsKey(filePath) || fileLastModifiedTimes.get(filePath) != lastModified) {
                                fileLastModifiedTimes.put(filePath, lastModified);
                                fileChanged = true; // Set the flag to indicate file change
                                System.out.println("File(s) have changed. Reloading StreamerFunc...");
                                System.out.println("lastModified");
                                System.out.println(lastModified);
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                    DnsParser.class.notifyAll();
                }

                try {
                    Thread.sleep(RELOAD_INTERVAL);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        });

        reloadThread.setDaemon(true);
        reloadThread.start();
    }

}
