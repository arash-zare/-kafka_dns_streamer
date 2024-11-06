package derak.cloud.stream.new_pipeline_kafka_dns_streamer.model;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DerakServerData {
    private String serverName;
    private String city;
    private String country;
    private String continent;
    private String dataCenter;
    private String hostName;
    private double lat;
    private double lon;
    private String province;
    private String rack;
    private String region;
    private String physicalServerIP;
    private String vmServerIP;
    private String serverID;
//    private String poolType;
//    private String zone_id;
//    private String domain;

    public DerakServerData(String serverID, String vmServerIP, String city, String continent, String country,
                           String dataCenter, String hostName, Double lat, Double lon, String province,
                           String rack, String region, String physicalServerIP) {
        this.serverName = serverName;
        this.city = city;
        this.continent = continent;
        this.country = country;
        this.dataCenter = dataCenter;
        this.hostName = hostName;
        this.lat = lat;
        this.lon = lon;
        this.province = province;
        this.rack = rack;
        this.region = region;
        this.physicalServerIP = physicalServerIP;
        this.vmServerIP = vmServerIP;
        this.serverID = serverID;
    }

//    public DerakServerData(String serverName, String vmServerIP, String city, String continent, String country,
//                     String dataCenter, String hostName, Double lat, Double lon, String province,
//                     String rack, String region, String physicalServerIP) {
//        this.serverName = serverName;
//        this.city = city;
//        this.continent = continent;
//        this.country = country;
//        this.dataCenter = dataCenter;
//        this.hostName = hostName;
//        this.lat = lat;
//        this.lon = lon;
//        this.province = province;
//        this.rack = rack;
//        this.region = region;
//        this.physicalServerIP = physicalServerIP;
//        this.vmServerIP = vmServerIP;
//    }

    public DerakServerData(String vmServerIP, String city, String continent, String country,
                           String dataCenter, String hostName, Double lat, Double lon, String province,
                           String rack, String region, String physicalServerIP) {
        this.city = city;
        this.continent = continent;
        this.country = country;
        this.dataCenter = dataCenter;
        this.hostName = hostName;
        this.lat = lat;
        this.lon = lon;
        this.province = province;
        this.rack = rack;
        this.region = region;
        this.physicalServerIP = physicalServerIP;
        this.vmServerIP = vmServerIP;
    }

    public DerakServerData() {
        this.serverName = "";
        this.city = "";
        this.continent = "";
        this.country = "";
        this.dataCenter = "";
        this.hostName = "";
        this.lat = 0.0;
        this.lon = 0.0;
        this.province = "";
        this.rack = "";
        this.region = "";
        this.physicalServerIP = "";
        this.vmServerIP = "";
//        this.poolType = "";
//        this.zone_id = "";
//        this.domain = "";
    }


}

