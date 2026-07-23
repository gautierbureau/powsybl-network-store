package importer;

import com.powsybl.matpower.model.MBranch;
import com.powsybl.matpower.model.MBus;
import com.powsybl.matpower.model.MGen;
import com.powsybl.matpower.model.MatpowerFormatVersion;
import com.powsybl.matpower.model.MatpowerModel;
import com.powsybl.matpower.model.MatpowerWriter;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Converts a MATPOWER .m case (MATLAB script) into the binary .mat file powsybl reads, by parsing
 * the bus/gen/branch matrices into powsybl's own MatpowerModel and writing it with MatpowerWriter.
 *
 * Args: input.m output.mat [caseName]
 */
public final class MToMat {

    private MToMat() {
    }

    public static void main(String[] args) throws Exception {
        Path in = Path.of(args[0]);
        Path out = Path.of(args[1]);
        String caseName = args.length > 2 ? args[2] : in.getFileName().toString().replaceFirst("\\.m$", "");
        List<String> lines = Files.readAllLines(in);

        MatpowerModel model = new MatpowerModel(caseName);
        model.setVersion(MatpowerFormatVersion.V2);
        model.setBaseMva(scalar(lines, "mpc.baseMVA"));

        for (double[] r : matrix(lines, "mpc.bus")) {
            MBus bus = new MBus();
            bus.setNumber((int) r[0]);
            bus.setName("");
            bus.setType(MBus.Type.fromInt((int) r[1]));
            bus.setRealPowerDemand(r[2]);
            bus.setReactivePowerDemand(r[3]);
            bus.setShuntConductance(r[4]);
            bus.setShuntSusceptance(r[5]);
            bus.setAreaNumber((int) r[6]);
            bus.setVoltageMagnitude(r[7]);
            bus.setVoltageAngle(r[8]);
            bus.setBaseVoltage(r[9]);
            bus.setLossZone((int) r[10]);
            bus.setMaximumVoltageMagnitude(r[11]);
            bus.setMinimumVoltageMagnitude(r[12]);
            model.addBus(bus);
        }

        for (double[] r : matrix(lines, "mpc.gen")) {
            MGen gen = new MGen();
            gen.setNumber((int) r[0]);
            gen.setRealPowerOutput(r[1]);
            gen.setReactivePowerOutput(r[2]);
            gen.setMaximumReactivePowerOutput(r[3]);
            gen.setMinimumReactivePowerOutput(r[4]);
            gen.setVoltageMagnitudeSetpoint(r[5]);
            gen.setTotalMbase(r[6]);
            gen.setStatus((int) r[7]);
            gen.setMaximumRealPowerOutput(r[8]);
            gen.setMinimumRealPowerOutput(r[9]);
            model.addGenerator(gen);
        }

        for (double[] r : matrix(lines, "mpc.branch")) {
            MBranch branch = new MBranch();
            branch.setFrom((int) r[0]);
            branch.setTo((int) r[1]);
            branch.setR(r[2]);
            branch.setX(r[3]);
            branch.setB(r[4]);
            branch.setRateA(r[5]);
            branch.setRateB(r[6]);
            branch.setRateC(r[7]);
            branch.setRatio(r[8]);
            branch.setPhaseShiftAngle(r[9]);
            branch.setStatus((int) r[10]);
            branch.setAngMin(r[11]);
            branch.setAngMax(r[12]);
            model.addBranch(branch);
        }

        MatpowerWriter.write(model, out, true);
        System.out.printf("wrote %s: %d buses, %d gens, %d branches%n",
                out, model.getBuses().size(), model.getGenerators().size(), model.getBranches().size());
    }

    private static double scalar(List<String> lines, String key) {
        for (String line : lines) {
            String s = strip(line);
            int eq = s.indexOf('=');
            if (eq > 0 && s.substring(0, eq).trim().equals(key)) {
                return Double.parseDouble(s.substring(eq + 1).replace(";", "").trim());
            }
        }
        throw new IllegalArgumentException("scalar not found: " + key);
    }

    private static List<double[]> matrix(List<String> lines, String key) {
        List<double[]> rows = new ArrayList<>();
        boolean in = false;
        for (String raw : lines) {
            String s = strip(raw);
            if (!in) {
                int eq = s.indexOf('=');
                if (eq > 0 && s.substring(0, eq).trim().equals(key) && s.contains("[")) {
                    in = true;
                    s = s.substring(s.indexOf('[') + 1);
                } else {
                    continue;
                }
            }
            if (s.contains("]")) {
                s = s.substring(0, s.indexOf(']'));
                addRow(rows, s);
                break;
            }
            addRow(rows, s);
        }
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("matrix not found or empty: " + key);
        }
        return rows;
    }

    private static void addRow(List<double[]> rows, String s) {
        String t = s.replace(";", "").trim();
        if (t.isEmpty()) {
            return;
        }
        String[] tokens = t.split("\\s+");
        double[] row = new double[tokens.length];
        for (int i = 0; i < tokens.length; i++) {
            row[i] = Double.parseDouble(tokens[i]);
        }
        rows.add(row);
    }

    // drop the MATLAB % comment
    private static String strip(String line) {
        int pct = line.indexOf('%');
        return pct >= 0 ? line.substring(0, pct) : line;
    }
}
