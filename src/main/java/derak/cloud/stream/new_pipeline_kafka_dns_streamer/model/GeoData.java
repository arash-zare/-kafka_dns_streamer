package derak.cloud.stream.new_pipeline_kafka_dns_streamer.model;


public class GeoData {
    private String city;
    private String country;
    private String continent;
    private String org;
    private long asn;
    private double lat;
    private double lon;

    public GeoData(String city, String country, String continent,
                   String org, long asn){
        this.city = city;
        this.country = country;
        this.continent = continent;
        this.org = org;
        this.asn = asn;
    }

    public GeoData(String city, String country, String continent,
                   String org, long asn,double lat,double lon){
        this.city = city;
        this.country = country;
        this.continent = continent;
        this.org = org;
        this.asn = asn;
        this.lat = lat;
        this.lon = lon;
    }

    public GeoData(){}

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public String getContinent() {
        return continent;
    }

    public void setContinent(String continent) {
        this.continent = continent;
    }

    public String getOrg() {
        return org;
    }

    public void setOrg(String org) {
        this.org = org;
    }

    public long getAsn() {
        return asn;
    }

    public void setAsn(long asn) {
        this.asn = asn;
    }

    public double getLat() {
        return this.lat;
    }

    public double getLon() {
        return this.lon;
    }

    public void setLat(double lat) {
        this.lat = lat;
    }

    public void setLon(double lon) {
        this.lon = lon;
    }

}
