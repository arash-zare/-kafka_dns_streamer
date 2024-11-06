package derak.cloud.stream.new_pipeline_kafka_dns_streamer;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Map;

import derak.cloud.stream.new_pipeline_kafka_dns_streamer.avro.DnsLog;
import io.krakens.grok.api.Grok;
import io.krakens.grok.api.GrokCompiler;
import io.krakens.grok.api.Match;
import sun.jvm.hotspot.types.CIntegerField;

import java.util.TimeZone;

public class PrimaryDnsParser {

    final private Grok preProcessGrok;
    final private SimpleDateFormat timeLocalFormat;
    final private SimpleDateFormat timestampFormat;
    final private SimpleDateFormat timeLocalFormatOut;


    public PrimaryDnsParser() {
        GrokCompiler grokCompiler = GrokCompiler.newInstance();
        grokCompiler.registerDefaultPatterns();

        String preProcess =  "^\"%{IPORHOST:ns}\" %{DATA:time_local_tcp} Remote (?<remoteInfo>[^wants]*) wants" +
                " '(?<qdomainWithType>[^']*)', do = %{NUMBER:dnssecOk}, bufsize = %{NUMBER:bufsize}%{SPACE}" +
                "(:|[(]%{NUMBER:rawPacketSizeLimit}[)]:) packetcache %{WORD:query_status}";



        this.preProcessGrok = grokCompiler.compile(preProcess);
        this.timeLocalFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX");
        this.timestampFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
        this.timeLocalFormatOut = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
        this.timeLocalFormatOut.setTimeZone(TimeZone.getTimeZone("UTC"));
//        this.timestampFormat.setTimeZone(TimeZone.getTimeZone("GMT+3:30"));

    }

    public void primaryParse(String log, DnsLog dnsLog) {
        Object tmp;
        String[] split;
        Match gm = preProcessGrok.match(log);
        final Map<String, Object> fields = gm.capture();

        try {
            dnsLog.setMessage(log);
//            System.out.println(log);


            if (fields.isEmpty())
                return;
            if (fields.containsKey("ns")) {
                tmp = fields.get("ns");
                if (tmp != null) {
                    dnsLog.setNs(tmp.toString().toLowerCase());
                }
            }
//            System.out.println(dnsLog.getNs());

            try {
                if (fields.containsKey("time_local_tcp")) {
                    tmp = fields.get("time_local_tcp"); // contains time_local (TCP optional)
                    if (tmp != null) {
                        split = tmp.toString().split(" ");
                        long local_time = timeLocalFormat.parse(split[0]).getTime();
                        String lc_time = timeLocalFormatOut.format(new java.util.Date(local_time));
//                        dnsLog.setEventTime(lc_time);
                        dnsLog.setTimeLocalEdited(lc_time);
                        if (split.length > 1) dnsLog.setTcp(split[1]);
                    }
                }
            } catch (ParseException e) {
                e.printStackTrace();
            }
//            System.out.println(dnsLog.getEventTime());
            try {
                dnsLog.setIngestTime(timestampFormat.format(new java.util.Date()));
//                if (fields.containsKey("timestamp")) {
//                    tmp = fields.get("timestamp"); // contains time_local (TCP optional)
//                    if (tmp != null) {
////                        dnsLog.setIngestTime(timestampFormat.parse(tmp.toString()).getTime() / 1000);
//                    }
//                }
            } catch (Exception e) {
                e.printStackTrace();
            }

//            System.out.println(dnsLog.getIngestTime());

            if (fields.containsKey("remoteInfo")) {//"%{GREEDYDATA:forwarder_addr}<-%{GREEDYDATA:client_network}/%{WORD:client_network_mask}","%{GREEDYDATA:forwarder_addr}"
                tmp = fields.get("remoteInfo");
                if (tmp != null) {
                    split = tmp.toString().split("<-");
                    dnsLog.setForwarderAddr(split[0]);
                    if (split.length > 1) {
                        String[] split2 = split[1].split("/");
                        dnsLog.setClientNetwork(split2[0]);
                        if (split2.length > 1) dnsLog.setClientNetworkMask(split2[1]);
                    }
                }
            }
//            }
//            System.out.println(dnsLog.getForwarder());

            if (fields.containsKey("qdomainWithType")) { // "host|query_type"
                tmp = fields.get("qdomainWithType");
                if (tmp != null) {
                    split = tmp.toString().split("[|]");
                    dnsLog.setHost(split[0].toLowerCase());
                    if (split.length > 1) dnsLog.setQueryType(split[1]);
                }
            }

//        System.out.println(dnsLog.getHost());
//        System.out.println(dnsLog.getQueryType());

        if (fields.containsKey("dnssecOk")) {
                tmp = fields.get("dnssecOk");
                if (tmp != null) {
                    dnsLog.setDnsSecOk(Integer.parseInt (tmp.toString()));
                }
            }
//        System.out.println(dnsLog.getDnsSecOk());

        if (fields.containsKey("bufsize")) {
                tmp = fields.get("bufsize");
                if (tmp != null) {
                    try {
                        dnsLog.setBufsize(Integer.parseInt(tmp.toString()));
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
//        System.out.println(dnsLog.getBufsize());

        if (fields.containsKey("rawPacketSizeLimit")) {
                tmp = fields.get("rawPacketSizeLimit");
                if (tmp != null) {
                    try {
                        dnsLog.setPacketSizeLimit(Integer.parseInt(tmp.toString()));
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
//        System.out.println(dnsLog.getPacketSizeLimit());


            if (fields.containsKey("query_status")) {
                tmp = fields.get("query_status");
                if (tmp != null) {
                    dnsLog.setQueryStatus(tmp.toString());
                }
            }
//        System.out.println(dnsLog.getQueryStatus());

            if(dnsLog.getClientNetwork() != null) {
                if (!dnsLog.getClientNetwork().equals("")) {
                    dnsLog.setEdnsSupport(true);
                    dnsLog.setClientAddr(dnsLog.getClientNetwork());
                } else {
                    dnsLog.setEdnsSupport(false);
                    dnsLog.setClientAddr(dnsLog.getForwarderAddr());
                }
            }else{
                dnsLog.setEdnsSupport(false);
                dnsLog.setClientAddr(dnsLog.getForwarderAddr());
            }
            if(dnsLog.getClientAddr() != null) {
                if (dnsLog.getClientAddr().contains(":")) {
                    dnsLog.setIsipv6(true);
                } else {
                    dnsLog.setIsipv6(false);
                }
            }
        }   catch (Exception e) {
            System.out.println("-------------------------------total exception------------------------------------");
            e.printStackTrace();
        }

    }
}

