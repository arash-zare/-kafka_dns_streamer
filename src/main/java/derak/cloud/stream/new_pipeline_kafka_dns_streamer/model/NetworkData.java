package derak.cloud.stream.new_pipeline_kafka_dns_streamer.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import inet.ipaddr.IPAddress;
import inet.ipaddr.IPAddressString;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class NetworkData {
    private String type;
    private String serverCountry;
    private IPAddress block;
    private int asn;
    private String asnOrg;

    private String continent;
    private String country;
    private String city;
    private String serverName;


    public NetworkData(String type, String serverCountry, String block,
                       int asn, String asnOrg, String continent,
                       String country, String city, String serverName) {
        this.type = type;
        this.serverCountry = serverCountry;
        this.block = new IPAddressString(block).getAddress();
        this.asn = asn;
        this.asnOrg = asnOrg;
        this.continent = continent;
        this.country = country;
        this.city = city;
        this.serverName = serverName;
    }

    public GeoData getGeoData() {
        return new GeoData(city, country, continent, asnOrg, asn);
    }

}
