package importer;

import com.powsybl.cgmes.conformity.CgmesConformity1Catalog;
import com.powsybl.commons.datasource.ReadOnlyDataSource;
import com.powsybl.iidm.network.Network;
import com.powsybl.network.store.client.NetworkStoreService;
import com.powsybl.network.store.client.PreloadingStrategy;

/**
 * Imports the CGMES small grid into the local network-store server (classpath compatible with the
 * powsybl-core version network-store is built against) and prints the network UUID.
 */
public final class ImportMain {

    private ImportMain() {
    }

    public static void main(String[] args) {
        try (NetworkStoreService service = new NetworkStoreService("http://localhost:8080", PreloadingStrategy.COLLECTION)) {
            ReadOnlyDataSource dataSource = CgmesConformity1Catalog.smallBusBranch().dataSource();
            Network network = service.importNetwork(dataSource);
            service.flush(network);
            System.out.println("UUID=" + service.getNetworkUuid(network));
        }
    }
}
