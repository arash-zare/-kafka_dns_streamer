package derak.cloud.stream.new_pipeline_kafka_dns_streamer.model;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DomainInfo {
    private String humanReadable;
    private String zoneId;
    private String poolName;
    private String poolId;
    private String poolTye;
    private String zoneStatus;
    private String userSegment;

    public DomainInfo(String zoneId){
        this.zoneId = zoneId;
    }
}
