package derak.cloud.stream.new_pipeline_kafka_dns_streamer.services;

import java.net.InetAddress;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Objects;
import java.util.function.Function;

import derak.cloud.stream.new_pipeline_kafka_dns_streamer.model.GeoData;

import com.maxmind.db.NoCache;
import com.maxmind.db.NodeCache;
import com.maxmind.db.Reader;
import com.maxmind.geoip2.DatabaseReader;
import com.maxmind.geoip2.exception.AddressNotFoundException;
import com.maxmind.geoip2.model.AbstractResponse;
import com.maxmind.geoip2.model.AsnResponse;
import com.maxmind.geoip2.model.CityResponse;
import org.elasticsearch.SpecialPermission;
import org.elasticsearch.common.cache.Cache;
import org.elasticsearch.common.cache.CacheBuilder;
import org.elasticsearch.common.network.InetAddresses;

public final class GeoIpProcessor {

    /**
     * The in-memory cache for the geoip data. There should only be 1 instance of this class..
     * This cache differs from the maxmind's {@link NodeCache} such that this cache stores the deserialized Json objects to avoid the
     * cost of deserialization for each lookup (cached or not). This comes at slight expense of higher memory usage, but significant
     * reduction of CPU usage.
     */
    static class GeoIpCache {
        private final Cache<CacheKey<?>, AbstractResponse> cache;

        //package private for testing
        GeoIpCache(long maxSize) {
            if (maxSize < 0) {
                throw new IllegalArgumentException("geoip max cache size must be 0 or greater");
            }
            this.cache = CacheBuilder.<CacheKey<?>, AbstractResponse>builder().setMaximumWeight(maxSize).build();
        }

        <T extends AbstractResponse> T putIfAbsent(InetAddress ip, Class<T> responseType,
                                                   Function<InetAddress, AbstractResponse> retrieveFunction) {

            //can't use cache.computeIfAbsent due to the elevated permissions for the jackson (run via the cache loader)
            CacheKey<T> cacheKey = new CacheKey<>(ip, responseType);
            //intentionally non-locking for simplicity...it's OK if we re-put the same key/value in the cache during a race condition.
            AbstractResponse response = cache.get(cacheKey);
            if (response == null) {
                response = retrieveFunction.apply(ip);
                cache.put(cacheKey, response);
            }
            return responseType.cast(response);
        }

        //only useful for testing
        <T extends AbstractResponse> T get(InetAddress ip, Class<T> responseType) {
            CacheKey<T> cacheKey = new CacheKey<>(ip, responseType);
            return responseType.cast(cache.get(cacheKey));
        }

        /**
         * The key to use for the cache. Since this cache can span multiple geoip processors that all use different databases, the response
         * type is needed to be included in the cache key. For example, if we only used the IP address as the key the City and ASN the same
         * IP may be in both with different values and we need to cache both. The response type scopes the IP to the correct database
         * provides a means to safely cast the return objects.
         *
         * @param <T> The AbstractResponse type used to scope the key and cast the result.
         */
        private static class CacheKey<T extends AbstractResponse> {

            private final InetAddress ip;
            private final Class<T> responseType;

            private CacheKey(InetAddress ip, Class<T> responseType) {
                this.ip = ip;
                this.responseType = responseType;
            }

            //generated
            @Override
            public boolean equals(Object o) {
                if (this == o) return true;
                if (o == null || getClass() != o.getClass()) return false;
                CacheKey<?> cacheKey = (CacheKey<?>) o;
                return Objects.equals(ip, cacheKey.ip) &&
                        Objects.equals(responseType, cacheKey.responseType);
            }

            //generated
            @Override
            public int hashCode() {
                return Objects.hash(ip, responseType);
            }
        }
    }

    private final GeoIpCache cache;

    final private DatabaseReaderLazyLoader asnLazyLoader;
    final private DatabaseReaderLazyLoader cityLazyLoader;

    public GeoIpProcessor(int cacheSize, String asnPath, String cityPath, boolean onMemory) {
        this.cache = new GeoIpCache(cacheSize);
        final Path geoIpAsnFile = Paths.get(asnPath);
        final Path geoIpCityFile = Paths.get(cityPath);

        asnLazyLoader = createLoader(geoIpAsnFile, onMemory);
        cityLazyLoader = createLoader(geoIpCityFile, onMemory);

    }


    private static DatabaseReaderLazyLoader createLoader(Path databasePath, boolean loadDatabaseOnHeap) {
        return new DatabaseReaderLazyLoader(
                databasePath,
                () -> {
                    DatabaseReader.Builder builder = createDatabaseBuilder(databasePath).withCache(NoCache.getInstance());
                    if (loadDatabaseOnHeap) {
                        builder.fileMode(Reader.FileMode.MEMORY);
                    } else {
                        builder.fileMode(Reader.FileMode.MEMORY_MAPPED);
                    }
                    return builder.build();
                });
    }

    private static DatabaseReader.Builder createDatabaseBuilder(Path databasePath) {
        return new DatabaseReader.Builder(databasePath.toFile());
    }


    public GeoData execute(String ip) {
        if (ip == null || ip.isEmpty())
            return null;
        try {
            InetAddress ipAddress = InetAddresses.forString(ip);
            GeoData data = new GeoData();
            retrieveCityGeoData(ipAddress, data);
            retrieveAsnGeoData(ipAddress, data);
            return data;
        }catch (Exception e){
            return null;
        }
    }


    private void retrieveCityGeoData(InetAddress ipAddress, GeoData geoData) {
        SpecialPermission.check();
        CityResponse response = AccessController.doPrivileged((PrivilegedAction<CityResponse>) () ->
                cache.putIfAbsent(ipAddress, CityResponse.class, ip -> {
                    try {
                        return cityLazyLoader.get().city(ip);
                    } catch (AddressNotFoundException e) {
                        throw new AddressNotFoundRuntimeException(e);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }));
        geoData.setCity(response.getCity().getName());
        geoData.setCountry(response.getCountry().getIsoCode());
        geoData.setContinent(response.getContinent().getName());
        geoData.setLat(response.getLocation().getLatitude());
        geoData.setLon(response.getLocation().getLongitude());
    }

    private void retrieveAsnGeoData(InetAddress ipAddress, GeoData geoData) {
        SpecialPermission.check();
        AsnResponse response = AccessController.doPrivileged((PrivilegedAction<AsnResponse>) () ->
                cache.putIfAbsent(ipAddress, AsnResponse.class, ip -> {
                    try {
                        return asnLazyLoader.get().asn(ip);
                    } catch (AddressNotFoundException e) {
                        throw new AddressNotFoundRuntimeException(e);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }));

        geoData.setAsn(response.getAutonomousSystemNumber());
        geoData.setOrg(response.getAutonomousSystemOrganization());
    }

    // Geoip2's AddressNotFoundException is checked and due to the fact that we need run their code
    // inside a PrivilegedAction code block, we are forced to catch any checked exception and rethrow
    // it with an unchecked exception.
    //package private for testing
    static final class AddressNotFoundRuntimeException extends RuntimeException {
        AddressNotFoundRuntimeException(Throwable cause) {
            super(cause);
        }
    }

}
