package importer;

import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.serde.ExportOptions;
import com.powsybl.iidm.serde.NetworkSerDe;

import java.nio.file.Path;
import java.util.Locale;

/**
 * Reads a network file in memory (any format on the classpath, e.g. a MATPOWER .mat) and writes it
 * as XIIDM, without touching the store — so it can be post-processed by external tools and then
 * re-imported.
 *
 * Args: input output.xiidm
 */
public final class ToXiidm {

    private ToXiidm() {
    }

    public static void main(String[] args) {
        Path in = Path.of(args[0]);
        Path out = Path.of(args[1]);
        long start = System.nanoTime();
        Network network = Network.read(in);
        NetworkSerDe.write(network, new ExportOptions(), out);
        System.out.printf(Locale.US, "== %s -> %s in %.0f ms (buses=%d lines=%d 2wt=%d)%n",
                in, out, (System.nanoTime() - start) / 1e6,
                network.getBusView().getBusStream().count(), network.getLineCount(),
                network.getTwoWindingsTransformerCount());
    }
}
